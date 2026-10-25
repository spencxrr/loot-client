package dev.loot.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatUtil {
    private ChatUtil() {}

    private static MutableText prefix() {
        return Text.literal("[").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("Loot").formatted(Formatting.GOLD))
            .append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
    }

    public static void info(String message) {
        send(prefix().append(Text.literal(message).formatted(Formatting.GRAY)));
    }

    public static void error(String message) {
        send(prefix().append(Text.literal(message).formatted(Formatting.RED)));
    }

    public static void send(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(text);
    }
}
