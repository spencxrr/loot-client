package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint", "Always sprint when moving.", Category.MISC);
    }

    @Override
    public void onTick() {
        mc.options.sprintKey.setPressed(true);
    }

    @Override
    public void onDisable() {
        mc.options.sprintKey.setPressed(false);
    }
}
