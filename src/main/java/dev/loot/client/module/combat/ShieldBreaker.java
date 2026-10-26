package dev.loot.client.module.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;

public class ShieldBreaker extends WeaponSwap {
    public ShieldBreaker() {
        super("Shield Breaker", "Switches to an axe when your target is blocking.");
    }

    @Override
    protected boolean shouldSwap(Entity target) {
        return target instanceof LivingEntity l && l.isBlocking();
    }

    @Override
    protected boolean isWeapon(ItemStack stack) {
        return stack.isIn(ItemTags.AXES);
    }
}
