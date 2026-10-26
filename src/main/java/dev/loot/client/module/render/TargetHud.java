package dev.loot.client.module.render;

import dev.loot.client.gui.Theme;
import dev.loot.client.gui.UI;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.Targets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** Card under the crosshair showing who you're fighting: head, health, distance and armor. */
public class TargetHud extends Module {
    private final NumberSetting offsetY = add(new NumberSetting("OffsetY", "Distance below the crosshair.", 30, 0, 200, 2));
    private final BoolSetting closest = add(new BoolSetting("Closest", "Fall back to the closest player when you haven't hit anyone.", true));
    private final NumberSetting range = add(new NumberSetting("Range", "Max distance for the fallback.", 8, 3, 32, 1));

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private float shownHealth = -1;
    private LivingEntity shownFor;
    private float fade;

    public TargetHud() {
        super("Target HUD", "Card showing your current target's health and gear.", Category.RENDER);
    }

    private LivingEntity target() {
        LivingEntity t = Targets.lastTarget;
        if (t != null && t.isAlive() && t != mc.player && System.currentTimeMillis() - Targets.lastTargetTime < 6000
            && t.squaredDistanceTo(mc.player) < 32 * 32) return t;
        return closest.get() ? Targets.closestPlayer(range.get()) : null;
    }

    @Override
    public void onRenderHud(DrawContext ctx, float tickDelta) {
        if (mc.options.hudHidden) return;
        LivingEntity t = target();
        fade += ((t != null ? 1 : 0) - fade) * 0.25f;
        if (t != null && t != shownFor) {
            shownFor = t;
            shownHealth = t.getHealth();
        }
        if (fade < 0.05f || shownFor == null) return;
        LivingEntity e = shownFor;

        int w = 150, h = 46;
        int x = ctx.getScaledWindowWidth() / 2 - w / 2;
        int y = ctx.getScaledWindowHeight() / 2 + offsetY.getInt();
        int alpha = (int) (fade * 255);
        UI.roundRect(ctx, x, y, x + w, y + h, 7, withAlpha(Theme.SURFACE, (int) (fade * 0xEE)));

        int head = 30;
        if (e instanceof PlayerEntity p && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if (entry != null) PlayerSkinDrawer.draw(ctx, entry.getSkinTextures(), x + 8, y + 8, head);
        } else {
            UI.roundRect(ctx, x + 8, y + 8, x + 8 + head, y + 8 + head, 4, Theme.SURFACE_RAISED);
        }

        int tx = x + 8 + head + 8;
        String name = e instanceof PlayerEntity p ? p.getGameProfile().name() : e.getName().getString();
        UI.drawBold(ctx, name, tx, y + 7, withAlpha(Theme.TEXT, alpha));

        float hp = e.getHealth() + e.getAbsorptionAmount();
        shownHealth += (hp - shownHealth) * 0.15f;
        float pct = Math.max(0, Math.min(1, shownHealth / e.getMaxHealth()));
        int barX = tx, barY = y + 21, barW = x + w - 8 - tx;
        UI.roundRect(ctx, barX, barY, barX + barW, barY + 5, 2.5f, withAlpha(Theme.TOGGLE_OFF, alpha));
        int fillColor = Theme.lerp(0xFFFF4D5E, Theme.ACCENT, pct);
        if (pct > 0.02f) UI.roundRect(ctx, barX, barY, barX + Math.max(5, (int) (barW * pct)), barY + 5, 2.5f, withAlpha(fillColor, alpha));

        float mine = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        String status = mine > hp + 2 ? "Winning" : mine < hp - 2 ? "Losing" : "Even";
        int statusColor = mine > hp + 2 ? Theme.GOOD : mine < hp - 2 ? Theme.BAD : Theme.TEXT_MUTED;
        UI.drawText(ctx, String.format("%.1f hp", hp / 2f), tx, y + 30, withAlpha(Theme.TEXT_MUTED, alpha));
        UI.drawTextRight(ctx, status, x + w - 8, y + 30, withAlpha(statusColor, alpha));

        // gear row along the right of the name
        int ix = x + w - 8;
        for (int i = ARMOR.length - 1; i >= 0; i--) {
            ItemStack st = e.getEquippedStack(ARMOR[i]);
            if (st.isEmpty()) continue;
            ix -= 11;
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(ix, y + 5);
            m.scale(0.65f, 0.65f);
            ctx.drawItem(st, 0, 0);
            m.popMatrix();
        }
    }

    private static int withAlpha(int argb, int alpha) {
        int a = (argb >>> 24) * alpha / 255;
        return (argb & 0x00FFFFFF) | (a << 24);
    }
}
