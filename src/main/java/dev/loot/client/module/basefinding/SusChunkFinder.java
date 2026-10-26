package dev.loot.client.module.basefinding;

import dev.loot.client.module.Category;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.block.AbstractPlantStemBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Kelp generates with an age of at most 24 and only reaches 25 by growing, which only happens
 * while a player keeps the chunk loaded. Lots of max-age kelp tips means someone has spent real
 * time nearby, often because they live there.
 */
public class SusChunkFinder extends ChunkScanModule<Integer> {
    private final NumberSetting threshold = add(new NumberSetting("Threshold", "Grown kelp tips a chunk needs.", 6, 1, 64, 1));
    private final BoolSetting alert = add(new BoolSetting("Alert", "Chat + sound for new hits.", true));
    private final NumberSetting renderY = add(new NumberSetting("RenderY", "Height to draw chunk markers at.", 62, -64, 320, 1));

    public SusChunkFinder() {
        super("Sus Chunk Finder", "Flags ocean chunks where kelp has grown, a sign of long player presence.", Category.BASEFINDING);
    }

    @Override
    protected Integer scan(WorldChunk chunk) {
        int count = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        for (ChunkSection section : sections) {
            if (section == null || section.isEmpty() || !section.hasAny(s -> s.isOf(Blocks.KELP))) continue;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        BlockState s = section.getBlockState(x, y, z);
                        if (s.isOf(Blocks.KELP) && s.get(AbstractPlantStemBlock.AGE) >= 25) count++;
                    }
        }
        return count >= threshold.getInt() ? count : null;
    }

    @Override
    protected void onFound(ChunkPos pos, Integer count) {
        if (!alert.get()) return;
        ChatUtil.coords("Sus chunk (" + count + " grown kelp) at", pos.getCenterX(), renderY.getInt(), pos.getCenterZ());
        ChatUtil.ping();
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        for (var e : results.entrySet()) ChunkHighlight.draw(r, new ChunkPos(e.getKey()), renderY.get(), 0xFFFF6B9A, 40);
    }
}
