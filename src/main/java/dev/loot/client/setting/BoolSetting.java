package dev.loot.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public void toggle() {
        value = !value;
    }

    @Override
    public boolean parse(String input) {
        switch (input.toLowerCase()) {
            case "true", "on", "yes", "1" -> value = true;
            case "false", "off", "no", "0" -> value = false;
            case "toggle" -> toggle();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public String display() {
        return value ? "on" : "off";
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive()) value = json.getAsBoolean();
    }
}
