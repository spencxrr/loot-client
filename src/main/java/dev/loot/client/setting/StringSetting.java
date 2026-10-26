package dev.loot.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class StringSetting extends Setting<String> {
    public StringSetting(String name, String description, String defaultValue) {
        super(name, description, defaultValue);
    }

    @Override
    public boolean parse(String input) {
        value = input;
        return true;
    }

    @Override
    public String display() {
        return value.isEmpty() ? "(empty)" : value;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive()) value = json.getAsString();
    }
}
