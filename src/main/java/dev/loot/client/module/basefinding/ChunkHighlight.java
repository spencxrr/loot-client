package dev.loot.client.module.basefinding;

import dev.loot.client.render.Render3D;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

import java.util.function.LongPredicate;

/**
 * Flat chunk-sized squares drawn at one height, used by the chunk-level finders. Touching
 * flagged chunks merge into one shape (no inner edges). An inset shrinks the outer border so two
 * finders drawn at the same height layer as a ring (outer finder) around a fill (inner finder).
 */
final class ChunkHighlight {
    private ChunkHighlight() {}

    static void draw(Render3D r, ChunkPos pos, double y, int argb, int fillAlpha) {
        Box box = new Box(pos.getStartX(), y, pos.getStartZ(), pos.getEndX() + 1, y + 0.1, pos.getEndZ() + 1);
        r.box(box, argb, fillAlpha);
    }

    /** One chunk of a merged region: fill plus only the outside edges. */
    static void region(Render3D r, long key, LongPredicate flagged, double y, int rgb, int fillAlpha, double inset) {
        int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
        boolean n = flagged.test(ChunkPos.toLong(cx, cz - 1)), s = flagged.test(ChunkPos.toLong(cx, cz + 1));
        boolean w = flagged.test(ChunkPos.toLong(cx - 1, cz)), e = flagged.test(ChunkPos.toLong(cx + 1, cz));
        double x1 = (cx << 4) + (w ? 0 : inset), x2 = (cx << 4) + 16 - (e ? 0 : inset);
        double z1 = (cz << 4) + (n ? 0 : inset), z2 = (cz << 4) + 16 - (s ? 0 : inset);
        r.boxFill(new Box(x1, y, z1, x2, y + 0.05, z2), (fillAlpha << 24) | (rgb & 0xFFFFFF));
        int edge = 0xFF000000 | rgb;
        double ly = y + 0.06;
        if (!n) r.line(x1, ly, z1, x2, ly, z1, edge);
        if (!s) r.line(x1, ly, z2, x2, ly, z2, edge);
        if (!w) r.line(x1, ly, z1, x1, ly, z2, edge);
        if (!e) r.line(x2, ly, z1, x2, ly, z2, edge);
    }
}
