package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.ModeSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.Rotations;
import dev.loot.client.util.Targets;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.util.math.Vec3d;

public class AimAssist extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "Max target distance.", 5, 1, 12, 0.5));
    private final NumberSetting fov = add(new NumberSetting("FOV", "Only pull toward targets within this many degrees of your crosshair.", 60, 10, 180, 5));
    private final NumberSetting speed = add(new NumberSetting("Speed", "Max degrees turned per tick.", 6, 1, 45, 1));
    private final ModeSetting aimAt = add(new ModeSetting("AimAt", "Where on the target to aim.", "Body", "Head", "Body", "Feet"));
    private final BoolSetting mobs = add(new BoolSetting("Mobs", "Also target hostile mobs.", false));
    private final BoolSetting onClick = add(new BoolSetting("OnClick", "Only assist while holding attack.", true));

    public AimAssist() {
        super("Aim Assist", "Smoothly pulls your aim toward nearby targets.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null || (onClick.get() && !mc.options.attackKey.isPressed())) return;
        Entity best = null;
        double bestAngle = fov.get();
        for (Entity e : mc.world.getEntities()) {
            if (!(Targets.validPlayer(e) || (mobs.get() && e instanceof Monster && e.isAlive()))) continue;
            if (e.distanceTo(mc.player) > range.get()) continue;
            double angle = Rotations.angleTo(point(e));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = e;
            }
        }
        if (best != null) Rotations.approach(point(best), speed.getFloat());
    }

    private Vec3d point(Entity e) {
        Vec3d pos = e.getEntityPos();
        return switch (aimAt.get()) {
            case "Head" -> pos.add(0, e instanceof LivingEntity ? e.getStandingEyeHeight() : e.getHeight(), 0);
            case "Feet" -> pos.add(0, 0.2, 0);
            default -> pos.add(0, e.getHeight() * 0.6, 0);
        };
    }
}
