package dev.loot.client.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** A set of blocks, edited with ".set <module> <setting> add|remove <block>". */
public class BlockListSetting extends Setting<Set<Block>> {
    private Runnable onChange = () -> {};

    public BlockListSetting(String name, String description, Block... defaults) {
        super(name, description, new LinkedHashSet<>(Set.of(defaults)));
    }

    public BlockListSetting onChange(Runnable onChange) {
        this.onChange = onChange;
        return this;
    }

    public boolean contains(Block block) {
        return value.contains(block);
    }

    @Override
    public boolean parse(String input) {
        String[] parts = input.trim().split("\\s+");
        if (parts.length == 1 && parts[0].equalsIgnoreCase("clear")) {
            value = new LinkedHashSet<>();
            onChange.run();
            return true;
        }
        if (parts.length != 2) return false;
        Identifier id = Identifier.tryParse(parts[1].contains(":") ? parts[1] : "minecraft:" + parts[1]);
        if (id == null || !Registries.BLOCK.containsId(id)) return false;
        Block block = Registries.BLOCK.get(id);
        Set<Block> copy = new LinkedHashSet<>(value);
        switch (parts[0].toLowerCase()) {
            case "add" -> copy.add(block);
            case "remove", "del" -> copy.remove(block);
            default -> {
                return false;
            }
        }
        value = copy;
        onChange.run();
        return true;
    }

    @Override
    public String display() {
        if (value.isEmpty()) return "(none)";
        return value.stream().map(b -> Registries.BLOCK.getId(b).getPath()).collect(Collectors.joining(", "));
    }

    @Override
    public JsonElement toJson() {
        JsonArray arr = new JsonArray();
        for (Block b : value) arr.add(Registries.BLOCK.getId(b).toString());
        return arr;
    }

    @Override
    public void fromJson(JsonElement json) {
        if (!json.isJsonArray()) return;
        Set<Block> set = new LinkedHashSet<>();
        for (JsonElement e : json.getAsJsonArray()) {
            Identifier id = Identifier.tryParse(e.getAsString());
            if (id != null && Registries.BLOCK.containsId(id)) set.add(Registries.BLOCK.get(id));
        }
        value = set;
        onChange.run();
    }
}
