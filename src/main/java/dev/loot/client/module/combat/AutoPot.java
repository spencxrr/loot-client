package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.InvUtil;
import net.minecraft.util.Hand;

/** Throws healing/regen splash potions at your feet when health is low. */
public class AutoPot extends Module {
    private final NumberSetting health = add(new NumberSetting("Health", "Throw at or below this health.", 10, 1, 20, 1));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between throws.", 10, 2, 40, 1));

    private int timer;

    public AutoPot() {
        super("Auto Pot", "Splashes healing potions when you're low.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (timer-- > 0 || mc.currentScreen != null) return;
        if (mc.player.getHealth() > health.get()) return;
        int slot = InvUtil.findHotbar(PotionUtil::isHealingSplash);
        if (slot == -1) return;
        int prev = InvUtil.selected();
        float pitch = mc.player.getPitch();
        InvUtil.select(slot);
        mc.player.setPitch(90f);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.setPitch(pitch);
        InvUtil.select(prev);
        timer = delay.getInt();
    }
}
