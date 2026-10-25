package dev.loot.client.module.render;

import dev.loot.client.LootClient;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
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
    private final BoolSetting rainbow = add(new BoolSetting("Rainbow", "Rainbow module list.", true));

    public Hud() {
        super("HUD", "On-screen info overlay.", Category.RENDER);
        setEnabled(true);
    }

    @Override
    public void onRenderHud(DrawContext ctx, float tickDelta) {
        if (mc.player == null || mc.options.hudHidden) return;
        var tr = mc.textRenderer;
        int width = ctx.getScaledWindowWidth();
        int height = ctx.getScaledWindowHeight();

        if (watermark.get()) {
            ctx.drawTextWithShadow(tr, "Loot Client " + LootClient.VERSION, 2, 2, 0xFFFFAA00);
        }

        if (moduleList.get()) {
            List<Module> enabled = LootClient.modules().getModules().stream()
                .filter(m -> m.isEnabled() && m != this)
                .sorted(Comparator.comparingInt((Module m) -> tr.getWidth(label(m))).reversed())
                .toList();
            int y = 2;
            for (int i = 0; i < enabled.size(); i++) {
                String text = label(enabled.get(i));
                int color = rainbow.get() ? ColorUtil.rainbow(i * 150L, 0.6f, 1f) : 0xFFFFAA00;
                ctx.drawTextWithShadow(tr, text, width - tr.getWidth(text) - 2, y, color);
                y += tr.fontHeight + 1;
            }
        }

        int y = height - tr.fontHeight - 2;
        if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) y -= 14;

        if (coords.get()) {
            double x = mc.player.getX(), py = mc.player.getY(), z = mc.player.getZ();
            boolean nether = mc.world.getRegistryKey() == World.NETHER;
            String other = nether
                ? String.format(" §7[§f%.0f, %.0f§7 ow]", x * 8, z * 8)
                : mc.world.getRegistryKey() == World.OVERWORLD ? String.format(" §7[§f%.0f, %.0f§7 nether]", x / 8, z / 8) : "";
            ctx.drawTextWithShadow(tr, String.format("§7XYZ §f%.1f %.1f %.1f", x, py, z) + other, 2, y, 0xFFFFFFFF);
            y -= tr.fontHeight + 1;
        }
        if (ping.get() && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                ctx.drawTextWithShadow(tr, "§7Ping §f" + entry.getLatency() + "ms", 2, y, 0xFFFFFFFF);
                y -= tr.fontHeight + 1;
            }
        }
        if (fps.get()) {
            ctx.drawTextWithShadow(tr, "§7FPS §f" + mc.getCurrentFps(), 2, y, 0xFFFFFFFF);
        }
    }

    private static String label(Module m) {
        String info = m.getInfo();
        return info == null ? m.getName() : m.getName() + " §7" + info;
    }
}
