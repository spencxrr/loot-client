package dev.loot.client.module.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;

final class PotionUtil {
    private PotionUtil() {}

    static boolean isHealingSplash(ItemStack s) {
        if (!s.isOf(Items.SPLASH_POTION)) return false;
        PotionContentsComponent c = s.get(DataComponentTypes.POTION_CONTENTS);
        return c != null && (c.matches(Potions.HEALING) || c.matches(Potions.STRONG_HEALING)
            || c.matches(Potions.REGENERATION) || c.matches(Potions.STRONG_REGENERATION) || c.matches(Potions.LONG_REGENERATION));
    }
}
