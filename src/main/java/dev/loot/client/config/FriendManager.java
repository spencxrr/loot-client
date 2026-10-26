package dev.loot.client.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.loot.client.LootClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Names that combat modules and ESP colors treat as friends. */
public class FriendManager {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lootclient-friends.json");
    private final Set<String> names = new TreeSet<>();

    public FriendManager() {
        try {
            if (Files.exists(FILE)) {
                Set<String> loaded = new Gson().fromJson(Files.readString(FILE), new TypeToken<Set<String>>() {}.getType());
                if (loaded != null) loaded.forEach(n -> names.add(n.toLowerCase(Locale.ROOT)));
            }
        } catch (Exception e) {
            LootClient.LOGGER.error("Failed to load friends", e);
        }
    }

    public boolean isFriend(String name) {
        return name != null && names.contains(name.toLowerCase(Locale.ROOT));
    }

    public boolean add(String name) {
        boolean added = names.add(name.toLowerCase(Locale.ROOT));
        save();
        return added;
    }

    public boolean remove(String name) {
        boolean removed = names.remove(name.toLowerCase(Locale.ROOT));
        save();
        return removed;
    }

    public Set<String> all() {
        return names;
    }

    private void save() {
        try {
            Files.writeString(FILE, new Gson().toJson(names));
        } catch (Exception e) {
            LootClient.LOGGER.error("Failed to save friends", e);
        }
    }
}
