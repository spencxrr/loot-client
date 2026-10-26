package dev.loot.client.module.basefinding;

import dev.loot.client.module.Category;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.block.AbstractPlantStemBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Estimates how long a chunk has been loaded by players from plant growth. Sugar cane and cactus
 * generate at age 0 and tick up roughly once a minute of loaded time; kelp only hits max age by
 * growing. The highest reading in the chunk becomes its "minutes loaded" estimate (a lower bound).
 */
public class SusChunkV2 extends ChunkScanModule<Integer> {
    private final NumberSetting minMinutes = add(new NumberSetting("MinMinutes", "Estimated loaded minutes to flag a chunk.", 10, 1, 120, 1));
    private final BoolSetting alert = add(new BoolSetting("Alert", "Chat + sound for new hits.", true));
    private final NumberSetting renderY = add(new NumberSetting("RenderY", "Height to draw chunk markers at.", 64, -64, 320, 1));

    // A block gets a random tick about every 68 seconds at the default randomTickSpeed of 3.
    private static final double MINUTES_PER_TICK = 4096.0 / 3 / 20 / 60;

    public SusChunkV2() {
        super("Sus Chunk V2", "Estimates how long players kept each chunk loaded, from plant growth.", Category.BASEFINDING);
    }

    private static boolean interesting(BlockState s) {
        return s.isOf(Blocks.SUGAR_CANE) || s.isOf(Blocks.CACTUS) || s.isOf(Blocks.KELP);
    }

    @Override
    protected Integer scan(WorldChunk chunk) {
        int bestTicks = 0;
        int grownKelp = 0;
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty() || !section.hasAny(SusChunkV2::interesting)) continue;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        BlockState s = section.getBlockState(x, y, z);
                        if (s.isOf(Blocks.SUGAR_CANE) || s.isOf(Blocks.CACTUS)) {
                            bestTicks = Math.max(bestTicks, s.get(Properties.AGE_15));
                        } else if (s.isOf(Blocks.KELP) && s.get(AbstractPlantStemBlock.AGE) >= 25) {
                            grownKelp++;
                        }
                    }
        }
        // Each kelp growth step needs ~7 random ticks on average and a max-age tip needs at least 2 steps.
        if (grownKelp >= 3) bestTicks = Math.max(bestTicks, 14);
        int minutes = (int) Math.round(bestTicks * MINUTES_PER_TICK);
        return minutes >= minMinutes.getInt() ? minutes : null;
    }

    @Override
    protected void onFound(ChunkPos pos, Integer minutes) {
        if (!alert.get()) return;
        ChatUtil.coords("Chunk loaded ~" + minutes + "+ min at", pos.getCenterX(), renderY.getInt(), pos.getCenterZ());
        ChatUtil.ping();
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        for (var e : results.entrySet()) {
            float t = Math.min(1f, e.getValue() / 30f);
            int color = dev.loot.client.gui.Theme.lerp(0xFFA3AEFF, 0xFFFF6B9A, t);
            ChunkHighlight.draw(r, new ChunkPos(e.getKey()), renderY.get(), color, 40);
        }
    }
}
