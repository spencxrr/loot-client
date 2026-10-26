package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.InvUtil;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;

/** While your inventory is open, refills the offhand and a chosen hotbar slot with totems. */
public class AutoInvTotem extends Module {
    private final BoolSetting offhand = add(new BoolSetting("Offhand", "Refill the offhand.", true));
    private final BoolSetting hotbar = add(new BoolSetting("Hotbar", "Refill a hotbar slot.", true));
    private final NumberSetting slot = add(new NumberSetting("Slot", "Hotbar slot to keep a totem in (1-9).", 9, 1, 9, 1));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between moves.", 1, 0, 10, 1));

    private int timer;

    public AutoInvTotem() {
        super("Auto Inv Totem", "Fills totem slots while your inventory is open.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (!(mc.currentScreen instanceof InventoryScreen)) return;
        if (timer-- > 0) return;
        int src = InvUtil.findInventory(s -> s.isOf(Items.TOTEM_OF_UNDYING));
        if (src == -1) return;
        if (offhand.get() && !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            InvUtil.swap(src, 40);
        } else if (hotbar.get() && !mc.player.getInventory().getStack(slot.getInt() - 1).isOf(Items.TOTEM_OF_UNDYING)) {
            InvUtil.swap(src, slot.getInt() - 1);
        } else {
            return;
        }
        timer = delay.getInt();
    }
}
