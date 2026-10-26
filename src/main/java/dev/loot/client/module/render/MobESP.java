package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class MobESP extends Module {
    private final BoolSetting hostiles = add(new BoolSetting("Hostiles", "Hostile mobs.", true));
    private final BoolSetting passives = add(new BoolSetting("Passives", "Animals and villagers.", false));
    private final BoolSetting items = add(new BoolSetting("Items", "Dropped items.", false));
    private final NumberSetting range = add(new NumberSetting("Range", "Max distance in blocks.", 128, 16, 1024, 16));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity, 0 for outline only.", 30, 0, 255, 5));

    private int count;

    public MobESP() {
        super("Mob ESP", "Boxes around mobs and items through walls.", Category.RENDER);
    }

    private int colorFor(Entity e) {
        if (e instanceof PlayerEntity) return 0;
        if (e instanceof Monster && hostiles.get()) return 0xFFFF9A5C;
        if (e instanceof PassiveEntity && passives.get()) return 0xFF6BE0A0;
        if (e instanceof ItemEntity && items.get()) return 0xFF7AD3FF;
        return 0;
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        double maxSq = range.get() * range.get();
        count = 0;
        for (Entity e : mc.world.getEntities()) {
            if (!e.isAlive() || (e instanceof LivingEntity le && le.isDead())) continue;
            int color = colorFor(e);
            if (color == 0 || e.squaredDistanceTo(mc.player) > maxSq) continue;
            Vec3d lerp = e.getLerpedPos(tickDelta);
            Box box = e.getBoundingBox().offset(lerp.subtract(e.getEntityPos()));
            r.box(box, color, fill.getInt());
            count++;
        }
    }

    @Override
    public String getInfo() {
        return String.valueOf(count);
    }
}
