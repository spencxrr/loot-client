package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.basefinding.ChunkScanModule;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BlockListSetting;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds configured blocks in loaded chunks. Uses the shared background chunk scanner, skips
 * whole 16x16x16 sections via the palette when they can't contain a target block, and re-scans
 * a chunk as soon as the server says a block in it changed, so mined ores vanish right away.
 */
public class BlockESP extends ChunkScanModule<List<BlockESP.Found>> {
    public record Found(BlockPos pos, int color) {}

    private final BlockListSetting blocks = add(new BlockListSetting("Blocks", "Blocks to highlight. Edit with .set BlockESP Blocks add <id>.",
        Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS, Blocks.SPAWNER).onChange(this::rescanAll));
    private final NumberSetting range = add(new NumberSetting("Range", "Max render distance in blocks.", 256, 16, 1024, 16));
    private final NumberSetting limit = add(new NumberSetting("Limit", "Max boxes drawn per frame (closest first).", 2000, 100, 20000, 100));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity.", 60, 0, 255, 5));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", "Draw lines to found blocks.", false));

    private int total;

    public BlockESP() {
        super("Block ESP", "Highlights chosen blocks (ores, spawners, anything) through walls.", Category.RENDER);
    }

    @Override
    protected List<Found> scan(WorldChunk chunk) {
        Set<Block> targets = Set.copyOf(blocks.get());
        if (targets.isEmpty()) return null;
        List<Found> hits = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        int baseX = chunk.getPos().getStartX();
        int baseZ = chunk.getPos().getStartZ();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            if (!section.hasAny(state -> targets.contains(state.getBlock()))) continue;
            int baseY = chunk.sectionIndexToCoord(i) << 4;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        Block b = section.getBlockState(x, y, z).getBlock();
                        if (targets.contains(b)) hits.add(new Found(new BlockPos(baseX + x, baseY + y, baseZ + z), colorFor(b)));
                    }
        }
        return hits.isEmpty() ? null : hits;
    }

    @Override
    public void onTick() {
        super.onTick();
        int n = 0;
        for (List<Found> list : results.values()) n += list.size();
        total = n;
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        double maxSq = range.get() * range.get();
        int max = limit.getInt();
        BlockPos player = mc.player.getBlockPos();
        List<Found> visible = new ArrayList<>();
        for (List<Found> list : results.values())
            for (Found f : list)
                if (f.pos().getSquaredDistance(player) <= maxSq) visible.add(f);
        if (visible.size() > max) {
            visible.sort((a, b) -> Double.compare(a.pos().getSquaredDistance(player), b.pos().getSquaredDistance(player)));
            visible = visible.subList(0, max);
        }
        for (Found f : visible) {
            Box box = new Box(f.pos());
            r.box(box, f.color(), fill.getInt());
            if (tracers.get()) r.line(r.crosshair(), box.getCenter(), f.color());
        }
    }

    private static final Map<Block, Integer> COLORS = Map.of(
        Blocks.DIAMOND_ORE, 0xFF33EBFF,
        Blocks.DEEPSLATE_DIAMOND_ORE, 0xFF33EBFF,
        Blocks.ANCIENT_DEBRIS, 0xFFA0522D,
        Blocks.SPAWNER, 0xFFB000FF,
        Blocks.EMERALD_ORE, 0xFF2EE65C,
        Blocks.DEEPSLATE_EMERALD_ORE, 0xFF2EE65C,
        Blocks.GOLD_ORE, 0xFFFFD500,
        Blocks.DEEPSLATE_GOLD_ORE, 0xFFFFD500,
        Blocks.NETHER_GOLD_ORE, 0xFFFFD500
    );

    private static int colorFor(Block block) {
        Integer fixed = COLORS.get(block);
        if (fixed != null) return fixed;
        // Map color gives a sensible per-block tint without a config entry for every block.
        int rgb = block.getDefaultMapColor().color;
        if (rgb == 0) rgb = 0xFFFFFF;
        return 0xFF000000 | rgb;
    }

    @Override
    public String getInfo() {
        return String.valueOf(total);
    }
}
