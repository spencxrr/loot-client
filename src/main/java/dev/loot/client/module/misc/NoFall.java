package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Marks every movement packet as "on ground" while you're falling, so the server never builds
 * up fall distance. The old version sent an extra ground packet once per tick, which still let a
 * fast fall (3+ blocks per tick near terminal velocity) deal damage between packets.
 */
public class NoFall extends Module {
    private final NumberSetting minFall = add(new NumberSetting("MinFall", "Fall distance before it kicks in.", 2, 0, 3, 0.5));

    public NoFall() {
        super("No Fall", "Prevents fall damage.", Category.MISC);
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (!(packet instanceof PlayerMoveC2SPacket p) || p.isOnGround() || mc.player == null || mc.getNetworkHandler() == null) return false;
        if (mc.player.getAbilities().creativeMode || mc.player.isGliding() || mc.player.fallDistance < minFall.get()) return false;
        boolean hc = p.horizontalCollision();
        PlayerMoveC2SPacket replacement;
        if (p.changesPosition() && p.changesLook()) {
            replacement = new PlayerMoveC2SPacket.Full(p.getX(0), p.getY(0), p.getZ(0), p.getYaw(0), p.getPitch(0), true, hc);
        } else if (p.changesPosition()) {
            replacement = new PlayerMoveC2SPacket.PositionAndOnGround(p.getX(0), p.getY(0), p.getZ(0), true, hc);
        } else if (p.changesLook()) {
            replacement = new PlayerMoveC2SPacket.LookAndOnGround(p.getYaw(0), p.getPitch(0), true, hc);
        } else {
            replacement = new PlayerMoveC2SPacket.OnGroundOnly(true, hc);
        }
        mc.getNetworkHandler().sendPacket(replacement);
        return true;
    }
}
