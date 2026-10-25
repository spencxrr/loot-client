package dev.loot.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Batches world-space lines and quads for one frame and draws them on top of the world
 * (depth test off), which is what ESP-style modules want.
 */
public class Render3D {
    private static final BufferAllocator LINE_ALLOC = new BufferAllocator(1 << 18);
    private static final BufferAllocator QUAD_ALLOC = new BufferAllocator(1 << 18);

    private final Vec3d camera;
    private final Matrix4f matrix;
    private final BufferBuilder lines;
    private final BufferBuilder quads;
    private final Vec3d lookVec;

    public Render3D(Vec3d camera, Matrix4f positionMatrix, Vec3d lookVec) {
        this.camera = camera;
        this.matrix = positionMatrix;
        this.lookVec = lookVec;
        this.lines = new BufferBuilder(LINE_ALLOC, VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        this.quads = new BufferBuilder(QUAD_ALLOC, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
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
    }

    private void vQuad(double x, double y, double z, int argb) {
        quads.vertex(matrix, (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z))
            .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
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

    public void draw(float lineWidth) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BuiltBuffer quadBuf = quads.endNullable();
        if (quadBuf != null) BufferRenderer.drawWithGlobalProgram(quadBuf);

        RenderSystem.lineWidth(lineWidth);
        BuiltBuffer lineBuf = lines.endNullable();
        if (lineBuf != null) BufferRenderer.drawWithGlobalProgram(lineBuf);
        RenderSystem.lineWidth(1f);

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        modelView.popMatrix();
    }
}
