package dev.loot.client.module.render;

import dev.loot.client.LootClient;
import dev.loot.client.gui.Anim;
import dev.loot.client.gui.Theme;
import dev.loot.client.gui.UI;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class Hud extends Module {
    private final BoolSetting watermark = add(new BoolSetting("Watermark", "Client name in the corner.", true));
    private final BoolSetting moduleList = add(new BoolSetting("ModuleList", "List of enabled modules.", true));
    private final BoolSetting coords = add(new BoolSetting("Coords", "Coordinates, with the other dimension's.", true));
    private final BoolSetting fps = add(new BoolSetting("FPS", "Frames per second.", true));
    private final BoolSetting ping = add(new BoolSetting("Ping", "Your latency to the server.", true));
    private final BoolSetting wave = add(new BoolSetting("Wave", "Animated shimmer on the module list.", true));

    public Hud() {
        super("HUD", "On-screen info overlay.", Category.RENDER);
        setEnabled(true);
    }

    @Override
    public void onRenderHud(DrawContext ctx, float tickDelta) {
        if (mc.player == null || mc.options.hudHidden) return;
        int width = ctx.getScaledWindowWidth();
        int height = ctx.getScaledWindowHeight();

        if (watermark.get()) {
            String ver = LootClient.VERSION;
            int w = UI.widthBold("Loot") + UI.width(ver) + 22;
            UI.roundRect(ctx, 4, 4, 4 + w, 19, 6, Theme.SURFACE);
            UI.roundRect(ctx, 9, 9, 14, 14, 2.5f, Theme.ACCENT);
            UI.drawBold(ctx, "Loot", 18, 7, Theme.TEXT);
            UI.drawText(ctx, ver, 22 + UI.widthBold("Loot"), 7, Theme.TEXT_FAINT);
        }

        if (moduleList.get()) renderModuleList(ctx, width);

        int y = height - 14;
        if (mc.currentScreen instanceof ChatScreen) y -= 14;

        if (coords.get()) {
            double x = mc.player.getX(), py = mc.player.getY(), z = mc.player.getZ();
            String main = String.format("%.0f, %.0f, %.0f", x, py, z);
            String other = mc.world.getRegistryKey() == World.NETHER ? String.format("  ow %.0f, %.0f", x * 8, z * 8)
                : mc.world.getRegistryKey() == World.OVERWORLD ? String.format("  nether %.0f, %.0f", x / 8, z / 8) : "";
            y = pill(ctx, "XYZ", main + other, y);
        }
        if (ping.get() && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) y = pill(ctx, "Ping", entry.getLatency() + "ms", y);
        }
        if (fps.get()) pill(ctx, "FPS", String.valueOf(mc.getCurrentFps()), y);
    }

    private int pill(DrawContext ctx, String label, String value, int y) {
        int w = UI.width(label) + UI.width(value) + 14;
        UI.roundRect(ctx, 4, y, 4 + w, y + 12, 3, Theme.SURFACE);
        UI.drawText(ctx, label, 9, y + 2, Theme.ACCENT);
        UI.drawText(ctx, value, 13 + UI.width(label), y + 2, Theme.TEXT);
        return y - 14;
    }

    // ---- module list ----------------------------------------------------------------------

    private final Map<Module, Anim> slide = new IdentityHashMap<>();
    private final Map<Module, String[]> labels = new IdentityHashMap<>();
    private final List<Module> shown = new ArrayList<>();
    private final Comparator<Module> byWidth = Comparator.comparingInt((Module m) -> UI.width(label(m))).reversed();
    private long lastFrame;

    private void renderModuleList(DrawContext ctx, int width) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        // modules slide in when enabled and slide back out when disabled
        shown.clear();
        for (Module m : LootClient.modules().getModules()) {
            if (m == this) continue;
            Anim a = slide.get(m);
            if (a == null) {
                if (!m.isEnabled()) continue;
                slide.put(m, a = new Anim(0f));
            }
            a.update(m.isEnabled(), 12f, dt);
            if (a.value > 0.01f) shown.add(m);
        }
        shown.sort(byWidth);

        float y = 4;
        try {
            for (int i = 0; i < shown.size(); i++) {
                Module m = shown.get(i);
                float t = UI.easeOutCubic(slide.get(m).value);
                String info = m.getInfo();
                int nameW = UI.width(m.getName());
                int w = UI.width(label(m)) + 10;
                int x = width - 4 - w + Math.round((1f - t) * (w + 8));
                int iy = Math.round(y);
                UI.alpha = t;
                UI.roundRect(ctx, x, iy, x + w, iy + 12, 3, Theme.SURFACE);
                float wt = wave.get() ? (float) (Math.sin((now / 600.0) - i * 0.45) * 0.5 + 0.5) : 1f;
                int color = Theme.lerp(Theme.ACCENT_SOFT, Theme.ACCENT, wt);
                UI.fill(ctx, x + w - 2, iy + 2, x + w, iy + 10, color);
                UI.drawText(ctx, m.getName(), x + 4, iy + 2, color);
                if (info != null) UI.drawText(ctx, info, x + 8 + nameW, iy + 2, Theme.TEXT_FAINT);
                y += 13 * t;
            }
        } finally {
            UI.alpha = 1f;
        }
    }

    /** Name plus info, rebuilt only when the info text changes. */
    private String label(Module m) {
        String info = m.getInfo();
        if (info == null) return m.getName();
        String[] c = labels.get(m);
        if (c == null) labels.put(m, c = new String[2]);
        if (!info.equals(c[0])) {
            c[0] = info;
            c[1] = m.getName() + "  " + info;
        }
        return c[1];
    }
}
