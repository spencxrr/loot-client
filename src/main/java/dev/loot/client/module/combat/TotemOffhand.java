package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.InvUtil;
import net.minecraft.item.Items;

/**
 * Lets you keep something else (crystals, a shield, gapples) in your offhand and only
 * swaps a totem in once you drop low. Pair with Auto Totem off.
 */
public class TotemOffhand extends Module {
    private final NumberSetting health = add(new NumberSetting("Health", "Swap a totem in at or below this health.", 10, 1, 20, 1));

    private int cooldown;

    public TotemOffhand() {
        super("Totem Offhand", "Swaps a totem into your offhand when health is low.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (cooldown-- > 0 || mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        if (mc.player.getHealth() + mc.player.getAbsorptionAmount() > health.get()) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) return;
        int slot = InvUtil.findAny(s -> s.isOf(Items.TOTEM_OF_UNDYING));
        if (slot == -1) return;
        InvUtil.swap(slot, 40);
        cooldown = 3;
    }
}
