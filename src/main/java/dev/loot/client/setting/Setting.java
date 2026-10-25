package dev.loot.client.setting;

import com.google.gson.JsonElement;

public abstract class Setting<T> {
    private final String name;
    private final String description;
    protected T value;
    private final T defaultValue;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public void reset() {
        this.value = defaultValue;
    }

    /** Parses a value typed in chat. Returns false if the input was invalid. */
    public abstract boolean parse(String input);

    public abstract String display();

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement json);
}
