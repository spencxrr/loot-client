package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BlockListSetting;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.WorldUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds configured blocks in loaded chunks. Chunks are scanned on the main thread with a
 * per-tick time budget, and whole 16x16x16 sections are skipped via the palette when they
 * can't contain a target block, so even big block lists stay cheap.
 */
public class BlockESP extends Module {
    private final BlockListSetting blocks = add(new BlockListSetting("Blocks", "Blocks to highlight. Edit with .set BlockESP Blocks add <id>.",
        Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS, Blocks.SPAWNER).onChange(this::rescanAll));
    private final NumberSetting range = add(new NumberSetting("Range", "Max render distance in blocks.", 256, 16, 1024, 16));
    private final NumberSetting limit = add(new NumberSetting("Limit", "Max boxes drawn per frame.", 2000, 100, 20000, 100));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity.", 60, 0, 255, 5));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", "Draw lines to found blocks.", false));

    private final Map<Long, List<BlockPos>> found = new HashMap<>();
    private final Deque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private int refreshTimer;
    private int total;

    public BlockESP() {
        super("Block ESP", "Highlights chosen blocks (ores, spawners, anything) through walls.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        rescanAll();
    }

    @Override
    public void onDisable() {
        found.clear();
        queue.clear();
        queued.clear();
        total = 0;
    }

    private void rescanAll() {
        found.clear();
        queue.clear();
        queued.clear();
        if (!isEnabled() || !inGame()) return;
        for (WorldChunk chunk : WorldUtil.loadedChunks()) enqueue(chunk.getPos());
    }

    private void enqueue(ChunkPos pos) {
        long key = pos.toLong();
        if (queued.add(key)) queue.add(key);
    }

    @Override
    public void onChunkLoad(WorldChunk chunk) {
        if (isEnabled()) enqueue(chunk.getPos());
    }

    @Override
    public void onChunkUnload(WorldChunk chunk) {
        found.remove(chunk.getPos().toLong());
    }

    @Override
    public void onTick() {
        // Re-check nearby chunks every couple of seconds to pick up placed/broken blocks.
        if (refreshTimer-- <= 0) {
            refreshTimer = 40;
            ChunkPos center = mc.player.getChunkPos();
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++)
                    enqueue(new ChunkPos(center.x + x, center.z + z));
        }

        long deadline = System.nanoTime() + 2_000_000L; // 2ms per tick
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            long key = queue.poll();
            queued.remove(key);
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
            if (chunk == null) {
                found.remove(key);
                continue;
            }
            List<BlockPos> hits = scan(mc.world, chunk, blocks.get());
            if (hits.isEmpty()) found.remove(key);
            else found.put(key, hits);
        }
        total = found.values().stream().mapToInt(List::size).sum();
    }

    static List<BlockPos> scan(ClientWorld world, WorldChunk chunk, Set<Block> targets) {
        List<BlockPos> hits = new ArrayList<>();
        if (targets.isEmpty()) return hits;
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
                    for (int x = 0; x < 16; x++)
                        if (targets.contains(section.getBlockState(x, y, z).getBlock()))
                            hits.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
        }
        return hits;
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        double maxSq = range.get() * range.get();
        int max = limit.getInt();
        int drawn = 0;
        BlockPos player = mc.player.getBlockPos();
        outer:
        for (List<BlockPos> list : found.values()) {
            for (BlockPos pos : list) {
                if (pos.getSquaredDistance(player) > maxSq) continue;
                int color = colorFor(mc.world.getBlockState(pos).getBlock());
                Box box = new Box(pos);
                r.box(box, color, fill.getInt());
                if (tracers.get()) r.line(r.crosshair(), box.getCenter(), color);
                if (++drawn >= max) break outer;
            }
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
