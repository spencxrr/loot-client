package dev.loot.client.module.basefinding;

import dev.loot.client.module.Category;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Looks for perfectly straight 1-wide, 2-tall air corridors cut through rock: caves are never
 * that regular, people strip-mining or tunnelling to a stash are. Runs are followed across chunk
 * borders, so long tunnels are reported once as one line no matter how many chunks they cross.
 */
public class TunnelBaseFinder extends ChunkScanModule<List<TunnelBaseFinder.Tunnel>> {
    record Tunnel(BlockPos start, BlockPos end) {
        int length() {
            return Math.max(Math.abs(end.getX() - start.getX()), Math.abs(end.getZ() - start.getZ())) + 1;
        }
    }

    private static final int MAX_FOLLOW = 160;

    private final NumberSetting minLength = add(new NumberSetting("MinLength", "Straight blocks needed to count as a tunnel.", 12, 5, 64, 1));
    private final NumberSetting maxY = add(new NumberSetting("MaxY", "Only look below this height.", 60, -64, 320, 1));
    private final BoolSetting alert = add(new BoolSetting("Alert", "Chat + sound for new tunnels.", true));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity.", 35, 0, 255, 5));

    public TunnelBaseFinder() {
        super("Tunnel Base Finder", "Finds straight, hand-dug tunnels underground.", Category.BASEFINDING);
    }

    private static volatile Set<Block> rocks;

    private static boolean rock(BlockState s) {
        if (rocks == null) {
            Set<Block> set = new HashSet<>(List.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.NETHERRACK, Blocks.ANDESITE,
                Blocks.DIORITE, Blocks.GRANITE, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.END_STONE, Blocks.CALCITE,
                Blocks.DRIPSTONE_BLOCK, Blocks.GRAVEL, Blocks.DIRT, Blocks.SOUL_SOIL, Blocks.SOUL_SAND, Blocks.MAGMA_BLOCK));
            for (Block b : Registries.BLOCK) if (Registries.BLOCK.getId(b).getPath().endsWith("_ore")) set.add(b);
            rocks = set;
        }
        return rocks.contains(s.getBlock());
    }

    /** Air 2 tall with rock floor, ceiling and both walls, for a corridor running along x (alongX) or z. */
    private boolean corridor(ChunkView v, int x, int y, int z, boolean alongX) {
        if (!v.air(x, y, z) || !v.air(x, y + 1, z)) return false;
        if (!rock(v.get(x, y - 1, z)) || !rock(v.get(x, y + 2, z))) return false;
        int dx = alongX ? 0 : 1, dz = alongX ? 1 : 0;
        return rock(v.get(x + dx, y, z + dz)) && rock(v.get(x - dx, y, z - dz))
            && rock(v.get(x + dx, y + 1, z + dz)) && rock(v.get(x - dx, y + 1, z - dz));
    }

    @Override
    protected List<Tunnel> scan(WorldChunk chunk) {
        ChunkView v = new ChunkView(chunk);
        List<Tunnel> out = new ArrayList<>();
        int top = Math.min(maxY.getInt(), v.topY() - 3);
        int need = minLength.getInt();
        for (int y = v.bottomY() + 1; y <= top; y++) {
            for (int a = 0; a < 16; a++) {
                for (int b = 0; b < 16; b++) {
                    // along x: row z = a, start x = b
                    if (corridor(v, b, y, a, true) && !corridor(v, b - 1, y, a, true)) {
                        int len = 1;
                        while (len < MAX_FOLLOW && corridor(v, b + len, y, a, true)) len++;
                        if (len >= need) out.add(new Tunnel(v.world(b, y, a), v.world(b + len - 1, y, a)));
                        b += len - 1;
                    }
                }
                for (int b = 0; b < 16; b++) {
                    // along z: column x = a, start z = b
                    if (corridor(v, a, y, b, false) && !corridor(v, a, y, b - 1, false)) {
                        int len = 1;
                        while (len < MAX_FOLLOW && corridor(v, a, y, b + len, false)) len++;
                        if (len >= need) out.add(new Tunnel(v.world(a, y, b), v.world(a, y, b + len - 1)));
                        b += len - 1;
                    }
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    @Override
    protected void onFound(ChunkPos pos, List<Tunnel> tunnels) {
        if (!alert.get()) return;
        Tunnel longest = tunnels.stream().max(Comparator.comparingInt(Tunnel::length)).orElse(tunnels.get(0));
        BlockPos p = longest.start();
        ChatUtil.coords("Tunnel (" + longest.length() + " long) at", p.getX(), p.getY(), p.getZ());
        ChatUtil.ping();
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        for (List<Tunnel> list : results.values())
            for (Tunnel t : list)
                r.box(new Box(t.start().getX(), t.start().getY(), t.start().getZ(), t.end().getX() + 1, t.end().getY() + 2, t.end().getZ() + 1),
                    dev.loot.client.gui.Theme.ACCENT, fill.getInt());
    }
}
