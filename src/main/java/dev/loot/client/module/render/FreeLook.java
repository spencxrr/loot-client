package dev.loot.client.module.render;

import dev.loot.client.LootClient;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

/** Orbit the third-person camera around yourself while you keep walking and looking the same way. */
public class FreeLook extends Module {
    public float yaw, pitch;
    private Perspective previous;

    public FreeLook() {
        super("Free Look", "Look around in third person without turning.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        if (!inGame()) {
            setEnabled(false);
            return;
        }
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        previous = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    @Override
    public void onDisable() {
        if (previous != null) mc.options.setPerspective(previous);
    }

    public static boolean look(double dx, double dy) {
        FreeLook fl = LootClient.modules().get(FreeLook.class);
        if (!fl.isEnabled()) return false;
        fl.yaw += (float) (dx * 0.15);
        fl.pitch = MathHelper.clamp(fl.pitch + (float) (dy * 0.15), -90, 90);
        return true;
    }
}
