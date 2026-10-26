package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.util.Targets;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Draws hitboxes where entities actually are on the server tick, with no smoothing,
 * so you can see the true position you're swinging at.
 */
public class StaticHitBoxes extends Module {
    private final BoolSetting mobs = add(new BoolSetting("Mobs", "Also show mob hitboxes.", false));

    public StaticHitBoxes() {
        super("Static HitBoxes", "Shows true, un-interpolated hitboxes.", Category.COMBAT);
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            if (!(Targets.validPlayer(e) || (mobs.get() && e instanceof LivingEntity && e.isAlive()))) continue;
            if (e.squaredDistanceTo(mc.player) > 64 * 64) continue;
            r.boxOutline(e.getBoundingBox(), 0xFFFFFFFF);
        }
    }
}
