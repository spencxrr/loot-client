package dev.loot.client.module.basefinding;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClustersTest {
    private static long k(int x, int z) {
        return Clusters.key(x, z);
    }

    @Test
    void packingRoundTripsNegativeCoords() {
        for (int[] c : new int[][]{{0, 0}, {-1, -1}, {123456, -98765}, {-1875000, 1875000}}) {
            long key = k(c[0], c[1]);
            assertEquals(c[0], Clusters.x(key));
            assertEquals(c[1], Clusters.z(key));
        }
        // same layout as ChunkPos.toLong: x low bits, z high bits
        assertEquals(5L | (7L << 32), k(5, 7));
    }

    @Test
    void chunksTwoApartJoinThreeApartDont() {
        List<List<Long>> g = Clusters.group(List.of(k(0, 0), k(2, 0), k(4, 2), k(8, 2)), 2);
        assertEquals(2, g.size());
        assertEquals(3, g.get(0).size(), "0,0 / 2,0 / 4,2 chain into one");
        assertEquals(List.of(k(8, 2)), g.get(1));
    }

    @Test
    void groupsAcrossTheOrigin() {
        List<List<Long>> g = Clusters.group(List.of(k(-1, -1), k(0, 0), k(1, -1)), 1);
        assertEquals(1, g.size());
        assertEquals(3, g.get(0).size());
    }

    @Test
    void emptyInputGivesNoGroups() {
        assertTrue(Clusters.group(List.of(), 2).isEmpty());
    }

    @Test
    void trailThroughNewTerrain() {
        // a 1-wide strip of old chunks along x, everything around it new
        Set<Long> old = new HashSet<>();
        for (int x = 0; x < 10; x++) old.add(k(x, 0));
        Set<Long> trail = Clusters.trail(old, key -> !old.contains(key) && Math.abs(Clusters.z(key)) <= 1, 3);
        assertEquals(old, trail);
        assertEquals("east-west", Clusters.axis(trail));
    }

    @Test
    void oldAreaWithoutNewNeighboursIsNoTrail() {
        Set<Long> old = new HashSet<>();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) old.add(k(x, z));
        // nothing around is new: a big explored area isn't a trail
        assertTrue(Clusters.trail(old, key -> false, 3).isEmpty());
    }

    @Test
    void axisNorthSouth() {
        assertEquals("north-south", Clusters.axis(List.of(k(0, 0), k(0, 1), k(1, 5))));
    }
}
