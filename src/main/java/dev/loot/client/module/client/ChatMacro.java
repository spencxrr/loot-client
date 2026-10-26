package dev.loot.client.module.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.loot.client.LootClient;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.util.InputUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Keys that send a chat message or command. Manage them with
 * .macro add <key> <message>, .macro remove <key>, .macro list.
 */
public class ChatMacro extends Module {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lootclient-macros.json");
    private final Map<String, String> macros = new TreeMap<>();
    private final Set<Integer> down = new HashSet<>();

    public ChatMacro() {
        super("Chat Macro", "Bind chat messages and commands to keys (.macro).", Category.CLIENT);
        try {
            if (Files.exists(FILE)) {
                Map<String, String> loaded = new Gson().fromJson(Files.readString(FILE), new TypeToken<Map<String, String>>() {}.getType());
                if (loaded != null) macros.putAll(loaded);
            }
        } catch (Exception e) {
            LootClient.LOGGER.error("Failed to load macros", e);
        }
    }

    public Map<String, String> macros() {
        return macros;
    }

    /** Returns false if the key name isn't a real key. */
    public boolean put(String keyName, String message) {
        InputUtil.Key key = InputUtil.fromTranslationKey("key.keyboard." + keyName.toLowerCase());
        if (key == InputUtil.UNKNOWN_KEY) return false;
        macros.put(keyName.toLowerCase(), message);
        save();
        return true;
    }

    public boolean remove(String keyName) {
        boolean removed = macros.remove(keyName.toLowerCase()) != null;
        save();
        return removed;
    }

    private void save() {
        try {
            Files.writeString(FILE, new Gson().toJson(macros));
        } catch (Exception e) {
            LootClient.LOGGER.error("Failed to save macros", e);
        }
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null || mc.getNetworkHandler() == null) return;
        for (var e : macros.entrySet()) {
            int code = InputUtil.fromTranslationKey("key.keyboard." + e.getKey()).getCode();
            boolean pressed = InputUtil.isKeyPressed(mc.getWindow(), code);
            if (pressed && down.add(code)) send(e.getValue());
            else if (!pressed) down.remove(code);
        }
    }

    private void send(String msg) {
        if (msg.startsWith("/")) mc.getNetworkHandler().sendChatCommand(msg.substring(1));
        else mc.getNetworkHandler().sendChatMessage(msg);
    }
}
