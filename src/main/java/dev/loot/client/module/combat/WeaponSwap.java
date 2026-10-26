package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.util.InvUtil;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

/**
 * Base for "switch to X right as you hit" modules. The attack hook runs before vanilla syncs
 * the selected slot, so the hit lands with the new item; we switch back a tick later.
 */
abstract class WeaponSwap extends Module {
    protected final BoolSetting swapBack = add(new BoolSetting("SwapBack", "Switch back to your previous slot after the hit.", true));

    private int restoreSlot = -1;
    private int restoreIn;

    protected WeaponSwap(String name, String description) {
        super(name, description, Category.COMBAT);
    }

    /** Should we swap for this hit? */
    protected abstract boolean shouldSwap(Entity target);

    protected abstract boolean isWeapon(ItemStack stack);

    @Override
    public boolean onAttack(Entity target) {
        if (isWeapon(mc.player.getMainHandStack()) || !shouldSwap(target)) return false;
        int slot = InvUtil.findHotbar(this::isWeapon);
        if (slot == -1) return false;
        if (restoreSlot == -1) restoreSlot = InvUtil.selected();
        InvUtil.select(slot);
        restoreIn = 2;
        return false;
    }

    @Override
    public void onTick() {
        if (restoreSlot != -1 && --restoreIn <= 0) {
            if (swapBack.get()) InvUtil.select(restoreSlot);
            restoreSlot = -1;
        }
    }
}
