package dev.loot.client.module.basefinding;

import dev.loot.client.module.Category;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights single blocks that world generation never (or almost never) places, so each one
 * is a player's doing. "Underground" blocks like beds and crafting tables only count below the
 * height limit, since villages and igloos generate them on the surface.
 */
public class SuspiciousESP extends ChunkScanModule<List<SuspiciousESP.Find>> {
    record Find(BlockPos pos, int color) {}

    private final NumberSetting undergroundY = add(new NumberSetting("UndergroundY", "Beds, tables and furnaces only count below this.", 40, -64, 320, 1));
    private final BoolSetting redstone = add(new BoolSetting("Redstone", "Include hoppers, observers, droppers and pistons.", true));
    private final BoolSetting alert = add(new BoolSetting("Alert", "Chat + sound for new chunks with finds.", false));
    private final NumberSetting range = add(new NumberSetting("Range", "Max render distance.", 256, 16, 1024, 16));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity.", 50, 0, 255, 5));

    public SuspiciousESP() {
        super("Suspicious ESP", "Highlights blocks that only players place.", Category.BASEFINDING);
    }

    /** 0 when not suspicious, otherwise the color to draw it in. */
    private int classify(BlockState s, int y) {
        Block b = s.getBlock();
        if (b instanceof ShulkerBoxBlock) return 0xFFC77DFF;
        if (b == Blocks.ENDER_CHEST || b == Blocks.BEACON || b == Blocks.RESPAWN_ANCHOR || b == Blocks.ENCHANTING_TABLE) return 0xFFB18CFF;
        if (b == Blocks.NETHERITE_BLOCK || b == Blocks.DIAMOND_BLOCK || b == Blocks.EMERALD_BLOCK) return 0xFF5CE6E6;
        if (b instanceof AnvilBlock || b == Blocks.CRAFTER || b == Blocks.COMPARATOR) return 0xFFFFB45C;
        if (redstone.get() && (b == Blocks.HOPPER || b == Blocks.OBSERVER || b == Blocks.DROPPER || b == Blocks.PISTON)) return 0xFFFF7A7A;
        if (y < undergroundY.getInt()) {
            if (b instanceof BedBlock || b instanceof AbstractSignBlock) return 0xFFFF7AC8;
            if (b == Blocks.CRAFTING_TABLE || b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER
                || b == Blocks.BARREL) return 0xFFFFD27A;
        }
        return 0;
    }

    @Override
    protected List<Find> scan(WorldChunk chunk) {
        List<Find> out = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        int bx = chunk.getPos().getStartX(), bz = chunk.getPos().getStartZ();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            int by = chunk.sectionIndexToCoord(i) << 4;
            if (!section.hasAny(s -> classify(s, by) != 0 || classify(s, by + 15) != 0)) continue;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        int color = classify(section.getBlockState(x, y, z), by + y);
                        if (color != 0) out.add(new Find(new BlockPos(bx + x, by + y, bz + z), color));
                    }
        }
        return out.isEmpty() ? null : out;
    }

    @Override
    protected void onFound(ChunkPos pos, List<Find> finds) {
        if (!alert.get()) return;
        BlockPos p = finds.get(0).pos();
        ChatUtil.coords(finds.size() + " suspicious blocks near", p.getX(), p.getY(), p.getZ());
        ChatUtil.ping();
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        double maxSq = range.get() * range.get();
        for (List<Find> list : results.values())
            for (Find f : list)
                if (f.pos().getSquaredDistance(mc.player.getEntityPos()) <= maxSq) r.box(new Box(f.pos()), f.color(), fill.getInt());
    }
}
