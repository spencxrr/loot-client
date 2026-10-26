package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

public class Step extends Module {
    private static final double VANILLA = 0.6;

    private final NumberSetting height = add(new NumberSetting("Height", "Blocks you can step up.", 1, 0.6, 4, 0.5));

    public Step() {
        super("Step", "Walk up full blocks without jumping.", Category.MISC);
    }

    @Override
    public void onTick() {
        EntityAttributeInstance attr = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (attr != null && attr.getBaseValue() != height.get()) attr.setBaseValue(height.get());
    }

    @Override
    public void onDisable() {
        if (!inGame()) return;
        EntityAttributeInstance attr = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (attr != null) attr.setBaseValue(VANILLA);
    }
}
