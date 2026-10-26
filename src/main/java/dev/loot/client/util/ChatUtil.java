package dev.loot.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatUtil {
    private ChatUtil() {}

    private static MutableText prefix() {
        return Text.literal("[").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("Loot").styled(s -> s.withColor(dev.loot.client.gui.Theme.ACCENT & 0xFFFFFF)))
            .append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
    }

    public static void info(String message) {
        send(prefix().append(Text.literal(message).formatted(Formatting.GRAY)));
    }

    public static void error(String message) {
        send(prefix().append(Text.literal(message).formatted(Formatting.RED)));
    }

    /** "message x, y, z" with the coordinates clickable to copy. */
    public static void coords(String message, int x, int y, int z) {
        String c = x + " " + y + " " + z;
        send(prefix().append(Text.literal(message + " ").formatted(Formatting.GRAY))
            .append(Text.literal(x + ", " + y + ", " + z).setStyle(net.minecraft.text.Style.EMPTY.withColor(0xC9CFFF).withUnderline(true)
                .withClickEvent(new net.minecraft.text.ClickEvent.CopyToClipboard(c)))));
    }

    public static void ping() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1f, 1.6f);
    }

    public static void send(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(text);
    }
}
