package dev.loot.client.module.basefinding;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/** Fast block lookups in chunk-local coordinates that fall back to the world across the border. */
final class ChunkView {
    private final WorldChunk chunk;
    private final int bx, bz;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    ChunkView(WorldChunk chunk) {
        this.chunk = chunk;
        this.bx = chunk.getPos().getStartX();
        this.bz = chunk.getPos().getStartZ();
    }

    BlockState get(int lx, int y, int lz) {
        pos.set(bx + lx, y, bz + lz);
        if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) return chunk.getBlockState(pos);
        var world = MinecraftClient.getInstance().world;
        return world == null ? Blocks.AIR.getDefaultState() : world.getBlockState(pos);
    }

    boolean air(int lx, int y, int lz) {
        return get(lx, y, lz).isAir();
    }

    /** Full, non-air block that a player would have to mine through. */
    boolean solid(int lx, int y, int lz) {
        BlockState s = get(lx, y, lz);
        return !s.isAir() && s.isOpaque() && s.getFluidState().isEmpty();
    }

    BlockPos world(int lx, int y, int lz) {
        return new BlockPos(bx + lx, y, bz + lz);
    }

    int bottomY() {
        return chunk.getBottomY();
    }

    int topY() {
        return chunk.getBottomY() + chunk.getHeight();
    }
}
