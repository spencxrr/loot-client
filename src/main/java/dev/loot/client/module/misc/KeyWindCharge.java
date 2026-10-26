package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.util.ChatUtil;
import dev.loot.client.util.InvUtil;
import net.minecraft.item.Items;

/** Bind it to a key: throws a wind charge, optionally straight down for a quick launch. */
public class KeyWindCharge extends Module {
    private final BoolSetting down = add(new BoolSetting("AtFeet", "Aim straight down for a jump boost, then restore your view.", false));

    public KeyWindCharge() {
        super("Key Wind Charge", "Throws a wind charge on keypress without switching slots.", Category.MISC);
    }

    @Override
    public void onEnable() {
        if (inGame()) {
            float pitch = mc.player.getPitch();
            if (down.get()) mc.player.setPitch(90f);
            if (!InvUtil.useHotbarItem(Items.WIND_CHARGE, true)) ChatUtil.error("No wind charge in your hotbar.");
            if (down.get()) mc.player.setPitch(pitch);
        }
        setEnabled(false);
    }
}
