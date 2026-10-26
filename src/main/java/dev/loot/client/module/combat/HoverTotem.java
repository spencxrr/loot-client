package dev.loot.client.module.combat;

import dev.loot.client.mixin.HandledScreenAccessor;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.util.InvUtil;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;

/** Hover a totem in your inventory and it jumps into your offhand. */
public class HoverTotem extends Module {
    private final BoolSetting hotbarToo = add(new BoolSetting("Hotbar", "If the offhand is full, move it to an empty hotbar slot.", false));

    private int cooldown;

    public HoverTotem() {
        super("Hover Totem", "Moves a hovered totem to your offhand.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (cooldown-- > 0 || !(mc.currentScreen instanceof InventoryScreen screen)) return;
        Slot slot = ((HandledScreenAccessor) screen).loot$getFocusedSlot();
        if (slot == null || !(slot.inventory instanceof PlayerInventory) || !slot.getStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        int index = slot.getIndex();
        if (index == 40) return;
        if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            InvUtil.swap(index, 40);
            cooldown = 2;
        } else if (hotbarToo.get() && index >= 9) {
            int empty = InvUtil.findHotbar(s -> s.isEmpty());
            if (empty != -1) {
                InvUtil.swap(index, empty);
                cooldown = 2;
            }
        }
    }
}
