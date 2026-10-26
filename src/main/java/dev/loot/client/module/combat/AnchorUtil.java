package dev.loot.client.module.combat;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import dev.loot.client.util.InvUtil;

final class AnchorUtil {
    private AnchorUtil() {}

    static boolean isAnchor(BlockPos pos) {
        return MinecraftClient.getInstance().world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR);
    }

    static int charges(BlockPos pos) {
        BlockState s = MinecraftClient.getInstance().world.getBlockState(pos);
        return s.isOf(Blocks.RESPAWN_ANCHOR) ? s.get(RespawnAnchorBlock.CHARGES) : 0;
    }

    static void click(BlockPos pos, Direction side) {
        var mc = MinecraftClient.getInstance();
        Vec3d hit = Vec3d.ofCenter(pos).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, side, pos, false));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** A hotbar slot that won't charge or place anything when right-clicking an anchor (prefers totem). */
    static int explodeSlot() {
        int totem = InvUtil.findHotbar(Items.TOTEM_OF_UNDYING);
        if (totem != -1) return totem;
        return InvUtil.findHotbar(s -> !s.isOf(Items.GLOWSTONE) && !s.isOf(Items.RESPAWN_ANCHOR)
            && !(s.getItem() instanceof net.minecraft.item.BlockItem));
    }
}
