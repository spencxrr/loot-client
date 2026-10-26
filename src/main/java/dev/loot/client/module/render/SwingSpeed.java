package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;

/** Changes how long your hand swing animation takes. Visual only, attack speed is unaffected. */
public class SwingSpeed extends Module {
    private final NumberSetting length = add(new NumberSetting("Length", "Swing length in percent of vanilla. Higher is slower.", 150, 25, 400, 5));

    public SwingSpeed() {
        super("SwingSpeed", "Slows down or speeds up your swing animation.", Category.RENDER);
    }

    public int duration(int vanilla) {
        return Math.max(1, Math.round(vanilla * length.getFloat() / 100f));
    }
}
