package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.util.ChatUtil;
import dev.loot.client.util.InvUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

/** Bind it to a key: swaps between your elytra and best chestplate. */
public class ElytraSwap extends Module {
    public ElytraSwap() {
        super("Elytra Swap", "Swaps your elytra and chestplate on keypress.", Category.COMBAT);
    }

    private static boolean isChestplate(ItemStack s) {
        return Registries.ITEM.getId(s.getItem()).getPath().endsWith("_chestplate");
    }

    @Override
    public void onEnable() {
        if (inGame()) swap();
        setEnabled(false);
    }

    private void swap() {
        boolean wearingElytra = mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
        int src = wearingElytra
            ? InvUtil.findAny(s -> isChestplate(s) && s.isOf(Items.NETHERITE_CHESTPLATE))
            : InvUtil.findAny(s -> s.isOf(Items.ELYTRA));
        if (src == -1 && wearingElytra) src = InvUtil.findAny(ElytraSwap::isChestplate);
        if (src == -1) {
            ChatUtil.error(wearingElytra ? "No chestplate in your inventory." : "No elytra in your inventory.");
            return;
        }
        int sync = mc.player.playerScreenHandler.syncId;
        int from = InvUtil.toHandlerSlot(src);
        // pick up, drop onto chest slot (6) which swaps, then put the old piece back
        mc.interactionManager.clickSlot(sync, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, 6, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, from, 0, SlotActionType.PICKUP, mc.player);
    }
}
