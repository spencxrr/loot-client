package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/** Shift-clicks everything out of chests, barrels and shulkers you open. */
public class AutoLoot extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between item moves (0 = instant).", 1, 0, 10, 1));
    private final BoolSetting shulkers = add(new BoolSetting("Shulkers", "Also empty shulker boxes.", true));
    private final BoolSetting autoClose = add(new BoolSetting("AutoClose", "Close the container when it's empty.", true));

    private int timer;

    public AutoLoot() {
        super("Auto Loot", "Empties containers into your inventory when opened.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        if (!(screen instanceof GenericContainerScreen || (shulkers.get() && screen instanceof ShulkerBoxScreen))) return;
        if (timer-- > 0) return;
        ScreenHandler handler = screen.getScreenHandler();
        int moves = delay.getInt() == 0 ? Integer.MAX_VALUE : 1;
        boolean any = false;
        for (Slot slot : handler.slots) {
            if (slot.inventory instanceof PlayerInventory || !slot.hasStack()) continue;
            any = true;
            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
            if (--moves <= 0) break;
        }
        timer = delay.getInt();
        if (!any && autoClose.get()) mc.player.closeHandledScreen();
    }
}
