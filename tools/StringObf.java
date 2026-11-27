import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Post-ProGuard string obfuscation for Loot Client.
 *
 * Replaces every String LDC in our own (already renamed) classes with an
 * encrypted literal plus a call to a generated decoder, so decompiling the
 * shipped jar no longer hands the reader a plaintext map of module names,
 * setting labels and serialization keys.
 *
 * Why this is verification-safe (no VerifyError):
 *   A `LDC "x"` pushes one String. We turn it into `LDC "<enc>"` followed by
 *   `invokestatic Decoder.d(String)String`, which pops one String and pushes
 *   one String. The stack shape at every instruction boundary is therefore
 *   unchanged, so the existing StackMapTable frames stay valid. We only let
 *   ASM recompute maxStack/maxLocals (COMPUTE_MAXS), which needs no access to
 *   the Minecraft class hierarchy - the same trap that caused the earlier
 *   frame-recomputation VerifyError. The CI JVM verifier still gates this.
 *
 * Deliberately skipped:
 *   - dev/loot/client/mixin/**  (Mixin matches classes/members by name and
 *     reads annotation + refmap strings; leave the whole package untouched).
 *   - the generated Decoder class itself.
 *   - empty strings (no value in hiding "").
 *   - invokedynamic string-concat recipes are not LDCs, so they are left as-is.
 *
 * The transform is a symmetric per-index XOR, so decode == encode. The runtime
 * String value is byte-for-byte identical to the original, so config keys,
 * reflection names and enum names keep working.
 */
public final class StringObf {

    static final String DECODER = "dev/loot/client/Decoder";

    // Symmetric: applying twice returns the original. char is 16-bit; XOR is
    // reversible and modified-UTF-8 stores every code unit independently, so
    // even lone surrogates round-trip through the constant pool unchanged.
    static String xor(String s) {
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) {
            c[i] = (char) (c[i] ^ (0x5A ^ (i * 31)));
        }
        return new String(c);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: StringObf <in.jar> <out.jar>");
            System.exit(2);
        }
        JarFile in = new JarFile(args[0]);
        int classesTouched = 0, stringsEnc = 0;

        try (JarOutputStream out = new JarOutputStream(new java.io.FileOutputStream(args[1]))) {
            // Our own decoder, injected first.
            out.putNextEntry(new JarEntry(DECODER + ".class"));
            out.write(decoderClass());
            out.closeEntry();

            List<JarEntry> entries = new ArrayList<>();
            java.util.Enumeration<JarEntry> en = in.entries();
            while (en.hasMoreElements()) entries.add(en.nextElement());

            for (JarEntry e : entries) {
                if (e.isDirectory()) { out.putNextEntry(new JarEntry(e.getName())); out.closeEntry(); continue; }
                byte[] data = readAll(in.getInputStream(e));
                String name = e.getName();

                boolean ours = name.endsWith(".class")
                        && name.startsWith("dev/loot/client/")
                        && !name.startsWith("dev/loot/client/mixin/")
                        && !name.equals(DECODER + ".class");

                if (ours) {
                    int[] count = {0};
                    byte[] rewritten = transform(data, count);
                    if (count[0] > 0) { classesTouched++; stringsEnc += count[0]; data = rewritten; }
                }

                JarEntry ne = new JarEntry(name);
                out.putNextEntry(ne);
                out.write(data);
                out.closeEntry();
            }
        } finally {
            in.close();
        }
        System.out.println("StringObf: encrypted " + stringsEnc + " string(s) across " + classesTouched + " class(es)");
    }

    static byte[] transform(byte[] classBytes, int[] count) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        boolean changed = false;
        for (MethodNode m : cn.methods) {
            if (m.instructions == null) continue;
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode) {
                    Object cst = ((LdcInsnNode) insn).cst;
                    if (cst instanceof String && !((String) cst).isEmpty()) {
                        ((LdcInsnNode) insn).cst = xor((String) cst);
                        MethodInsnNode call = new MethodInsnNode(Opcodes.INVOKESTATIC, DECODER, "d",
                                "(Ljava/lang/String;)Ljava/lang/String;", false);
                        m.instructions.insert(insn, call);
                        insn = call; // skip past what we inserted
                        changed = true;
                        count[0]++;
                    }
                }
            }
        }
        if (!changed) return classBytes;
        // COMPUTE_MAXS only: stack shape is unchanged so frames stay valid and
        // we never need to resolve Minecraft types to recompute them.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /** Generates: public final class Decoder { public static String d(String s){ ...xor... } } */
    static byte[] decoderClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                DECODER, null, "java/lang/Object", null);

        org.objectweb.asm.MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        // public static String d(String s) {
        //   char[] c = s.toCharArray();
        //   for (int i=0;i<c.length;i++) c[i]=(char)(c[i]^(0x5A^(i*31)));
        //   return new String(c);
        // }
        org.objectweb.asm.MethodVisitor d = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "d",
                "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        d.visitCode();
        d.visitVarInsn(Opcodes.ALOAD, 0);
        d.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
        d.visitVarInsn(Opcodes.ASTORE, 1);                 // c
        d.visitInsn(Opcodes.ICONST_0);
        d.visitVarInsn(Opcodes.ISTORE, 2);                 // i = 0
        org.objectweb.asm.Label loop = new org.objectweb.asm.Label();
        org.objectweb.asm.Label end = new org.objectweb.asm.Label();
        d.visitLabel(loop);
        d.visitVarInsn(Opcodes.ILOAD, 2);
        d.visitVarInsn(Opcodes.ALOAD, 1);
        d.visitInsn(Opcodes.ARRAYLENGTH);
        d.visitJumpInsn(Opcodes.IF_ICMPGE, end);
        d.visitVarInsn(Opcodes.ALOAD, 1);                  // c
        d.visitVarInsn(Opcodes.ILOAD, 2);                  // i
        d.visitVarInsn(Opcodes.ALOAD, 1);                  // c
        d.visitVarInsn(Opcodes.ILOAD, 2);                  // i
        d.visitInsn(Opcodes.CALOAD);                       // c[i]
        d.visitIntInsn(Opcodes.BIPUSH, 0x5A);
        d.visitVarInsn(Opcodes.ILOAD, 2);
        d.visitIntInsn(Opcodes.BIPUSH, 31);
        d.visitInsn(Opcodes.IMUL);                         // i*31
        d.visitInsn(Opcodes.IXOR);                         // 0x5A ^ (i*31)
        d.visitInsn(Opcodes.IXOR);                         // c[i] ^ that
        d.visitInsn(Opcodes.I2C);
        d.visitInsn(Opcodes.CASTORE);                      // c[i] = ...
        d.visitIincInsn(2, 1);                             // i++
        d.visitJumpInsn(Opcodes.GOTO, loop);
        d.visitLabel(end);
        d.visitTypeInsn(Opcodes.NEW, "java/lang/String");
        d.visitInsn(Opcodes.DUP);
        d.visitVarInsn(Opcodes.ALOAD, 1);
        d.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false);
        d.visitInsn(Opcodes.ARETURN);
        d.visitMaxs(0, 0);
        d.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) >= 0) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }
}
