package dev.loot.client.module.basefinding;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightMathTest {
    /** A small world: light-giving blocks, solid blocks, and block light propagated like vanilla. */
    static final class World implements LightMath.Grid {
        final Map<Long, Integer> sources = new HashMap<>();
        final Set<Long> solid = new HashSet<>();
        final Map<Long, Integer> lightOverride = new HashMap<>();
        int unloadedBeyondX = Integer.MAX_VALUE;
        /** Sources whose light exists but whose block we pretend not to see (a hidden block). */
        final Set<Long> invisible = new HashSet<>();

        static long key(int x, int y, int z) {
            return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
        }

        World source(int x, int y, int z, int lum) {
            sources.put(key(x, y, z), lum);
            return this;
        }

        World wall(int x, int y, int z) {
            solid.add(key(x, y, z));
            return this;
        }

        @Override
        public boolean loaded(int x, int y, int z) {
            return x <= unloadedBeyondX;
        }

        @Override
        public int luminance(int x, int y, int z) {
            long k = key(x, y, z);
            return invisible.contains(k) ? 0 : sources.getOrDefault(k, 0);
        }

        @Override
        public int light(int x, int y, int z) {
            Integer o = lightOverride.get(key(x, y, z));
            if (o != null) return o;
            if (solid.contains(key(x, y, z))) return 0;
            int best = 0;
            for (var e : sources.entrySet()) {
                long k = e.getKey();
                int sx = (int) (k >> 40), sy = (int) ((k >> 20) & 0xFFFFF), sz = (int) (k & 0xFFFFF);
                if (sx >= 1 << 19) sx -= 1 << 20;
                if (sy >= 1 << 19) sy -= 1 << 20;
                if (sz >= 1 << 19) sz -= 1 << 20;
                int d = Math.abs(sx - x) + Math.abs(sy - y) + Math.abs(sz - z);
                best = Math.max(best, e.getValue() - d);
            }
            return Math.max(0, best);
        }

        @Override
        public boolean opaque(int x, int y, int z) {
            return solid.contains(key(x, y, z));
        }
    }

    @Test
    void lavaNextToTheSpotExplainsIt() {
        World w = new World().source(0, 0, 0, 15);
        assertFalse(LightMath.hidden(w, 1, 0, 0, w.light(1, 0, 0), 13));
        assertFalse(LightMath.hidden(w, 2, 0, 0, w.light(2, 0, 0), 13));
    }

    @Test
    void glowBerriesAndLichenExplainTheirLight() {
        World berries = new World().source(0, 5, 0, 14);
        assertFalse(LightMath.hidden(berries, 0, 4, 0, berries.light(0, 4, 0), 13));
        World lichen = new World().source(0, 0, 0, 7);
        // lichen can never reach the hidden threshold at all
        assertFalse(LightMath.hidden(lichen, 1, 0, 0, lichen.light(1, 0, 0), 10));
    }

    @Test
    void naturalSourceAtTheEdgeOfReachStillExplains() {
        // level 10 spot, lava 5 blocks away: the full reach has to be searched
        World w = new World().source(0, 0, 0, 15);
        int level = w.light(5, 0, 0);
        assertTrue(level == 10);
        assertFalse(LightMath.hidden(w, 5, 0, 0, level, 10));
    }

    @Test
    void sourceInAnUnloadedChunkNeverCounts() {
        World w = new World().source(3, 0, 0, 15);
        w.invisible.add(World.key(3, 0, 0));
        w.unloadedBeyondX = 2; // the source is past the edge of what we have
        assertFalse(LightMath.hidden(w, 1, 0, 0, w.light(1, 0, 0), 13));
    }

    @Test
    void lightWithAHiddenSourceIsFlagged() {
        World w = new World().source(0, 0, 0, 14);
        w.invisible.add(World.key(0, 0, 0)); // the server shows a fake block where the torch is
        assertTrue(LightMath.hidden(w, 1, 0, 0, w.light(1, 0, 0), 13));
    }

    @Test
    void flatFakeLightIsRejected() {
        World w = new World();
        for (int x = -3; x <= 3; x++)
            for (int y = -3; y <= 3; y++)
                for (int z = -3; z <= 3; z++) w.lightOverride.put(World.key(x, y, z), 15);
        assertFalse(LightMath.hidden(w, 0, 0, 0, 15, 13));
    }

    @Test
    void randomNoiseIsRejected() {
        World w = new World();
        w.lightOverride.put(World.key(0, 0, 0), 14);
        w.lightOverride.put(World.key(1, 0, 0), 2);
        w.lightOverride.put(World.key(-1, 0, 0), 13);
        assertFalse(LightMath.hidden(w, 0, 0, 0, 14, 13));
    }

    @Test
    void dimLightIsIgnored() {
        World w = new World().source(0, 0, 0, 12);
        w.invisible.add(World.key(0, 0, 0));
        assertFalse(LightMath.hidden(w, 1, 0, 0, w.light(1, 0, 0), 13));
    }
}
