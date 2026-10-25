package dev.loot.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.math.MathHelper;

public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    public float getFloat() {
        return value.floatValue();
    }

    @Override
    public void set(Double v) {
        double snapped = step > 0 ? Math.round(v / step) * step : v;
        value = MathHelper.clamp(snapped, min, max);
    }

    @Override
    public boolean parse(String input) {
        try {
            set(Double.parseDouble(input));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String display() {
        return step >= 1 ? String.valueOf(getInt()) : String.format("%.2f", value);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive()) set(json.getAsDouble());
    }
}
