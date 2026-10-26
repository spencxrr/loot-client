package dev.loot.client.module.basefinding;

import dev.loot.client.render.Render3D;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

/** Flat chunk-sized markers drawn at a fixed height, used by the chunk-level finders. */
final class ChunkHighlight {
    private ChunkHighlight() {}

    static void draw(Render3D r, ChunkPos pos, double y, int argb, int fillAlpha) {
        Box box = new Box(pos.getStartX(), y, pos.getStartZ(), pos.getEndX() + 1, y + 0.1, pos.getEndZ() + 1);
        r.box(box, argb, fillAlpha);
    }
}
