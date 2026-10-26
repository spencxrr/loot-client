package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import org.joml.Vector4f;

/** Push fog back (or remove it) and optionally tint it with the client's lavender. */
public class Fog extends Module {
    private final BoolSetting remove = add(new BoolSetting("Remove", "Get rid of fog completely, including lava/water/blindness.", false));
    private final NumberSetting multiplier = add(new NumberSetting("Distance", "Fog distance multiplier when not removed.", 2, 0.25, 10, 0.25));
    private final BoolSetting tint = add(new BoolSetting("Tint", "Blend the fog color toward lavender.", true));
    private final NumberSetting tintAmount = add(new NumberSetting("TintAmount", "How strong the tint is.", 35, 0, 100, 5));

    public Fog() {
        super("Fog", "Change fog distance and color.", Category.RENDER);
    }

    public float distance(float vanilla) {
        if (remove.get()) return 1_000_000f;
        return vanilla * multiplier.getFloat();
    }

    public Vector4f color(Vector4f vanilla) {
        if (!tint.get()) return vanilla;
        float t = tintAmount.getFloat() / 100f;
        return new Vector4f(
            vanilla.x + (0xA3 / 255f - vanilla.x) * t,
            vanilla.y + (0xAE / 255f - vanilla.y) * t,
            vanilla.z + (0xFF / 255f - vanilla.z) * t,
            vanilla.w);
    }
}
