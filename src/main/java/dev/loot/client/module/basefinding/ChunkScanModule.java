package dev.loot.client.module.basefinding;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.util.WorldUtil;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared plumbing for modules that analyse whole chunks: queues loaded chunks, scans them on
 * the main thread under a small per-tick time budget, re-checks the chunks near the player
 * every couple of seconds, and drops results when chunks unload.
 *
 * @param <T> whatever a subclass keeps per chunk; return null from {@link #scan} for "nothing here".
 */
public abstract class ChunkScanModule<T> extends Module {
    protected final Map<Long, T> results = new HashMap<>();
    private final Deque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private int refreshTimer;

    protected ChunkScanModule(String name, String description, Category category) {
        super(name, description, category);
    }

    protected abstract T scan(WorldChunk chunk);

    /** Called once when a chunk first produces a result (not on refreshes). */
    protected void onFound(ChunkPos pos, T result) {}

    /** How many chunks around the player get re-scanned periodically, 0 to disable. */
    protected int refreshRadius() {
        return 2;
    }

    @Override
    public void onEnable() {
        rescanAll();
    }

    @Override
    public void onDisable() {
        results.clear();
        queue.clear();
        queued.clear();
    }

    protected void rescanAll() {
        results.clear();
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
        results.remove(chunk.getPos().toLong());
    }

    @Override
    public void onTick() {
        int r = refreshRadius();
        if (r > 0 && refreshTimer-- <= 0) {
            refreshTimer = 40;
            ChunkPos center = mc.player.getChunkPos();
            for (int x = -r; x <= r; x++)
                for (int z = -r; z <= r; z++)
                    enqueue(new ChunkPos(center.x + x, center.z + z));
        }

        long deadline = System.nanoTime() + 1_500_000L;
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            long key = queue.poll();
            queued.remove(key);
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
            if (chunk == null) {
                results.remove(key);
                continue;
            }
            T result = scan(chunk);
            if (result == null) {
                results.remove(key);
            } else if (results.put(key, result) == null) {
                onFound(chunk.getPos(), result);
            }
        }
    }

    @Override
    public String getInfo() {
        return String.valueOf(results.size());
    }
}
