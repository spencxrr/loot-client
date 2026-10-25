package dev.loot.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.loot.client.LootClient;
import dev.loot.client.module.Module;
import dev.loot.client.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lootclient.json");

    private Config() {}

    public static void save() {
        JsonObject root = new JsonObject();
        for (Module m : LootClient.modules().getModules()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", m.isEnabled());
            obj.addProperty("key", m.getKey());
            JsonObject settings = new JsonObject();
            for (Setting<?> s : m.getSettings()) settings.add(s.getName(), s.toJson());
            obj.add("settings", settings);
            root.add(m.getName(), obj);
        }
        try {
            Files.writeString(FILE, GSON.toJson(root));
        } catch (IOException e) {
            LootClient.LOGGER.error("Failed to save config", e);
        }
    }

    /** Loads settings and keybinds. Enabled states are applied separately once a world is joined. */
    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            for (Module m : LootClient.modules().getModules()) {
                if (!root.has(m.getName())) continue;
                JsonObject obj = root.getAsJsonObject(m.getName());
                if (obj.has("key")) m.setKey(obj.get("key").getAsInt());
                if (obj.has("settings")) {
                    JsonObject settings = obj.getAsJsonObject("settings");
                    for (Setting<?> s : m.getSettings()) {
                        JsonElement e = settings.get(s.getName());
                        if (e != null) s.fromJson(e);
                    }
                }
                if (obj.has("enabled")) m.setEnabled(obj.get("enabled").getAsBoolean());
            }
        } catch (Exception e) {
            LootClient.LOGGER.error("Failed to load config, using defaults", e);
        }
    }
}
