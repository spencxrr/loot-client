package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;

/** Stretched-res look: renders the world at a different aspect ratio than your window. */
public class AspectRatio extends Module {
    private final NumberSetting ratio = add(new NumberSetting("Ratio", "Width / height. 1.33 is 4:3, 1.78 is 16:9.", 1.33, 0.5, 3, 0.01));

    public AspectRatio() {
        super("Aspect Ratio", "Stretch the world view to another aspect ratio.", Category.RENDER);
    }

    public float ratio() {
        return ratio.getFloat();
    }
}
