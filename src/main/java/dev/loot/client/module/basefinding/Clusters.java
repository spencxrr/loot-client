package dev.loot.client.module.basefinding;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Chunk-key grouping shared by the finders. Keys are packed exactly like {@code ChunkPos.toLong}
 * (x in the low 32 bits, z in the high), but nothing here touches Minecraft, so it's unit tested.
 */
final class Clusters {
    private Clusters() {}

    static long key(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    static int x(long key) {
        return (int) key;
    }

    static int z(long key) {
        return (int) (key >>> 32);
    }

    /** Groups keys that are within {@code radius} chunks of each other (chained), biggest group first. */
    static List<List<Long>> group(Collection<Long> keys, int radius) {
        Set<Long> all = keys instanceof Set<Long> s ? s : new HashSet<>(keys);
        Set<Long> seen = new HashSet<>();
        List<List<Long>> out = new ArrayList<>();
        for (long start : keys) {
            if (!seen.add(start)) continue;
            List<Long> group = new ArrayList<>();
            Deque<Long> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                long k = stack.pop();
                group.add(k);
                int cx = x(k), cz = z(k);
                for (int dx = -radius; dx <= radius; dx++)
                    for (int dz = -radius; dz <= radius; dz++) {
                        long n = key(cx + dx, cz + dz);
                        if (all.contains(n) && seen.add(n)) stack.push(n);
                    }
            }
            out.add(group);
        }
        out.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return out;
    }

    /**
     * Trail chunks: old chunks with at least {@code minNew} of their 8 neighbours brand new. A strip
     * of already-visited terrain through fresh world is somebody's route.
     */
    static Set<Long> trail(Collection<Long> old, LongPredicate isNew, int minNew) {
        Set<Long> out = new HashSet<>();
        for (long k : old) {
            int cx = x(k), cz = z(k), news = 0;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if ((dx != 0 || dz != 0) && isNew.test(key(cx + dx, cz + dz))) news++;
            if (news >= minNew) out.add(k);
        }
        return out;
    }

    /** "east-west" or "north-south", whichever way the group is longer. */
    static String axis(Collection<Long> group) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (long k : group) {
            minX = Math.min(minX, x(k));
            maxX = Math.max(maxX, x(k));
            minZ = Math.min(minZ, z(k));
            maxZ = Math.max(maxZ, z(k));
        }
        return maxX - minX >= maxZ - minZ ? "east-west" : "north-south";
    }
}
