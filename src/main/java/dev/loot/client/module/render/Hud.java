package dev.loot.client.module.render;

import dev.loot.client.LootClient;
import dev.loot.client.gui.Theme;
import dev.loot.client.gui.UI;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;

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

        if (moduleList.get()) {
            List<Module> enabled = LootClient.modules().getModules().stream()
                .filter(m -> m.isEnabled() && m != this)
                .sorted(Comparator.comparingInt((Module m) -> UI.width(label(m))).reversed())
                .toList();
            int y = 4;
            long now = System.currentTimeMillis();
            for (int i = 0; i < enabled.size(); i++) {
                Module m = enabled.get(i);
                String info = m.getInfo();
                int nameW = UI.width(m.getName());
                int w = UI.width(label(m)) + 10;
                int x = width - 4 - w;
                UI.roundRect(ctx, x, y, x + w, y + 12, 3, Theme.SURFACE);
                float t = wave.get() ? (float) (Math.sin((now / 600.0) - i * 0.45) * 0.5 + 0.5) : 1f;
                int color = Theme.lerp(Theme.ACCENT_SOFT, Theme.ACCENT, t);
                ctx.fill(x + w - 2, y + 2, x + w, y + 10, color);
                UI.drawText(ctx, m.getName(), x + 4, y + 2, color);
                if (info != null) UI.drawText(ctx, info, x + 8 + nameW, y + 2, Theme.TEXT_FAINT);
                y += 13;
            }
        }

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

    private static String label(Module m) {
        String info = m.getInfo();
        return info == null ? m.getName() : m.getName() + "  " + info;
    }
}
