package dev.loot.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.loot.client.LootClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Batches world-space lines and quads for one frame and draws them straight to the main
 * framebuffer with depth testing off, which is what ESP-style modules want.
 *
 * 1.21.5+ removed the old immediate-mode RenderSystem calls, so this builds its own
 * pipelines and issues a render pass directly.
 */
public class Render3D {
    private static final RenderPipeline LINES = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
        .withLocation(Identifier.of(LootClient.MOD_ID, "pipeline/esp_lines"))
        .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build());

    private static final RenderPipeline QUADS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
        .withLocation(Identifier.of(LootClient.MOD_ID, "pipeline/esp_quads"))
        .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build());

    private static final BufferAllocator LINE_ALLOC = new BufferAllocator(1 << 18);
    private static final BufferAllocator QUAD_ALLOC = new BufferAllocator(1 << 18);

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private final Vec3d camera;
    private final Matrix4f matrix;
    private final Vec3d lookVec;
    private final BufferBuilder lines;
    private final BufferBuilder quads;
    private int lineVertices;
    private int quadVertices;

    /**
     * @param positionMatrix the world render matrix (view rotation); positions are passed camera-relative.
     */
    public Render3D(Vec3d camera, Matrix4f positionMatrix, Vec3d lookVec) {
        this.camera = camera;
        this.matrix = positionMatrix;
        this.lookVec = lookVec;
        this.lines = new BufferBuilder(LINE_ALLOC, LINES.getVertexFormatMode(), LINES.getVertexFormat());
        this.quads = new BufferBuilder(QUAD_ALLOC, QUADS.getVertexFormatMode(), QUADS.getVertexFormat());
    }

    public static void init() {
        // Forces the static pipelines to register during client init rather than mid-frame.
    }

    public Vec3d getCamera() {
        return camera;
    }

    /** A point just in front of the camera, used as the start of tracers. */
    public Vec3d crosshair() {
        return camera.add(lookVec.multiply(0.5));
    }

    private void vLine(double x, double y, double z, int argb) {
        lines.vertex(matrix, (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z))
            .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
        lineVertices++;
    }

    private void vQuad(double x, double y, double z, int argb) {
        quads.vertex(matrix, (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z))
            .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
        quadVertices++;
    }

    public void line(Vec3d a, Vec3d b, int argb) {
        vLine(a.x, a.y, a.z, argb);
        vLine(b.x, b.y, b.z, argb);
    }

    public void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        vLine(x1, y1, z1, argb);
        vLine(x2, y2, z2, argb);
    }

    public void boxOutline(Box b, int argb) {
        double x1 = b.minX, y1 = b.minY, z1 = b.minZ, x2 = b.maxX, y2 = b.maxY, z2 = b.maxZ;
        // bottom
        line(x1, y1, z1, x2, y1, z1, argb);
        line(x2, y1, z1, x2, y1, z2, argb);
        line(x2, y1, z2, x1, y1, z2, argb);
        line(x1, y1, z2, x1, y1, z1, argb);
        // top
        line(x1, y2, z1, x2, y2, z1, argb);
        line(x2, y2, z1, x2, y2, z2, argb);
        line(x2, y2, z2, x1, y2, z2, argb);
        line(x1, y2, z2, x1, y2, z1, argb);
        // verticals
        line(x1, y1, z1, x1, y2, z1, argb);
        line(x2, y1, z1, x2, y2, z1, argb);
        line(x2, y1, z2, x2, y2, z2, argb);
        line(x1, y1, z2, x1, y2, z2, argb);
    }

    public void boxFill(Box b, int argb) {
        double x1 = b.minX, y1 = b.minY, z1 = b.minZ, x2 = b.maxX, y2 = b.maxY, z2 = b.maxZ;
        // down / up
        vQuad(x1, y1, z1, argb); vQuad(x2, y1, z1, argb); vQuad(x2, y1, z2, argb); vQuad(x1, y1, z2, argb);
        vQuad(x1, y2, z1, argb); vQuad(x1, y2, z2, argb); vQuad(x2, y2, z2, argb); vQuad(x2, y2, z1, argb);
        // north / south
        vQuad(x1, y1, z1, argb); vQuad(x1, y2, z1, argb); vQuad(x2, y2, z1, argb); vQuad(x2, y1, z1, argb);
        vQuad(x1, y1, z2, argb); vQuad(x2, y1, z2, argb); vQuad(x2, y2, z2, argb); vQuad(x1, y2, z2, argb);
        // west / east
        vQuad(x1, y1, z1, argb); vQuad(x1, y1, z2, argb); vQuad(x1, y2, z2, argb); vQuad(x1, y2, z1, argb);
        vQuad(x2, y1, z1, argb); vQuad(x2, y2, z1, argb); vQuad(x2, y2, z2, argb); vQuad(x2, y1, z2, argb);
    }

    /** Outline plus a translucent fill using the same color. */
    public void box(Box b, int argb, int fillAlpha) {
        boxOutline(b, argb);
        if (fillAlpha > 0) boxFill(b, (argb & 0x00FFFFFF) | (fillAlpha << 24));
    }

    public void draw() {
        // Close both builders every frame so their allocators are free for the next one.
        BuiltBuffer quadBuf = quads.endNullable();
        BuiltBuffer lineBuf = lines.endNullable();
        if (quadBuf != null) submit(QUADS, quadBuf, quadVertices);
        if (lineBuf != null) submit(LINES, lineBuf, lineVertices);
    }

    private static void submit(RenderPipeline pipeline, BuiltBuffer built, int vertexCount) {
        try (built) {
            VertexFormat.DrawMode mode = pipeline.getVertexFormatMode();
            int indexCount = mode.getIndexCount(vertexCount);
            GpuBuffer vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(built.getBuffer());
            RenderSystem.ShapeIndexBuffer shapeIndices = RenderSystem.getSequentialBuffer(mode);
            GpuBuffer indices = shapeIndices.getIndexBuffer(indexCount);

            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

            Framebuffer target = MinecraftClient.getInstance().getFramebuffer();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Loot Client ESP", target.getColorAttachmentView(), OptionalInt.empty(),
                target.getDepthAttachmentView(), OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, shapeIndices.getIndexType());
                pass.drawIndexed(0, 0, indexCount, 1);
            }
        }
    }
}
