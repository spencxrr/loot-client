package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;

/**
 * Stops you from wasting glowstone (and time) by over-charging an anchor that's already
 * charged: one charge does the same damage as four.
 */
public class AnchorOptimizer extends Module {
    public AnchorOptimizer() {
        super("Anchor Optimizer", "Blocks extra glowstone clicks on charged anchors.", Category.COMBAT);
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (!(packet instanceof PlayerInteractBlockC2SPacket p) || mc.player == null) return false;
        var stack = mc.player.getStackInHand(p.getHand());
        if (!stack.isOf(Items.GLOWSTONE)) return false;
        var pos = p.getBlockHitResult().getBlockPos();
        return AnchorUtil.isAnchor(pos) && AnchorUtil.charges(pos) >= 1;
    }
}
