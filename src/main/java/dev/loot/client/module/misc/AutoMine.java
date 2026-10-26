package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;

public class AutoMine extends Module {
    public AutoMine() {
        super("Auto Mine", "Holds left click for you.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (mc.currentScreen == null) mc.options.attackKey.setPressed(true);
    }

    @Override
    public void onDisable() {
        mc.options.attackKey.setPressed(false);
    }
}
