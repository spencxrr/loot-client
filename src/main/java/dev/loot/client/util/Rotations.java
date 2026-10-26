package dev.loot.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class Rotations {
    private Rotations() {}

    /** {yaw, pitch} needed to look from the player's eyes at a point. */
    public static float[] to(Vec3d target) {
        Vec3d eyes = MinecraftClient.getInstance().player.getEyePos();
        double dx = target.x - eyes.x, dy = target.y - eyes.y, dz = target.z - eyes.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[]{yaw, MathHelper.clamp(pitch, -90f, 90f)};
    }

    /** Angle in degrees between where the player looks and the target. */
    public static double angleTo(Vec3d target) {
        var p = MinecraftClient.getInstance().player;
        float[] r = to(target);
        float dy = MathHelper.wrapDegrees(r[0] - p.getYaw());
        float dp = r[1] - p.getPitch();
        return Math.sqrt(dy * dy + dp * dp);
    }

    /** Instantly faces the target (client and server both see it next movement packet). */
    public static void face(Vec3d target) {
        var p = MinecraftClient.getInstance().player;
        float[] r = to(target);
        p.setYaw(r[0]);
        p.setPitch(r[1]);
    }

    /** Turns toward the target by at most {@code maxStep} degrees per call. */
    public static void approach(Vec3d target, float maxStep) {
        var p = MinecraftClient.getInstance().player;
        float[] r = to(target);
        float dy = MathHelper.clamp(MathHelper.wrapDegrees(r[0] - p.getYaw()), -maxStep, maxStep);
        float dp = MathHelper.clamp(r[1] - p.getPitch(), -maxStep, maxStep);
        p.setYaw(p.getYaw() + dy);
        p.setPitch(MathHelper.clamp(p.getPitch() + dp, -90f, 90f));
    }
}
