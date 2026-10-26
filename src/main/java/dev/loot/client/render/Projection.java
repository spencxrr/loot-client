package dev.loot.client.render;

import dev.loot.client.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * World-to-screen projection for HUD elements that follow things in the world (name tags,
 * labels). Rebuilt once per frame from the camera, so it matches vanilla minus view bobbing.
 */
public final class Projection {
    private static final Matrix4f viewProj = new Matrix4f();
    private static Vec3d camera = Vec3d.ZERO;

    private Projection() {}

    public static void update(Camera cam, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float fov = ((GameRendererAccessor) mc.gameRenderer).loot$getFov(cam, tickDelta, true);
        Matrix4f proj = mc.gameRenderer.getBasicProjectionMatrix(fov);
        Matrix4f view = new Matrix4f().rotation(new Quaternionf(cam.getRotation()).conjugate());
        viewProj.set(proj).mul(view);
        camera = cam.getCameraPos();
    }

    /** Scaled-GUI screen coordinates of a world point, or null when it's behind the camera. */
    public static Vec3d toScreen(Vec3d world) {
        Vector4f v = new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1f);
        viewProj.transform(v);
        if (v.w <= 0.05f) return null;
        float nx = v.x / v.w, ny = v.y / v.w;
        var window = MinecraftClient.getInstance().getWindow();
        double sx = (nx * 0.5 + 0.5) * window.getScaledWidth();
        double sy = (1 - (ny * 0.5 + 0.5)) * window.getScaledHeight();
        return new Vec3d(sx, sy, v.w);
    }

    public static Vec3d camera() {
        return camera;
    }
}
