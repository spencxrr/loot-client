package dev.loot.client.module.render;

import dev.loot.client.LootClient;
import dev.loot.client.gui.Theme;
import dev.loot.client.gui.UI;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Small animated cards in the top-right corner for what the finders find. Each card slides in,
 * says what was found and where, and slides back out. Off = finders post to chat like before.
 */
public class Notifications extends Module {
    private final NumberSetting duration = add(new NumberSetting("Duration", "Seconds each card stays.", 6, 2, 20, 0.5));
    private final NumberSetting max = add(new NumberSetting("Max", "Cards shown at once.", 4, 1, 8, 1));
    private final BoolSetting chat = add(new BoolSetting("Chat", "Also post finds in chat (with click-to-copy coords).", true));
    private final BoolSetting sound = add(new BoolSetting("Sound", "Ping when a card shows.", true));

    private static final int WIDTH_MAX = 210, PAD = 6;
    private static final long IN_MS = 260, OUT_MS = 320;

    private static final class Card {
        final String title;
        final List<String> lines;
        final int color;
        final long born = System.currentTimeMillis();
        float y = Float.NaN;

        Card(String title, List<String> lines, int color) {
            this.title = title;
            this.lines = lines;
            this.color = color;
        }
    }

    private final List<Card> cards = new ArrayList<>();

    public Notifications() {
        super("Notifications", "Animated top-right cards for finder alerts.", Category.RENDER);
        setEnabled(true);
    }

    /**
     * A find: a card (when this module is on), a chat line with copyable coords, and a ping.
     * {@code color} is the finder's own color (red for Sus, yellow for Light...). The first detail
     * goes on the chat line, the rest follow it as separate chat lines.
     */
    public static void found(String title, int color, int x, int y, int z, String... details) {
        Notifications n = LootClient.modules() == null ? null : LootClient.modules().get(Notifications.class);
        boolean cards = n != null && n.isEnabled();
        List<String> lines = new ArrayList<>();
        for (String d : details) if (d != null && !d.isEmpty()) lines.add(d);
        if (!cards || n.chat.get()) {
            ChatUtil.coords(title + (lines.isEmpty() ? "" : ": " + lines.get(0)) + " at", x, y, z);
            for (int i = 1; i < lines.size(); i++) ChatUtil.info(lines.get(i));
        }
        if (cards) {
            List<String> card = new ArrayList<>(lines);
            card.add(x + ", " + y + ", " + z);
            n.push(new Card(title, card, 0xFF000000 | (color & 0xFFFFFF)));
        }
        if (!cards || n.sound.get()) ChatUtil.ping();
    }

    private void push(Card c) {
        // main thread only (finders alert from ticks), same as rendering
        cards.add(0, c);
        while (cards.size() > max.getInt() + 2) cards.remove(cards.size() - 1);
    }

    @Override
    public void onDisable() {
        cards.clear();
    }

    @Override
    public void onRenderHud(DrawContext ctx, float tickDelta) {
        if (cards.isEmpty() || mc.options.hudHidden) return;
        long now = System.currentTimeMillis();
        long life = (long) (duration.get() * 1000);
        cards.removeIf(c -> now - c.born > life + OUT_MS);

        int right = ctx.getScaledWindowWidth() - 4;
        // stay under the vanilla potion icons
        float top = 4;
        if (mc.player != null && !mc.player.getStatusEffects().isEmpty()) top = 52;

        float target = top;
        int shown = 0;
        for (Card c : cards) {
            if (shown++ >= max.getInt()) break;
            int h = height(c);
            if (Float.isNaN(c.y)) c.y = target;
            // older cards glide down when a new one lands on top
            c.y += (target - c.y) * 0.25f;
            draw(ctx, c, now, life, right, Math.round(c.y), h);
            target += h + 4;
        }
    }

    private static int height(Card c) {
        return PAD + 9 + c.lines.size() * 9 + 6 + PAD - 2;
    }

    private void draw(DrawContext ctx, Card c, long now, long life, int right, int y, int h) {
        long age = now - c.born;
        float in = Math.min(1f, age / (float) IN_MS);
        float out = age > life ? Math.min(1f, (age - life) / (float) OUT_MS) : 0f;
        float slideIn = 1 - (float) Math.pow(1 - in, 3);   // ease out
        float slideOut = out * out;                          // ease in
        float alpha = Math.min(in * 1.6f, 1f) * (1 - out);
        if (alpha <= 0.01f) return;

        String title = fit(c.title, true);
        List<String> lines = new ArrayList<>();
        for (String l : c.lines) lines.add(fit(l, false));
        int w = UI.widthBold(title);
        for (String l : lines) w = Math.max(w, UI.width(l));
        w = Math.max(w, 96) + PAD * 2 + 3;

        int x = right - Math.round(w * slideIn) + Math.round((w + 8) * slideOut);
        int a = (int) (alpha * 255);
        UI.roundRect(ctx, x, y, x + w, y + h, 5, withAlpha(Theme.SURFACE, a));
        // accent bar in the finder's color, plus a thin progress line showing time left
        ctx.fill(x + 2, y + 4, x + 4, y + h - 4, withAlpha(c.color, a));
        float left = 1 - Math.min(1f, age / (float) life);
        ctx.fill(x + 6, y + h - 2, x + 6 + Math.round((w - 12) * left), y + h - 1, withAlpha(c.color, a / 2));

        int tx = x + PAD + 3, ty = y + PAD - 1;
        UI.drawBold(ctx, title, tx, ty, withAlpha(c.color, a));
        ty += 10;
        for (String l : lines) {
            UI.drawText(ctx, l, tx, ty, withAlpha(Theme.TEXT, a));
            ty += 9;
        }
        tiny(ctx, "Found With Loot Client", x + w - PAD, y + h - 7, withAlpha(Theme.TEXT_FAINT, a));
    }

    /** Extremely small right-aligned footer text (half size). */
    private static void tiny(DrawContext ctx, String s, int right, int y, int color) {
        float scale = 0.5f;
        int w = (int) Math.ceil(mcText().getWidth(s) * scale);
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(right - w, y);
        m.scale(scale, scale);
        ctx.drawText(mcText(), s, 0, 0, color, false);
        m.popMatrix();
    }

    private static net.minecraft.client.font.TextRenderer mcText() {
        return net.minecraft.client.MinecraftClient.getInstance().textRenderer;
    }

    private static String fit(String s, boolean bold) {
        int max = WIDTH_MAX - PAD * 2;
        if ((bold ? UI.widthBold(s) : UI.width(s)) <= max) return s;
        String cut = s;
        while (cut.length() > 4 && (bold ? UI.widthBold(cut + "...") : UI.width(cut + "...")) > max) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }

    private static int withAlpha(int argb, int a) {
        int base = argb >>> 24;
        return ((base * a / 255) << 24) | (argb & 0xFFFFFF);
    }
}
