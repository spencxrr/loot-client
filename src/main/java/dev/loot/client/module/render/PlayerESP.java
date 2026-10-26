package dev.loot.client.module.render;

import dev.loot.client.LootClient;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class PlayerESP extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "Max distance in blocks.", 512, 16, 2048, 16));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity, 0 for outline only.", 35, 0, 255, 5));
    private final BoolSetting friends = add(new BoolSetting("Friends", "Show friends too, in green.", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", "Lines from your crosshair to each player.", true));
    private final BoolSetting healthColor = add(new BoolSetting("HealthColor", "Tint enemies from lavender (full) to red (low).", true));

    private int count;

    public PlayerESP() {
        super("Player ESP", "Boxes around players through walls.", Category.RENDER);
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        double maxSq = range.get() * range.get();
        count = 0;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || p.squaredDistanceTo(mc.player) > maxSq) continue;
            boolean friend = LootClient.friends().isFriend(p.getGameProfile().name());
            if (friend && !friends.get()) continue;
            int color;
            if (friend) color = 0xFF5CE68A;
            else if (healthColor.get()) {
                float hp = Math.min(1f, (p.getHealth() + p.getAbsorptionAmount()) / p.getMaxHealth());
                color = dev.loot.client.gui.Theme.lerp(0xFFFF4D5E, dev.loot.client.gui.Theme.ACCENT, hp);
            } else color = dev.loot.client.gui.Theme.ACCENT;
            Vec3d lerp = p.getLerpedPos(tickDelta);
            Box box = p.getBoundingBox().offset(lerp.subtract(p.getEntityPos()));
            r.box(box, color, fill.getInt());
            if (tracers.get()) r.line(r.crosshair(), box.getCenter(), color);
            count++;
        }
    }

    @Override
    public String getInfo() {
        return String.valueOf(count);
    }
}
