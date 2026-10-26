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

    private static final Path PROFILES = FabricLoader.getInstance().getConfigDir().resolve("lootclient-configs");

    public static void save() {
        write(FILE, snapshot());
    }

    private static void write(Path file, JsonObject root) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            LootClient.LOGGER.error("Failed to save config", e);
        }
    }

    private static JsonObject snapshot() {
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
        return root;
    }

    /** Loads settings and keybinds. Enabled states are applied separately once a world is joined. */
    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            apply(JsonParser.parseString(Files.readString(FILE)).getAsJsonObject());
        } catch (Exception e) {
            LootClient.LOGGER.error("Failed to load config, using defaults", e);
        }
    }

    private static void apply(JsonObject root) {
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
            if (obj.has("enabled") && !(m instanceof dev.loot.client.module.client.ClickGUI)) m.setEnabled(obj.get("enabled").getAsBoolean());
        }
    }

    // ---- named profiles, stored as config/lootclient-configs/<name>.json ----

    private static Path profile(String name) {
        String clean = name.trim().replaceAll("[^a-zA-Z0-9 _-]", "").trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("config name can only use letters, numbers, spaces, - and _");
        return PROFILES.resolve(clean + ".json");
    }

    public static void saveProfile(String name) {
        write(profile(name), snapshot());
    }

    public static boolean loadProfile(String name) {
        Path file = profile(name);
        if (!Files.exists(file)) return false;
        try {
            apply(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            save();
            return true;
        } catch (Exception e) {
            LootClient.LOGGER.error("Failed to load config {}", name, e);
            return false;
        }
    }

    public static boolean deleteProfile(String name) {
        try {
            return Files.deleteIfExists(profile(name));
        } catch (IOException e) {
            return false;
        }
    }

    public static java.util.List<String> profiles() {
        if (!Files.isDirectory(PROFILES)) return java.util.List.of();
        try (var stream = Files.list(PROFILES)) {
            return stream.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
                .map(n -> n.substring(0, n.length() - 5)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        } catch (IOException e) {
            return java.util.List.of();
        }
    }
}
