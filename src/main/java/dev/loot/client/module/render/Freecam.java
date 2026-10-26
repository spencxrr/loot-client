package dev.loot.client.module.render;

import dev.loot.client.LootClient;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Detaches the camera from your body so you can fly it around. Your player stays put
 * (movement keys are swallowed while this is on) and the server sees nothing.
 */
public class Freecam extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", "Blocks per tick.", 1, 0.1, 5, 0.1));

    private Vec3d pos = Vec3d.ZERO, prevPos = Vec3d.ZERO;
    private float yaw, pitch, prevYaw, prevPitch;

    public Freecam() {
        super("Freecam", "Fly the camera around without moving.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        if (!inGame()) {
            setEnabled(false);
            return;
        }
        pos = prevPos = mc.player.getEntityPos().add(0, mc.player.getStandingEyeHeight(), 0);
        yaw = prevYaw = mc.player.getYaw();
        pitch = prevPitch = mc.player.getPitch();
    }

    @Override
    public void onDisable() {
        KeyBinding.updatePressedStates();
    }

    private boolean held(KeyBinding kb) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(kb);
        return key.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
    }

    @Override
    public void onTick() {
        prevPos = pos;
        prevYaw = yaw;
        prevPitch = pitch;
        if (mc.currentScreen != null) return;
        var o = mc.options;
        double f = (held(o.forwardKey) ? 1 : 0) - (held(o.backKey) ? 1 : 0);
        double s = (held(o.leftKey) ? 1 : 0) - (held(o.rightKey) ? 1 : 0);
        double v = (held(o.jumpKey) ? 1 : 0) - (held(o.sneakKey) ? 1 : 0);
        double sp = speed.get() * (held(o.sprintKey) ? 2 : 1);
        double rad = Math.toRadians(yaw);
        double dx = (-Math.sin(rad) * f + Math.cos(rad) * s);
        double dz = (Math.cos(rad) * f + Math.sin(rad) * s);
        Vec3d move = new Vec3d(dx, v, dz);
        if (move.lengthSquared() > 1) move = move.normalize();
        pos = pos.add(move.multiply(sp));

        // keep the real player still
        for (KeyBinding kb : new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sneakKey, o.sprintKey})
            kb.setPressed(false);
    }

    /** Mouse input while active. Returns true when it was consumed. */
    public static boolean look(double dx, double dy) {
        Freecam fc = LootClient.modules().get(Freecam.class);
        if (!fc.isEnabled()) return false;
        fc.yaw += (float) (dx * 0.15);
        fc.pitch = MathHelper.clamp(fc.pitch + (float) (dy * 0.15), -90, 90);
        fc.prevYaw = fc.yaw;
        fc.prevPitch = fc.pitch;
        return true;
    }

    public Vec3d pos(float tickDelta) {
        return prevPos.lerp(pos, tickDelta);
    }

    public float yaw(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevYaw, yaw);
    }

    public float pitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPitch, pitch);
    }
}
