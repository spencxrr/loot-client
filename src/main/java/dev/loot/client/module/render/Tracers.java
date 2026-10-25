package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.ModeSetting;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Tracers extends Module {
    private final EntityFilter filter = new EntityFilter(this, true, false, false);
    private final NumberSetting range = add(new NumberSetting("Range", "Max distance in blocks.", 512, 16, 2048, 16));
    private final ModeSetting colorMode = add(new ModeSetting("Color", "Type colors, or red-to-green by distance.", "Distance", "Distance", "Type"));
    private final ModeSetting target = add(new ModeSetting("Target", "Which part of the entity to point at.", "Body", "Feet", "Body", "Head"));

    public Tracers() {
        super("Tracers", "Draws lines from your crosshair to entities.", Category.RENDER);
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        double max = range.get();
        Vec3d start = r.crosshair();
        for (Entity e : mc.world.getEntities()) {
            if (!filter.test(e, mc.player)) continue;
            double dist = e.distanceTo(mc.player);
            if (dist > max) continue;
            Vec3d pos = e.getLerpedPos(tickDelta);
            double yOff = switch (target.get()) {
                case "Feet" -> 0;
                case "Head" -> e.getStandingEyeHeight();
                default -> e.getHeight() / 2;
            };
            r.line(start, pos.add(0, yOff, 0), color(e, dist));
        }
    }

    private int color(Entity e, double dist) {
        if (colorMode.is("Type")) return EntityFilter.color(e);
        float t = (float) MathHelper.clamp(dist / 64.0, 0, 1);
        int red = (int) (255 * (1 - t));
        int green = (int) (255 * t);
        return 0xFF000000 | red << 16 | green << 8 | 0x30;
    }
}
