package dev.loot.client.module.misc;

import dev.loot.client.mixin.MinecraftClientAccessor;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;

public class AutoClicker extends Module {
    private final BoolSetting left = add(new BoolSetting("Left", "Auto left click while holding attack.", true));
    private final BoolSetting right = add(new BoolSetting("Right", "Auto right click while holding use.", false));
    private final BoolSetting always = add(new BoolSetting("NoHold", "Click even when you aren't holding the button.", false));
    private final NumberSetting cps = add(new NumberSetting("CPS", "Clicks per second.", 12, 1, 20, 1));
    private final NumberSetting jitter = add(new NumberSetting("Randomize", "Random CPS spread, +/-.", 2, 0, 6, 1));

    private double leftAcc, rightAcc;

    public AutoClicker() {
        super("Auto Clicker", "Clicks for you at a set CPS.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null) return;
        var acc = (MinecraftClientAccessor) mc;
        double perTick = (cps.get() + ThreadLocalRandom.current().nextDouble(-jitter.get(), jitter.get() + 1e-9)) / 20.0;
        if (left.get() && (always.get() || mc.options.attackKey.isPressed())) {
            leftAcc += perTick;
            while (leftAcc >= 1) {
                leftAcc--;
                acc.loot$setAttackCooldown(0);
                acc.loot$doAttack();
            }
        }
        if (right.get() && (always.get() || mc.options.useKey.isPressed())) {
            rightAcc += perTick;
            while (rightAcc >= 1) {
                rightAcc--;
                acc.loot$doItemUse();
            }
        }
    }

    @Override
    public String getInfo() {
        return cps.display();
    }
}
