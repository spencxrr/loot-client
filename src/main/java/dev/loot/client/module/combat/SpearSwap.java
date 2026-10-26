package dev.loot.client.module.combat;

import dev.loot.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/** Spears (1.21.11) reach further; switch to one when the target is past sword range. */
public class SpearSwap extends WeaponSwap {
    private final NumberSetting minDistance = add(new NumberSetting("MinDistance", "Only swap when the target is at least this far.", 3, 0, 6, 0.25));

    public SpearSwap() {
        super("Spear Swap", "Switches to a spear for long-range hits.");
    }

    @Override
    protected boolean shouldSwap(Entity target) {
        return mc.player.distanceTo(target) >= minDistance.get();
    }

    @Override
    protected boolean isWeapon(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_spear");
    }
}
