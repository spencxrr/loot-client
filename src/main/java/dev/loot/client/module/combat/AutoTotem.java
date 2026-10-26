package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks to wait between swaps.", 0, 0, 20, 1));

    private int cooldown;

    public AutoTotem() {
        super("Auto Totem", "Keeps a totem of undying in your offhand.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        // Only act when the player's own inventory is the open handler, otherwise slot ids don't line up.
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (!inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) continue;
            int handlerSlot = i < 9 ? 36 + i : i;
            // Button 40 with SWAP moves the clicked stack into the offhand.
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, handlerSlot, 40, SlotActionType.SWAP, mc.player);
            cooldown = delay.getInt();
            return;
        }
    }

    @Override
    public String getInfo() {
        if (!inGame()) return null;
        return String.valueOf(mc.player.getInventory().count(Items.TOTEM_OF_UNDYING));
    }
}
