package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.InvUtil;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

/** Pulls healing splash potions from your inventory into empty hotbar slots. */
public class PotionRefill extends Module {
    private final BoolSetting needsInventory = add(new BoolSetting("InvOpen", "Only refill while your inventory is open.", false));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between moves.", 2, 0, 20, 1));

    private int timer;

    public PotionRefill() {
        super("Potion Refill", "Refills your hotbar with healing potions.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (timer-- > 0) return;
        if (needsInventory.get() ? !(mc.currentScreen instanceof InventoryScreen) : mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        int empty = InvUtil.findHotbar(s -> s.isEmpty());
        if (empty == -1) return;
        int src = InvUtil.findInventory(PotionUtil::isHealingSplash);
        if (src == -1) return;
        InvUtil.swap(src, empty);
        timer = delay.getInt();
    }
}
