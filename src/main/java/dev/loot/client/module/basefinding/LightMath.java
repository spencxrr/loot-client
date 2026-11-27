package dev.loot.client.module.basefinding;

/**
 * The hidden-light rules, kept free of Minecraft classes so they can be unit tested.
 *
 * Block light only ever comes from a block with luminance L and drops by one per block of
 * (Manhattan) distance, so a spot at light level {@code level} needs a source with
 * L - distance >= level, which means distance <= 15 - level. If any light-giving block sits in
 * that range, natural or not, the light is explained. Anything we can't see (unloaded chunks)
 * also counts as explained, so we never flag light just because its source is out of view.
 */
final class LightMath {
    private LightMath() {}

    interface Grid {
        /** False when the block at this position isn't known (unloaded chunk). */
        boolean loaded(int x, int y, int z);

        /** Light the block gives off (0 for none). */
        int luminance(int x, int y, int z);

        /** Block light level stored at this position. */
        int light(int x, int y, int z);

        /** Full opaque cube (light inside it is always 0). */
        boolean opaque(int x, int y, int z);
    }

    /** Max distance a source can be from a spot at this light level, plus one block of slack. */
    static int reach(int level) {
        return Math.max(1, 16 - level);
    }

    static boolean explained(Grid g, int x, int y, int z, int level) {
        int r = reach(level);
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > r) continue;
                    int px = x + dx, py = y + dy, pz = z + dz;
                    if (!g.loaded(px, py, pz)) return true;
                    if (g.luminance(px, py, pz) > 0) return true;
                }
        return false;
    }

    /**
     * Real block light: at least one neighbour is exactly one level dimmer (the way away from the
     * source), and no see-through neighbour differs by more than one. Flat fake light (all 15s)
     * and random noise both fail. Unknown neighbours fail too, to stay on the safe side.
     */
    static boolean realGradient(Grid g, int x, int y, int z, int level) {
        boolean dimmer = false;
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : dirs) {
            int px = x + d[0], py = y + d[1], pz = z + d[2];
            if (!g.loaded(px, py, pz)) return false;
            if (g.opaque(px, py, pz)) continue;
            int n = g.light(px, py, pz);
            if (n == level - 1) dimmer = true;
            else if (Math.abs(n - level) > 1) return false;
        }
        return dimmer;
    }

    /** A spot only counts as hidden light when it's bright, real-looking, and nothing nearby explains it. */
    static boolean hidden(Grid g, int x, int y, int z, int level, int threshold) {
        return level >= threshold && !explained(g, x, y, z, level) && realGradient(g, x, y, z, level);
    }
}
