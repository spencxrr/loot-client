package dev.loot.client.module.movement;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint", "Always sprint when moving.", Category.MOVEMENT);
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
