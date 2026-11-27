package dev.loot.client.module.basefinding;

import dev.loot.client.module.Category;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.ModeSetting;
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
    private final ModeSetting size = add(new ModeSetting("Size", "Tunnel cross-section: 1x2 strip mines, 2x2 and 3x3 dug highways and nether tunnels.", "1x2", "1x2", "2x2", "3x3", "All"));
    private final BoolSetting alert = add(new BoolSetting("Alert", "Chat + sound for new tunnels.", true));
    private final BoolSetting feedBases = add(new BoolSetting("FeedBaseFinder", "Count tunnels as evidence in Sus Chunk Finder's bases (when it's on).", true));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity.", 35, 0, 255, 5));

    public TunnelBaseFinder() {
        super("Tunnel Base Finder", "Finds straight, hand-dug tunnels underground.", Category.BASEFINDING);
        size.onChange(this::rescanAll);
    }

    @Override
    protected boolean keepUnloaded() {
        return true; // tunnels stay marked after you fly past
    }

    @Override
    protected dev.loot.client.setting.Setting<?>[] rescanOn() {
        return new dev.loot.client.setting.Setting<?>[]{minLength, maxY};
    }

    @Override
    public void onDisable() {
        super.onDisable();
        SusChunkFinder sus = sus();
        if (sus != null) sus.clearFed("tunnel");
    }

    @Override
    public void onTick() {
        boolean feeding = feedBases.get();
        if (feeding != lastFeeding) {
            lastFeeding = feeding;
            SusChunkFinder sus = sus();
            if (sus != null) {
                if (!feeding) sus.clearFed("tunnel");
                else results.forEach(this::feed);
            }
        }
        super.onTick();
    }

    private boolean lastFeeding;

    @Override
    protected void onWorldChange() {
        SusChunkFinder sus = sus();
        if (sus != null) sus.clearFed("tunnel");
    }

    @Override
    protected void onStored(long key, List<Tunnel> old, List<Tunnel> now) {
        if (feedBases.get()) feed(key, now);
    }

    private void feed(long key, List<Tunnel> tunnels) {
        SusChunkFinder sus = sus();
        if (sus == null) return;
        if (tunnels == null || tunnels.isEmpty()) {
            sus.feed("tunnel", key, null);
            return;
        }
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, n = 0;
        for (Tunnel t : tunnels) {
            minY = Math.min(minY, t.start().getY());
            maxY = Math.max(maxY, t.end().getY() + 1);
            // a really long one says more than a short one
            n += 1 + t.length() / 48;
        }
        sus.feed("tunnel", key, new SusChunkFinder.Evidence(java.util.Map.of("tunnel", n), minY, maxY));
    }

    private static SusChunkFinder sus() {
        return dev.loot.client.LootClient.modules() == null ? null : dev.loot.client.LootClient.modules().get(SusChunkFinder.class);
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

    /**
     * A w-wide, h-tall slice of air with rock floor, ceiling and both walls, for a corridor running
     * along x (alongX) or z. (x, z) is the slice's first column; the rest extend in +perpendicular.
     */
    private static boolean corridor(ChunkView v, int x, int y, int z, boolean alongX, int w, int h) {
        int px = alongX ? 0 : 1, pz = alongX ? 1 : 0;
        for (int i = 0; i < w; i++) {
            int cx = x + px * i, cz = z + pz * i;
            for (int j = 0; j < h; j++) if (!v.air(cx, y + j, cz)) return false;
            if (!rock(v.get(cx, y - 1, cz)) || !rock(v.get(cx, y + h, cz))) return false;
        }
        for (int j = 0; j < h; j++) {
            if (!rock(v.get(x - px, y + j, z - pz))) return false;
            if (!rock(v.get(x + px * w, y + j, z + pz * w))) return false;
        }
        return true;
    }

    @Override
    protected List<Tunnel> scan(WorldChunk chunk) {
        List<Tunnel> out = new ArrayList<>();
        String mode = size.get();
        if (mode.equals("All")) {
            for (int[] s : new int[][]{{1, 2}, {2, 2}, {3, 3}}) scanSize(chunk, s[0], s[1], out);
        } else {
            scanSize(chunk, mode.charAt(0) - '0', mode.charAt(2) - '0', out);
        }
        return out.isEmpty() ? null : out;
    }

    private void scanSize(WorldChunk chunk, int w, int h, List<Tunnel> out) {
        ChunkView v = new ChunkView(chunk);
        int top = Math.min(maxY.getInt(), v.topY() - 1 - h);
        int need = minLength.getInt();
        for (int y = v.bottomY() + 1; y <= top; y++) {
            for (int a = 0; a < 16; a++) {
                for (int b = 0; b < 16; b++) {
                    // along x: row z = a, start x = b
                    if (corridor(v, b, y, a, true, w, h) && !corridor(v, b - 1, y, a, true, w, h)) {
                        int len = 1;
                        while (len < MAX_FOLLOW && corridor(v, b + len, y, a, true, w, h)) len++;
                        if (len >= need) out.add(new Tunnel(v.world(b, y, a), v.world(b + len - 1, y + h - 2, a + w - 1)));
                        b += len - 1;
                    }
                }
                for (int b = 0; b < 16; b++) {
                    // along z: column x = a, start z = b
                    if (corridor(v, a, y, b, false, w, h) && !corridor(v, a, y, b - 1, false, w, h)) {
                        int len = 1;
                        while (len < MAX_FOLLOW && corridor(v, a, y, b + len, false, w, h)) len++;
                        if (len >= need) out.add(new Tunnel(v.world(a, y, b), v.world(a + w - 1, y + h - 2, b + len - 1)));
                        b += len - 1;
                    }
                }
            }
        }
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
