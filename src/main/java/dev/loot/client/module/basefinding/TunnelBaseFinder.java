package dev.loot.client.module.basefinding;

import dev.loot.client.module.Category;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Looks for perfectly straight 1-wide, 2-tall air corridors cut through stone: caves are never
 * that regular, people strip-mining or tunnelling to a stash are. Runs are measured inside each
 * chunk, so MinLength tops out at 16.
 */
public class TunnelBaseFinder extends ChunkScanModule<List<TunnelBaseFinder.Tunnel>> {
    record Tunnel(BlockPos start, BlockPos end) {}

    private final NumberSetting minLength = add(new NumberSetting("MinLength", "Straight blocks needed to count as a tunnel.", 10, 5, 16, 1));
    private final NumberSetting maxY = add(new NumberSetting("MaxY", "Only look below this height.", 60, -64, 320, 1));
    private final BoolSetting alert = add(new BoolSetting("Alert", "Chat + sound for new tunnels.", true));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity.", 35, 0, 255, 5));

    public TunnelBaseFinder() {
        super("Tunnel Base Finder", "Finds straight, hand-dug tunnels underground.", Category.BASEFINDING);
    }

    private static boolean rock(BlockState s) {
        return s.isOf(Blocks.STONE) || s.isOf(Blocks.DEEPSLATE) || s.isOf(Blocks.TUFF) || s.isOf(Blocks.NETHERRACK)
            || s.isOf(Blocks.ANDESITE) || s.isOf(Blocks.DIORITE) || s.isOf(Blocks.GRANITE) || s.isOf(Blocks.BLACKSTONE)
            || s.isOf(Blocks.BASALT) || s.isOf(Blocks.END_STONE);
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
            for (int z = 0; z < 16; z++) { // runs along x
                int run = 0;
                for (int x = 0; x <= 16; x++) {
                    if (x < 16 && corridor(v, x, y, z, true)) run++;
                    else {
                        if (run >= need) out.add(new Tunnel(v.world(x - run, y, z), v.world(x - 1, y, z)));
                        run = 0;
                    }
                }
            }
            for (int x = 0; x < 16; x++) { // runs along z
                int run = 0;
                for (int z = 0; z <= 16; z++) {
                    if (z < 16 && corridor(v, x, y, z, false)) run++;
                    else {
                        if (run >= need) out.add(new Tunnel(v.world(x, y, z - run), v.world(x, y, z - 1)));
                        run = 0;
                    }
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    @Override
    protected void onFound(ChunkPos pos, List<Tunnel> tunnels) {
        if (!alert.get()) return;
        BlockPos p = tunnels.get(0).start();
        ChatUtil.coords("Tunnel found at", p.getX(), p.getY(), p.getZ());
        ChatUtil.ping();
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        for (List<Tunnel> list : results.values())
            for (Tunnel t : list)
                r.box(new Box(t.start().getX(), t.start().getY(), t.start().getZ(), t.end().getX() + 1, t.end().getY() + 2, t.end().getZ() + 1),
                    0xFFA3AEFF, fill.getInt());
    }
}
