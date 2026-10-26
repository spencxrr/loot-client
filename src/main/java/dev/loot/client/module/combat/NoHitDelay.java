package dev.loot.client.module.combat;

import dev.loot.client.mixin.MinecraftClientAccessor;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;

/** Removes the 10-tick lockout vanilla applies after you swing at nothing. */
public class NoHitDelay extends Module {
    public NoHitDelay() {
        super("No Hit Delay", "Lets you swing again instantly after missing.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        ((MinecraftClientAccessor) mc).loot$setAttackCooldown(0);
    }
}
