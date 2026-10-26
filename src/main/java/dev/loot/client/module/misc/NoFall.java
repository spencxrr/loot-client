package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/** Tells the server we're on the ground while falling, which resets fall distance server-side. */
public class NoFall extends Module {
    public NoFall() {
        super("No Fall", "Prevents fall damage.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (mc.player.getAbilities().creativeMode || mc.player.isGliding()) return;
        if (mc.player.fallDistance > 2.5) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
        }
    }
}
