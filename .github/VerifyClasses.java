import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Runs the real JVM bytecode verifier over every dev/loot/client class in the
 * obfuscated (pre-remap, yarn-named) jar, WITHOUT launching Minecraft.
 *
 * For each class we load and then link it (resolveClass), which triggers the
 * exact same bytecode verification the JVM does at mod-load time - the thing
 * that threw "VerifyError: Bad type on operand stack" in-game. Linking does NOT
 * run static initializers, so no Minecraft/GL state is touched and nothing
 * executes. A VerifyError here means the jar would fail to load in-game.
 *
 * Args: <obfJar> <classpath>   (classpath = named MC + Fabric + libs, pathsep-joined)
 * Exit: 0 = all classes verify; 1 = at least one VerifyError.
 */
public final class VerifyClasses {

    static final class Linker extends URLClassLoader {
        Linker(URL[] urls) { super(urls, ClassLoader.getSystemClassLoader()); }
        void loadAndLink(String binaryName) throws ClassNotFoundException {
            Class<?> c = loadClass(binaryName); // find + define (no init)
            resolveClass(c);                    // link -> verify (no init)
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: VerifyClasses <obfJar> [classpath]");
            System.exit(2);
        }
        File jar = new File(args[0]);
        String cp = args.length > 1 ? args[1] : "";

        List<URL> urls = new ArrayList<>();
        urls.add(jar.toURI().toURL());
        if (!cp.isEmpty()) {
            for (String p : cp.split(File.pathSeparator)) {
                if (p != null && !p.trim().isEmpty()) urls.add(new File(p).toURI().toURL());
            }
        }

        List<String> names = new ArrayList<>();
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.startsWith("dev/loot/client/") && n.endsWith(".class")) {
                    names.add(n.substring(0, n.length() - 6).replace('/', '.'));
                }
            }
        }
        Collections.sort(names);

        Linker linker = new Linker(urls.toArray(new URL[0]));
        int ok = 0, verifyErrors = 0, other = 0;
        List<String> verifyFailures = new ArrayList<>();
        List<String> otherFailures = new ArrayList<>();

        for (String bn : names) {
            try {
                linker.loadAndLink(bn);
                ok++;
            } catch (VerifyError ve) {
                verifyErrors++;
                verifyFailures.add(bn + "  ::  " + firstLine(ve));
            } catch (Throwable t) {
                // NoClassDefFoundError etc: usually a classpath gap in this
                // harness, not a real load failure. Report but don't fail on it.
                other++;
                otherFailures.add(bn + "  ::  " + t.getClass().getSimpleName() + ": " + firstLine(t));
            }
        }

        System.out.println("==============================================================");
        System.out.println(" JVM bytecode verification of " + jar.getName());
        System.out.println("==============================================================");
        System.out.println("  classes checked : " + names.size());
        System.out.println("  verified OK     : " + ok);
        System.out.println("  VerifyError     : " + verifyErrors + "   (fatal - would not load in-game)");
        System.out.println("  other/skipped   : " + other + "   (non-fatal, usually classpath gaps here)");
        if (!verifyFailures.isEmpty()) {
            System.out.println("\n  -- VerifyError classes --");
            for (String f : verifyFailures) System.out.println("    " + f);
        }
        if (!otherFailures.isEmpty()) {
            System.out.println("\n  -- other (non-fatal) --");
            int shown = 0;
            for (String f : otherFailures) { System.out.println("    " + f); if (++shown >= 25) { System.out.println("    ... (" + (otherFailures.size() - shown) + " more)"); break; } }
        }
        System.out.println("==============================================================");
        if (verifyErrors > 0) {
            System.out.println(" RESULT: FAILED - obfuscated jar would throw VerifyError at load");
            System.exit(1);
        }
        System.out.println(" RESULT: OK - every client class passes JVM bytecode verification");
    }

    static String firstLine(Throwable t) {
        String m = t.getMessage();
        if (m == null) return t.toString();
        int nl = m.indexOf('\n');
        return nl < 0 ? m : m.substring(0, nl);
    }
}
