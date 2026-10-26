package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class ESP extends Module {
    private final EntityFilter filter = new EntityFilter(this, true, false, false);
    private final NumberSetting range = add(new NumberSetting("Range", "Max distance in blocks.", 256, 16, 1024, 16));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity, 0 for outline only.", 40, 0, 255, 5));

    private int count;

    public ESP() {
        super("ESP", "Draws boxes around entities through walls.", Category.RENDER);
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        double maxSq = range.get() * range.get();
        count = 0;
        for (Entity e : mc.world.getEntities()) {
            if (!filter.test(e, mc.player)) continue;
            if (e.squaredDistanceTo(mc.player) > maxSq) continue;
            Vec3d lerp = e.getLerpedPos(tickDelta);
            Box box = e.getBoundingBox().offset(lerp.subtract(e.getEntityPos()));
            r.box(box, EntityFilter.color(e), fill.getInt());
            count++;
        }
    }

    @Override
    public String getInfo() {
        return String.valueOf(count);
    }
}
