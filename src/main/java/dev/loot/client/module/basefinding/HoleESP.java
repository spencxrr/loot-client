package dev.loot.client.module.basefinding;

import dev.loot.client.module.Category;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.ModeSetting;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Shafts: highlights 1x1 vertical holes dug straight down, the usual way people head to their
 * underground stash. Safe: highlights 1x1 holes walled with bedrock/obsidian for crystal PvP.
 */
public class HoleESP extends ChunkScanModule<List<HoleESP.Hole>> {
    record Hole(BlockPos bottom, int depth, boolean bedrock) {}

    private final ModeSetting mode = add(new ModeSetting("Mode", "Shafts for dug-down holes, Safe for PvP holes.", "Shafts", "Shafts", "Safe"));
    private final NumberSetting minDepth = add(new NumberSetting("MinDepth", "Shaft depth needed to count.", 8, 3, 64, 1));
    private final NumberSetting range = add(new NumberSetting("Range", "Max render distance.", 128, 8, 512, 8));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity.", 45, 0, 255, 5));

    public HoleESP() {
        super("Hole ESP", "Shows dug-down 1x1 shafts or safe PvP holes.", Category.BASEFINDING);
        mode.onChange(this::rescanAll);
    }

    @Override
    protected List<Hole> scan(WorldChunk chunk) {
        ChunkView v = new ChunkView(chunk);
        List<Hole> out = new ArrayList<>();
        boolean shafts = mode.is("Shafts");
        int bottom = v.bottomY() + 1, top = v.topY() - 2;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (shafts) scanShafts(v, x, z, bottom, top, out);
                else scanSafe(v, x, z, bottom, top, out);
            }
        }
        return out.isEmpty() ? null : out;
    }

    private void scanShafts(ChunkView v, int x, int z, int bottom, int top, List<Hole> out) {
        int run = 0;
        for (int y = top; y >= bottom; y--) {
            boolean enclosed = v.air(x, y, z) && v.solid(x + 1, y, z) && v.solid(x - 1, y, z) && v.solid(x, y, z + 1) && v.solid(x, y, z - 1);
            if (enclosed) {
                run++;
                continue;
            }
            if (run >= minDepth.getInt() && v.solid(x, y, z)) out.add(new Hole(v.world(x, y + 1, z), run, false));
            run = 0;
        }
    }

    private static boolean hard(BlockState s) {
        return s.isOf(Blocks.BEDROCK) || s.isOf(Blocks.OBSIDIAN) || s.isOf(Blocks.CRYING_OBSIDIAN)
            || s.isOf(Blocks.REINFORCED_DEEPSLATE) || s.isOf(Blocks.RESPAWN_ANCHOR) || s.isOf(Blocks.ENDER_CHEST) || s.isOf(Blocks.NETHERITE_BLOCK);
    }

    private void scanSafe(ChunkView v, int x, int z, int bottom, int top, List<Hole> out) {
        for (int y = bottom; y < top; y++) {
            if (!v.air(x, y, z) || !v.air(x, y + 1, z) || !v.air(x, y + 2, z)) continue;
            BlockState below = v.get(x, y - 1, z);
            if (!hard(below)) continue;
            BlockState[] sides = {v.get(x + 1, y, z), v.get(x - 1, y, z), v.get(x, y, z + 1), v.get(x, y, z - 1)};
            boolean allBedrock = below.isOf(Blocks.BEDROCK);
            boolean ok = true;
            for (BlockState s : sides) {
                if (!hard(s)) {
                    ok = false;
                    break;
                }
                if (!s.isOf(Blocks.BEDROCK)) allBedrock = false;
            }
            if (ok) out.add(new Hole(v.world(x, y, z), 1, allBedrock));
        }
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        double maxSq = range.get() * range.get();
        boolean shafts = mode.is("Shafts");
        for (List<Hole> holes : results.values()) {
            for (Hole h : holes) {
                if (h.bottom().getSquaredDistance(mc.player.getEntityPos()) > maxSq) continue;
                if (shafts) {
                    r.box(new Box(h.bottom().getX(), h.bottom().getY(), h.bottom().getZ(),
                        h.bottom().getX() + 1, h.bottom().getY() + h.depth(), h.bottom().getZ() + 1), dev.loot.client.gui.Theme.ACCENT, fill.getInt());
                } else {
                    int color = h.bedrock() ? 0xFF5CE68A : 0xFFFFC05C;
                    r.box(new Box(h.bottom().getX(), h.bottom().getY(), h.bottom().getZ(),
                        h.bottom().getX() + 1, h.bottom().getY() + 0.05, h.bottom().getZ() + 1), color, fill.getInt());
                }
            }
        }
    }
}
