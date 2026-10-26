package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.util.ChatUtil;
import dev.loot.client.util.InvUtil;
import net.minecraft.item.Items;

/** Bind it to a key: each press throws one pearl from your hotbar and switches back. */
public class KeyPearl extends Module {
    public KeyPearl() {
        super("Key Pearl", "Throws an ender pearl on keypress without switching slots.", Category.MISC);
    }

    @Override
    public void onEnable() {
        if (inGame() && !mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack())
            && !InvUtil.useHotbarItem(Items.ENDER_PEARL, true)) {
            ChatUtil.error("No ender pearl in your hotbar.");
        }
        setEnabled(false);
    }
}
