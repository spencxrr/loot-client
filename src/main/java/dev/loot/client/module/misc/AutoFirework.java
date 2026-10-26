package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.InvUtil;
import net.minecraft.item.Items;

public class AutoFirework extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Seconds between rockets while gliding.", 3, 0.5, 15, 0.5));
    private final NumberSetting minSpeed = add(new NumberSetting("MinSpeed", "Only boost below this speed (blocks/tick, 0 = always).", 1.2, 0, 3, 0.1));

    private int timer;

    public AutoFirework() {
        super("Auto Firework", "Uses rockets automatically while flying an elytra.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (!mc.player.isGliding()) {
            timer = 0;
            return;
        }
        if (timer-- > 0) return;
        if (minSpeed.get() > 0 && mc.player.getVelocity().length() > minSpeed.get()) return;
        if (InvUtil.useHotbarItem(Items.FIREWORK_ROCKET, true)) timer = (int) (delay.get() * 20);
    }
}
