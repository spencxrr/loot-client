package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;

/** Jumps the moment you get hit, which cancels part of the horizontal knockback. */
public class AutoJumpReset extends Module {
    private final NumberSetting chance = add(new NumberSetting("Chance", "Percent of hits to jump-reset.", 100, 0, 100, 5));

    private int lastHurt;

    public AutoJumpReset() {
        super("Auto Jump Reset", "Jumps when you take a hit to cut knockback.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        int hurt = mc.player.hurtTime;
        if (hurt > lastHurt && hurt == mc.player.maxHurtTime && mc.player.isOnGround() && mc.currentScreen == null
            && ThreadLocalRandom.current().nextInt(100) < chance.getInt()) {
            mc.player.jump();
        }
        lastHurt = hurt;
    }
}
