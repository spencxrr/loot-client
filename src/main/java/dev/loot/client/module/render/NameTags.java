package dev.loot.client.module.render;

import dev.loot.client.LootClient;
import dev.loot.client.gui.Theme;
import dev.loot.client.gui.UI;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Projection;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Clean name tags for players: name, health, ping, distance, and their gear. */
public class NameTags extends Module {
    private final NumberSetting scale = add(new NumberSetting("Scale", "Base size.", 1, 0.5, 2, 0.05));
    private final BoolSetting health = add(new BoolSetting("Health", "Show health.", true));
    private final BoolSetting ping = add(new BoolSetting("Ping", "Show ping.", true));
    private final BoolSetting distance = add(new BoolSetting("Distance", "Show distance.", true));
    private final BoolSetting gear = add(new BoolSetting("Gear", "Show armor and held items.", true));
    private final NumberSetting range = add(new NumberSetting("Range", "Max distance.", 256, 16, 1024, 16));

    private static final EquipmentSlot[] GEAR = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
        EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

    public NameTags() {
        super("Name Tags", "Better name tags with health, ping and gear.", Category.RENDER);
    }

    @Override
    public void onRenderHud(DrawContext ctx, float tickDelta) {
        if (mc.options.hudHidden) return;
        double maxSq = range.get() * range.get();
        Vec3d cam = Projection.camera();
        List<PlayerEntity> players = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers())
            if (p != mc.player && p.isAlive() && p.squaredDistanceTo(cam) <= maxSq) players.add(p);
        // far ones first so close tags draw on top
        players.sort(Comparator.comparingDouble((PlayerEntity p) -> -p.squaredDistanceTo(cam)));
        for (PlayerEntity p : players) draw(ctx, p, tickDelta);
    }

    private void draw(DrawContext ctx, PlayerEntity p, float tickDelta) {
        Vec3d head = p.getLerpedPos(tickDelta).add(0, p.getHeight() + 0.35, 0);
        Vec3d screen = Projection.toScreen(head);
        if (screen == null) return;
        double dist = Math.sqrt(p.squaredDistanceTo(Projection.camera()));
        float s = (float) (scale.get() * Math.max(0.55, Math.min(1.25, 8 / Math.max(dist, 1))));

        String name = p.getGameProfile().name();
        boolean friend = LootClient.friends().isFriend(name);
        List<String> parts = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        parts.add(name);
        colors.add(friend ? 0xFF5CE68A : Theme.TEXT);
        if (health.get()) {
            float hp = p.getHealth() + p.getAbsorptionAmount();
            float pct = Math.min(1f, hp / p.getMaxHealth());
            parts.add(String.format("%.1f", hp / 2f));
            colors.add(Theme.lerp(0xFFFF4D5E, 0xFF5CE68A, pct));
        }
        if (ping.get() && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if (entry != null) {
                parts.add(entry.getLatency() + "ms");
                colors.add(Theme.TEXT_FAINT);
            }
        }
        if (distance.get()) {
            parts.add(String.format("%.0fm", dist));
            colors.add(Theme.TEXT_FAINT);
        }

        int gapW = 5;
        int width = 0;
        for (String part : parts) width += UI.width(part);
        width += gapW * (parts.size() - 1) + 10;

        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate((float) screen.x, (float) screen.y);
        m.scale(s, s);
        int x = -width / 2, y = -12;
        UI.roundRect(ctx, x, y, x + width, y + 12, 4, Theme.SURFACE);
        if (friend) ctx.fill(x + 3, y + 11, x + width - 3, y + 12, 0xFF5CE68A);
        int cx = x + 5;
        for (int i = 0; i < parts.size(); i++) {
            UI.drawText(ctx, parts.get(i), cx, y + 2, colors.get(i));
            cx += UI.width(parts.get(i)) + gapW;
        }

        if (gear.get()) {
            List<ItemStack> items = new ArrayList<>();
            for (EquipmentSlot slot : GEAR) {
                ItemStack st = p.getEquippedStack(slot);
                if (!st.isEmpty()) items.add(st);
            }
            int ix = -items.size() * 9;
            for (ItemStack st : items) {
                ctx.drawItem(st, ix + 1, y - 18);
                ctx.drawStackOverlay(mc.textRenderer, st, ix + 1, y - 18);
                ix += 18;
            }
        }
        m.popMatrix();
    }
}
