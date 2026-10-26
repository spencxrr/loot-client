package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;

public class AutoWalk extends Module {
    private final BoolSetting sprint = add(new BoolSetting("Sprint", "Sprint while walking.", true));

    public AutoWalk() {
        super("Auto Walk", "Holds W for you.", Category.MISC);
    }

    @Override
    public void onTick() {
        mc.options.forwardKey.setPressed(true);
        if (sprint.get()) mc.options.sprintKey.setPressed(true);
    }

    @Override
    public void onDisable() {
        mc.options.forwardKey.setPressed(false);
    }
}
