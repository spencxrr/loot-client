package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.InvUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class AutoEat extends Module {
    private final NumberSetting hunger = add(new NumberSetting("Hunger", "Eat at or below this many hunger points (20 = full).", 14, 1, 19, 1));
    private final NumberSetting health = add(new NumberSetting("Health", "Also eat when health is at or below this (0 = off).", 0, 0, 20, 1));
    private final BoolSetting gapples = add(new BoolSetting("Gapples", "Allow eating golden apples.", false));
    private final BoolSetting badFood = add(new BoolSetting("BadFood", "Allow rotten flesh, spider eyes, chorus fruit, etc.", false));

    private int prevSlot = -1;
    private boolean eating;

    public AutoEat() {
        super("Auto Eat", "Eats when you get hungry.", Category.MISC);
    }

    public boolean isEating() {
        return eating;
    }

    private boolean edible(ItemStack s) {
        if (!s.getComponents().contains(DataComponentTypes.FOOD)) return false;
        if (!gapples.get() && (s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE))) return false;
        if (!badFood.get() && (s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.SPIDER_EYE) || s.isOf(Items.POISONOUS_POTATO)
            || s.isOf(Items.PUFFERFISH) || s.isOf(Items.CHORUS_FRUIT) || s.isOf(Items.SUSPICIOUS_STEW))) return false;
        return true;
    }

    @Override
    public void onTick() {
        boolean need = mc.player.getHungerManager().getFoodLevel() <= hunger.getInt()
            || (health.getInt() > 0 && mc.player.getHealth() <= health.getInt() && mc.player.getHungerManager().getFoodLevel() < 20);
        if (eating) {
            if (!need || !edible(mc.player.getMainHandStack())) stop();
            else mc.options.useKey.setPressed(true);
            return;
        }
        if (!need || mc.currentScreen != null || mc.player.getAbilities().creativeMode) return;
        int slot = InvUtil.findHotbar(this::edible);
        if (slot == -1) return;
        prevSlot = InvUtil.selected();
        InvUtil.select(slot);
        mc.options.useKey.setPressed(true);
        eating = true;
    }

    private void stop() {
        mc.options.useKey.setPressed(false);
        if (prevSlot != -1) InvUtil.select(prevSlot);
        prevSlot = -1;
        eating = false;
    }

    @Override
    public void onDisable() {
        if (eating && inGame()) stop();
    }
}
