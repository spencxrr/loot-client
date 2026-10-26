package dev.loot.client.module.combat;

import dev.loot.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class MaceSwap extends WeaponSwap {
    private final NumberSetting minFall = add(new NumberSetting("MinFall", "Blocks fallen before the mace is worth it.", 1.5, 0, 10, 0.5));

    public MaceSwap() {
        super("Mace Swap", "Switches to your mace when you hit while falling.");
    }

    @Override
    protected boolean shouldSwap(Entity target) {
        return mc.player.fallDistance >= minFall.get();
    }

    @Override
    protected boolean isWeapon(ItemStack stack) {
        return stack.isOf(Items.MACE);
    }
}
