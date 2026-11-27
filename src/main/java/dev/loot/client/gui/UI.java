package dev.loot.client.gui;

import dev.loot.client.LootClient;
import dev.loot.client.module.client.ClickGUI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Drawing helpers for the client's own look: TTF text and rounded shapes.
 *
 * Text objects and widths are cached per string, so the menu and HUD don't rebuild styled text
 * and re-measure every label every frame. The caches reset when the font or text size changes.
 * {@link #alpha} fades everything drawn through here, which the menu uses for its open and close
 * animation.
 */
public final class UI {
    private UI() {}

    /** Global opacity multiplier for everything drawn through UI. Reset to 1 after use. */
    public static float alpha = 1f;

    private static final Map<String, StyleSpriteSource.Font> FONTS = new HashMap<>();
    private static final int CACHE_LIMIT = 4096;
    private static final Map<String, Text> TEXT = new HashMap<>();
    private static final Map<String, Text> BOLD = new HashMap<>();
    private static final Map<String, Integer> WIDTH = new HashMap<>();
    private static final Map<String, Integer> WIDTH_BOLD = new HashMap<>();

    private static ClickGUI gui;
    private static String fontRaw;
    private static String fontName = "poppins";
    private static Style plain, boldStyle;
    private static float cachedScale = 1f;

    private static ClickGUI settings() {
        if (gui == null && LootClient.modules() != null) gui = LootClient.modules().get(ClickGUI.class);
        return gui;
    }

    /** Rebuilds styles and drops caches if the font or text size setting changed. Cheap when nothing changed. */
    private static void validate() {
        ClickGUI g = settings();
        String raw = g == null ? "Poppins" : g.font.get();
        float scale = g == null ? 1f : g.fontSize.getFloat() / 100f;
        if (plain == null || !raw.equals(fontRaw)) {
            fontRaw = raw;
            fontName = raw.toLowerCase(Locale.ROOT);
            plain = style(false);
            boldStyle = style(true);
            TEXT.clear();
            BOLD.clear();
            WIDTH.clear();
            WIDTH_BOLD.clear();
            CUTS.clear();
        }
        if (scale != cachedScale) {
            cachedScale = scale;
            WIDTH.clear();
            WIDTH_BOLD.clear();
            CUTS.clear();
        }
    }

    private static Style style(boolean bold) {
        if (fontName.equals("minecraft")) return Style.EMPTY.withBold(bold);
        String id = bold ? fontName + "_bold" : fontName;
        return Style.EMPTY.withFont(FONTS.computeIfAbsent(id, k -> new StyleSpriteSource.Font(Identifier.of("lootclient", k))));
    }

    /** Text size multiplier from the Click GUI settings. */
    public static float textScale() {
        validate();
        return cachedScale;
    }

    /** Sub-pixel resolution used for rounded corners, so curves don't look blocky at GUI scale 2-3. */
    private static final int SUB = 2;

    private static TextRenderer tr() {
        return MinecraftClient.getInstance().textRenderer;
    }

    private static Text cached(Map<String, Text> cache, String s, Style st) {
        Text t = cache.get(s);
        if (t == null) {
            if (cache.size() > CACHE_LIMIT) cache.clear();
            t = Text.literal(s).setStyle(st);
            cache.put(s, t);
        }
        return t;
    }

    public static Text text(String s) {
        validate();
        return cached(TEXT, s, plain);
    }

    public static Text bold(String s) {
        validate();
        return cached(BOLD, s, boldStyle);
    }

    public static int width(String s) {
        validate();
        Integer w = WIDTH.get(s);
        if (w == null) {
            if (WIDTH.size() > CACHE_LIMIT) WIDTH.clear();
            w = (int) Math.ceil(tr().getWidth(cached(TEXT, s, plain)) * cachedScale);
            WIDTH.put(s, w);
        }
        return w;
    }

    public static int widthBold(String s) {
        validate();
        Integer w = WIDTH_BOLD.get(s);
        if (w == null) {
            if (WIDTH_BOLD.size() > CACHE_LIMIT) WIDTH_BOLD.clear();
            w = (int) Math.ceil(tr().getWidth(cached(BOLD, s, boldStyle)) * cachedScale);
            WIDTH_BOLD.put(s, w);
        }
        return w;
    }

    public static int height() {
        return 9;
    }

    /** Applies the global {@link #alpha} to an ARGB color. */
    public static int fade(int color) {
        if (alpha >= 1f) return color;
        int a = (int) ((color >>> 24) * Math.max(0f, alpha));
        return a << 24 | color & 0xFFFFFF;
    }

    /** Multiplies a color's alpha by t (0..1). */
    public static int withAlpha(int color, float t) {
        int a = (int) ((color >>> 24) * Math.max(0f, Math.min(1f, t)));
        return a << 24 | color & 0xFFFFFF;
    }

    public static void fill(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int c = fade(color);
        if (c >>> 24 == 0) return;
        ctx.fill(x1, y1, x2, y2, c);
    }

    public static void drawText(DrawContext ctx, String s, int x, int y, int color) {
        draw(ctx, text(s), x, y, color);
    }

    public static void drawBold(DrawContext ctx, String s, int x, int y, int color) {
        draw(ctx, bold(s), x, y, color);
    }

    private static void draw(DrawContext ctx, Text t, int x, int y, int color) {
        int c = fade(color);
        // the text renderer drops very transparent glyphs anyway; skip the work
        if (c >>> 24 < 6) return;
        float ts = cachedScale;
        if (ts == 1f) {
            ctx.drawText(tr(), t, x, y, c, false);
            return;
        }
        // scale around the line's vertical middle so rows stay centered
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y + 4.5f * (1 - ts));
        m.scale(ts, ts);
        ctx.drawText(tr(), t, 0, 0, c, false);
        m.popMatrix();
    }

    public static void drawTextRight(DrawContext ctx, String s, int right, int y, int color) {
        drawText(ctx, s, right - width(s), y, color);
    }

    public static void drawTextCentered(DrawContext ctx, String s, int cx, int y, int color) {
        drawText(ctx, s, cx - width(s) / 2, y, color);
    }

    /** Cuts s down to fit maxWidth, ending with "..", using cached widths. */
    public static String ellipsize(String s, int maxWidth) {
        if (width(s) <= maxWidth) return s;
        Cut c = CUTS.get(s);
        if (c != null && c.max() == maxWidth && !c.fromEnd()) return c.result();
        String r = cut(s, maxWidth);
        if (CUTS.size() > CACHE_LIMIT) CUTS.clear();
        CUTS.put(s, new Cut(maxWidth, false, r));
        return r;
    }

    /** Last cut result per string, so overflowing labels don't re-measure substrings every frame. */
    private record Cut(int max, boolean fromEnd, String result) {}

    private static final Map<String, Cut> CUTS = new HashMap<>();

    private static String cut(String s, int maxWidth) {
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (width(s.substring(0, mid) + "..") <= maxWidth) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo) + "..";
    }

    /** Same as {@link #ellipsize} but keeps the end of the string, for text being typed. */
    public static String ellipsizeStart(String s, int maxWidth) {
        if (width(s) <= maxWidth) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (width(".." + s.substring(mid)) <= maxWidth) hi = mid;
            else lo = mid + 1;
        }
        return ".." + s.substring(lo);
    }

    /** Corner insets per radius (in sub-pixels), so curves aren't recomputed with sqrt every frame. */
    private static final int[][] INSETS = new int[129][];

    private static int[] insets(int r) {
        if (r >= INSETS.length) return computeInsets(r);
        int[] in = INSETS[r];
        if (in == null) INSETS[r] = in = computeInsets(r);
        return in;
    }

    private static int[] computeInsets(int r) {
        int[] in = new int[r];
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            in[i] = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
        }
        return in;
    }

    /** Filled rectangle with rounded corners of the given radius (in GUI pixels, may be fractional via SUB). */
    public static void roundRect(DrawContext ctx, int x1, int y1, int x2, int y2, float radius, int color) {
        roundRect(ctx, x1, y1, x2, y2, radius, color, true, true);
    }

    public static void roundRect(DrawContext ctx, int x1, int y1, int x2, int y2, float radius, int color, boolean roundTop, boolean roundBottom) {
        int c = fade(color);
        if (c >>> 24 == 0 || x2 <= x1 || y2 <= y1) return;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.scale(1f / SUB, 1f / SUB);
        int ax = x1 * SUB, ay = y1 * SUB, bx = x2 * SUB, by = y2 * SUB;
        int r = Math.max(0, Math.min(Math.round(radius * SUB), Math.min(bx - ax, by - ay) / 2));
        int top = roundTop ? r : 0;
        int bottom = roundBottom ? r : 0;
        ctx.fill(ax, ay + top, bx, by - bottom, c);
        if (r > 0) {
            int[] in = insets(r);
            for (int i = 0; i < r; i++) {
                int inset = in[i];
                if (roundTop) ctx.fill(ax + inset, ay + i, bx - inset, ay + i + 1, c);
                if (roundBottom) ctx.fill(ax + inset, by - i - 1, bx - inset, by - i, c);
            }
        }
        m.popMatrix();
    }

    /**
     * Soft drop shadow: a few stacked, expanding translucent round rects. Cheap, and makes panels
     * read as floating above the world instead of pasted onto it.
     */
    public static void shadow(DrawContext ctx, int x1, int y1, int x2, int y2, float radius, int strength) {
        for (int i = 3; i >= 1; i--) {
            int a = strength / (i + 1);
            roundRect(ctx, x1 - i, y1 - i + 1, x2 + i, y2 + i + 1, radius + i, a << 24);
        }
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

    // ---- easing ---------------------------------------------------------------------------

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    public static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    /** Overshoots a little past 1 before settling, for a springy pop. */
    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.4f, c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    public static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }
}
