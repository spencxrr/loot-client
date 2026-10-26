package dev.loot.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/** Drawing helpers for the client's own look: TTF text and rounded shapes. */
public final class UI {
    private UI() {}

    private static final StyleSpriteSource.Font FONT = new StyleSpriteSource.Font(Identifier.of("lootclient", "ui"));
    private static final StyleSpriteSource.Font FONT_BOLD = new StyleSpriteSource.Font(Identifier.of("lootclient", "ui_bold"));

    /** Sub-pixel resolution used for rounded corners, so curves don't look blocky at GUI scale 2-3. */
    private static final int SUB = 2;

    private static TextRenderer tr() {
        return MinecraftClient.getInstance().textRenderer;
    }

    public static Text text(String s) {
        return Text.literal(s).styled(st -> st.withFont(FONT));
    }

    public static Text bold(String s) {
        return Text.literal(s).styled(st -> st.withFont(FONT_BOLD));
    }

    public static int width(String s) {
        return tr().getWidth(text(s));
    }

    public static int widthBold(String s) {
        return tr().getWidth(bold(s));
    }

    public static int height() {
        return 9;
    }

    public static void drawText(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(tr(), text(s), x, y, color, false);
    }

    public static void drawBold(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(tr(), bold(s), x, y, color, false);
    }

    public static void drawTextRight(DrawContext ctx, String s, int right, int y, int color) {
        drawText(ctx, s, right - width(s), y, color);
    }

    /** Filled rectangle with rounded corners of the given radius (in GUI pixels, may be fractional via SUB). */
    public static void roundRect(DrawContext ctx, int x1, int y1, int x2, int y2, float radius, int color) {
        roundRect(ctx, x1, y1, x2, y2, radius, color, true, true);
    }

    public static void roundRect(DrawContext ctx, int x1, int y1, int x2, int y2, float radius, int color, boolean roundTop, boolean roundBottom) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.scale(1f / SUB, 1f / SUB);
        int ax = x1 * SUB, ay = y1 * SUB, bx = x2 * SUB, by = y2 * SUB;
        int r = Math.max(0, Math.min(Math.round(radius * SUB), Math.min(bx - ax, by - ay) / 2));
        int top = roundTop ? r : 0;
        int bottom = roundBottom ? r : 0;
        ctx.fill(ax, ay + top, bx, by - bottom, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            if (roundTop) ctx.fill(ax + inset, ay + i, bx - inset, ay + i + 1, color);
            if (roundBottom) ctx.fill(ax + inset, by - i - 1, bx - inset, by - i, color);
        }
        m.popMatrix();
    }

    /** Pill-shaped toggle switch, t = 0 (off) .. 1 (on) for smooth animation. */
    public static void toggle(DrawContext ctx, int x, int y, float t) {
        int w = 16, h = 8;
        roundRect(ctx, x, y, x + w, y + h, h / 2f, Theme.lerp(Theme.TOGGLE_OFF, Theme.ACCENT_SOFT, t));
        Matrix3x2fStack m = ctx.getMatrices();
        int knob = 6;
        float kx = x + 1 + (w - knob - 2) * t;
        m.pushMatrix();
        m.translate(kx - (int) kx, 0);
        roundRect(ctx, (int) kx, y + 1, (int) kx + knob, y + 1 + knob, knob / 2f, Theme.lerp(Theme.KNOB_OFF, Theme.KNOB_ON, t));
        m.popMatrix();
    }
}
