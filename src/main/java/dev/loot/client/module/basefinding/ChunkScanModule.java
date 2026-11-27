package dev.loot.client.module.basefinding;

import dev.loot.client.LootClient;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.Setting;
import dev.loot.client.util.WorldUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared plumbing for modules that analyse whole chunks.
 *
 * Chunks are scanned on a background thread (reading block data off-thread is safe enough for a
 * client: the worst case is a torn read, which we catch and retry), nearest chunks first, so a
 * full render distance gets through in a second or two without touching the frame rate. Chunks
 * are re-scanned when the server sends block changes for them, so placed/broken blocks show up
 * almost immediately instead of waiting for a periodic refresh.
 *
 * @param <T> whatever a subclass keeps per chunk; return null from {@link #scan} for "nothing here".
 */
public abstract class ChunkScanModule<T> extends Module {
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(
        Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 2)), r -> {
            Thread t = new Thread(r, "Loot chunk scanner");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    private static final int MAX_IN_FLIGHT = 6;

    private record Done<T>(long key, int generation, T result, boolean failed) {}

    protected final Map<Long, T> results = new HashMap<>();
    private final Set<Long> queue = new LinkedHashSet<>();
    private final Set<Long> inFlight = new LinkedHashSet<>();
    private final ConcurrentLinkedQueue<Done<T>> done = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> dirty = new ConcurrentLinkedQueue<>();
    private final Map<Long, Integer> dirtyAt = new HashMap<>();
    private final Map<Long, Integer> retries = new HashMap<>();
    private final AtomicInteger generation = new AtomicInteger();
    private int ticks;
    private int refreshTimer;
    private Object world;
    private List<Object> watched;
    private int watchedSettleAt = -1;

    protected ChunkScanModule(String name, String description, Category category) {
        super(name, description, category);
    }

    /** Runs on a worker thread. Only read the chunk and settings here, never entities or the HUD. */
    protected abstract T scan(WorldChunk chunk);

    /**
     * Runs on the main thread with a fresh scan result, before it is stored. Lets subclasses add
     * things that aren't thread safe to read (entities) or veto the result. Return null to drop it.
     */
    protected T postScan(WorldChunk chunk, T result) {
        return result;
    }

    /** Called once when a chunk first produces a result (not on refreshes). */
    protected void onFound(ChunkPos pos, T result) {}

    /** Called on the main thread after every finished scan: the stored value went from old to now (either may be null). */
    protected void onStored(long key, T old, T now) {}

    /** Called when you change world or dimension, before anything from the new world is scanned. */
    protected void onWorldChange() {}

    /** Settings that change what {@link #scan} finds; changing one re-scans (once a slider stops moving). */
    protected Setting<?>[] rescanOn() {
        return new Setting<?>[0];
    }

    /** Runs when a {@link #rescanOn} setting changed. Default: re-scan everything loaded. */
    protected void onScanSettingsChanged() {
        rescanAll();
    }

    /** How many chunks around the player get re-scanned periodically, 0 to disable. */
    protected int refreshRadius() {
        return 1;
    }

    /** Whether results survive their chunk unloading. */
    protected boolean keepUnloaded() {
        return false;
    }

    @Override
    public void onEnable() {
        rescanAll();
    }

    @Override
    public void onDisable() {
        generation.incrementAndGet();
        results.clear();
        queue.clear();
        inFlight.clear();
        done.clear();
        dirty.clear();
        dirtyAt.clear();
        retries.clear();
    }

    protected void rescanAll() {
        generation.incrementAndGet();
        if (!keepUnloaded()) results.clear();
        queue.clear();
        inFlight.clear();
        done.clear();
        retries.clear();
        if (!isEnabled() || !inGame()) return;
        for (WorldChunk chunk : WorldUtil.loadedChunks()) queue.add(chunk.getPos().toLong());
    }

    /** Queues one chunk for a (re)scan. Main thread only. */
    protected void enqueue(long key) {
        if (!inFlight.contains(key)) queue.add(key);
    }

    /** Thread safe: marks a chunk as changed so it gets re-scanned shortly. */
    protected void markDirty(int chunkX, int chunkZ) {
        dirty.add(ChunkPos.toLong(chunkX, chunkZ));
    }

    @Override
    public void onChunkLoad(WorldChunk chunk) {
        if (isEnabled()) enqueue(chunk.getPos().toLong());
    }

    @Override
    public void onChunkUnload(WorldChunk chunk) {
        long key = chunk.getPos().toLong();
        queue.remove(key);
        if (!keepUnloaded()) results.remove(key);
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (packet instanceof BlockUpdateS2CPacket p) {
            markDirty(p.getPos().getX() >> 4, p.getPos().getZ() >> 4);
        } else if (packet instanceof ChunkDeltaUpdateS2CPacket p) {
            long[] first = {Long.MIN_VALUE};
            p.visitUpdates((pos, state) -> {
                if (first[0] == Long.MIN_VALUE) first[0] = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
            });
            if (first[0] != Long.MIN_VALUE) dirty.add(first[0]);
        }
        return false;
    }

    @Override
    public void onTick() {
        ticks++;
        if (mc.world != world) {
            // results from the last world or dimension mean nothing here (and kept ones would draw in the wrong place)
            world = mc.world;
            generation.incrementAndGet();
            results.clear();
            queue.clear();
            inFlight.clear();
            done.clear();
            dirty.clear();
            dirtyAt.clear();
            retries.clear();
            onWorldChange();
            for (WorldChunk chunk : WorldUtil.loadedChunks()) queue.add(chunk.getPos().toLong());
        }
        Setting<?>[] watch = rescanOn();
        if (watch.length > 0) {
            List<Object> now = new ArrayList<>(watch.length);
            for (Setting<?> st : watch) now.add(st.get());
            if (watched == null) watched = now;
            else if (!now.equals(watched)) {
                watched = now;
                watchedSettleAt = ticks + 10;
            }
            if (watchedSettleAt != -1 && ticks >= watchedSettleAt) {
                watchedSettleAt = -1;
                onScanSettingsChanged();
            }
        }

        // block changes: wait a few ticks so bursts (explosions, pistons) coalesce into one rescan
        Long d;
        while ((d = dirty.poll()) != null) dirtyAt.putIfAbsent(d, ticks + 4);
        if (!dirtyAt.isEmpty()) {
            Iterator<Map.Entry<Long, Integer>> it = dirtyAt.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (e.getValue() <= ticks) {
                    enqueue(e.getKey());
                    it.remove();
                }
            }
        }

        int r = refreshRadius();
        if (r > 0 && refreshTimer-- <= 0) {
            refreshTimer = 60;
            ChunkPos center = mc.player.getChunkPos();
            for (int x = -r; x <= r; x++)
                for (int z = -r; z <= r; z++)
                    enqueue(ChunkPos.toLong(center.x + x, center.z + z));
        }

        applyDone();
        dispatch();
    }

    private void applyDone() {
        int gen = generation.get();
        Done<T> job;
        long deadline = System.nanoTime() + 2_000_000L;
        while ((job = done.poll()) != null) {
            inFlight.remove(job.key());
            if (job.generation() != gen) continue;
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(job.key()), ChunkPos.getPackedZ(job.key()));
            if (job.failed()) {
                // usually a block changed mid-read; try again a couple of times
                int n = retries.merge(job.key(), 1, Integer::sum);
                if (n <= 3 && chunk != null) queue.add(job.key());
                continue;
            }
            retries.remove(job.key());
            if (chunk == null) {
                if (!keepUnloaded()) results.remove(job.key());
                continue;
            }
            T result = job.result() == null ? null : postScan(chunk, job.result());
            T old = result == null ? results.remove(job.key()) : results.put(job.key(), result);
            if (result != null && old == null) onFound(chunk.getPos(), result);
            onStored(job.key(), old, result);
            if (System.nanoTime() > deadline) break;
        }
    }

    private void dispatch() {
        if (queue.isEmpty() || inFlight.size() >= MAX_IN_FLIGHT) return;
        ChunkPos center = mc.player.getChunkPos();
        int gen = generation.get();
        while (!queue.isEmpty() && inFlight.size() < MAX_IN_FLIGHT) {
            // nearest first, so what you're looking at shows up before the edge of render distance
            long best = 0;
            int bestDist = Integer.MAX_VALUE;
            for (long key : queue) {
                int dx = ChunkPos.getPackedX(key) - center.x, dz = ChunkPos.getPackedZ(key) - center.z;
                int dist = dx * dx + dz * dz;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = key;
                }
            }
            queue.remove(best);
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(best), ChunkPos.getPackedZ(best));
            if (chunk == null || chunk.isEmpty()) {
                if (!keepUnloaded()) results.remove(best);
                continue;
            }
            final long key = best;
            inFlight.add(key);
            WORKERS.execute(() -> {
                try {
                    done.add(new Done<>(key, gen, scan(chunk), false));
                } catch (Throwable t) {
                    if (!(t instanceof RuntimeException)) LootClient.LOGGER.warn("{} scan failed", getName(), t);
                    done.add(new Done<>(key, gen, null, true));
                }
            });
        }
    }

    /** Chunks still waiting to be scanned, for HUD info. */
    protected int pending() {
        return queue.size() + inFlight.size();
    }

    @Override
    public String getInfo() {
        return String.valueOf(results.size());
    }
}
