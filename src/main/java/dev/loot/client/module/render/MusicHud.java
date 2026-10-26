package dev.loot.client.module.render;

import dev.loot.client.gui.Theme;
import dev.loot.client.gui.UI;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.ModeSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Now-playing card for the game's music (and the client Radio, when it's on). */
public class MusicHud extends Module {
    private final ModeSetting position = add(new ModeSetting("Position", "Where to put it.", "TopCenter", "TopCenter", "BottomRight", "BottomLeft"));

    /** Set by other parts of the client (Radio) to show their track instead of the game's. */
    public static volatile String external;

    public MusicHud() {
        super("Music HUD", "Shows the song that's playing.", Category.RENDER);
    }

    private String current() {
        String ext = external;
        if (ext != null && !ext.isEmpty()) return ext;
        String key = mc.getMusicTracker().getCurrentMusicTranslationKey();
        return key == null ? null : Text.translatable(key).getString();
    }

    @Override
    public void onRenderHud(DrawContext ctx, float tickDelta) {
        if (mc.options.hudHidden) return;
        String track = current();
        if (track == null) return;
        int w = UI.width(track) + 34, h = 18;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int x, y;
        switch (position.get()) {
            case "BottomRight" -> { x = sw - w - 4; y = sh - h - 4; }
            case "BottomLeft" -> { x = 4; y = sh - h - 48; }
            default -> { x = sw / 2 - w / 2; y = 4; }
        }
        UI.roundRect(ctx, x, y, x + w, y + h, 6, Theme.SURFACE);
        // little animated equalizer
        long t = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            double v = 0.5 + 0.5 * Math.sin(t / (140.0 + i * 37) + i * 1.7);
            int bh = 3 + (int) (v * 8);
            int bx = x + 7 + i * 4;
            ctx.fill(bx, y + 13 - bh, bx + 2, y + 13, Theme.lerp(Theme.ACCENT_SOFT, Theme.ACCENT, (float) v));
        }
        UI.drawText(ctx, track, x + 27, y + 5, Theme.TEXT);
    }
}
