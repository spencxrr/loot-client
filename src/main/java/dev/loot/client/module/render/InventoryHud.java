package dev.loot.client.module.render;

import dev.loot.client.gui.Theme;
import dev.loot.client.gui.UI;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.ModeSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Your main inventory (the 27 slots above the hotbar) always on screen. */
public class InventoryHud extends Module {
    private final ModeSetting corner = add(new ModeSetting("Position", "Where to put it.", "BottomRight", "BottomRight", "BottomLeft", "TopLeft", "TopRight"));

    public InventoryHud() {
        super("Inventory HUD", "Shows your inventory on the HUD.", Category.RENDER);
    }

    @Override
    public void onRenderHud(DrawContext ctx, float tickDelta) {
        if (mc.options.hudHidden) return;
        int cell = 18, pad = 5;
        int w = 9 * cell + pad * 2, h = 3 * cell + pad * 2;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int x = corner.get().endsWith("Right") ? sw - w - 4 : 4;
        int y = corner.get().startsWith("Bottom") ? sh - h - 4 : 4;
        if (corner.is("TopLeft")) y = 24;
        UI.roundRect(ctx, x, y, x + w, y + h, 6, Theme.SURFACE);
        for (int i = 0; i < 27; i++) {
            int cx = x + pad + (i % 9) * cell, cy = y + pad + (i / 9) * cell;
            UI.roundRect(ctx, cx + 1, cy + 1, cx + cell - 1, cy + cell - 1, 3, Theme.SURFACE_RAISED);
            ItemStack st = mc.player.getInventory().getStack(9 + i);
            if (st.isEmpty()) continue;
            ctx.drawItem(st, cx + 1, cy + 1);
            ctx.drawStackOverlay(mc.textRenderer, st, cx + 1, cy + 1);
        }
    }
}
