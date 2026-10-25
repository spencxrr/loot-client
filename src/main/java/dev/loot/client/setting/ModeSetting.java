package dev.loot.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        super(name, description, defaultValue);
        this.modes = List.of(modes);
    }

    public List<String> getModes() {
        return modes;
    }

    public boolean is(String mode) {
        return value.equalsIgnoreCase(mode);
    }

    public void cycle(boolean forward) {
        int i = modes.indexOf(value);
        int n = modes.size();
        value = modes.get(((i + (forward ? 1 : -1)) % n + n) % n);
    }

    @Override
    public boolean parse(String input) {
        for (String mode : modes) {
            if (mode.equalsIgnoreCase(input)) {
                value = mode;
                return true;
            }
        }
        return false;
    }

    @Override
    public String display() {
        return value;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive()) parse(json.getAsString());
    }
}
