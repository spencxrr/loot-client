package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Shows the box your clicks are actually tested against: the entity's hitbox plus its targeting
 * margin, which includes whatever Hit Box is adding. Turns lavender on the one you're aiming at.
 */
public class RealHitBox extends Module {
    private final BoolSetting playersOnly = add(new BoolSetting("PlayersOnly", "Skip mobs.", true));
    private final NumberSetting range = add(new NumberSetting("Range", "Max distance.", 16, 4, 64, 1));
    private final BoolSetting look = add(new BoolSetting("LookVector", "Line showing where each player is facing.", true));

    public RealHitBox() {
        super("RealHitBox", "Draws the real, click-tested hitbox of nearby entities.", Category.RENDER);
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        double maxSq = range.get() * range.get();
        Entity aimed = mc.targetedEntity;
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive()) continue;
            if (playersOnly.get() && !(e instanceof net.minecraft.entity.player.PlayerEntity)) continue;
            if (e.squaredDistanceTo(mc.player) > maxSq) continue;
            Vec3d lerp = e.getLerpedPos(tickDelta);
            Box box = e.getBoundingBox().offset(lerp.subtract(e.getEntityPos())).expand(e.getTargetingMargin());
            int color = e == aimed ? 0xFFA3AEFF : 0xB0FFFFFF;
            r.box(box, color, e == aimed ? 30 : 0);
            if (look.get()) {
                Vec3d eye = lerp.add(0, e.getStandingEyeHeight(), 0);
                r.line(eye, eye.add(e.getRotationVec(tickDelta).multiply(2)), 0xFFFF6B9A);
            }
        }
    }
}
