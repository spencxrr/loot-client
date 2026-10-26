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
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds light sources that only players place (torches, lanterns, lamps...), optionally only
 * underground, which is where hidden bases and stash tunnels tend to give themselves away.
 */
public class LightFinder extends ChunkScanModule<List<BlockPos>> {
    private static final Set<Block> LIGHTS = Set.of(
        Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
        Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.SEA_LANTERN, Blocks.JACK_O_LANTERN, Blocks.REDSTONE_LAMP,
        Blocks.END_ROD, Blocks.BEACON, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
        Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT, Blocks.CANDLE);

    private final BoolSetting undergroundOnly = add(new BoolSetting("Underground", "Only count lights below the max Y.", true));
    private final NumberSetting maxY = add(new NumberSetting("MaxY", "Height limit for Underground.", 40, -64, 320, 1));
    private final NumberSetting minCount = add(new NumberSetting("MinCount", "Lights a chunk needs before it alerts.", 3, 1, 50, 1));
    private final BoolSetting alert = add(new BoolSetting("Alert", "Chat + sound when a chunk passes MinCount.", true));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity.", 50, 0, 255, 5));

    public LightFinder() {
        super("Light Finder", "Finds player-placed lights like torches and lanterns.", Category.BASEFINDING);
    }

    private static Set<Block> all;

    private static boolean isLight(BlockState s) {
        if (all == null) {
            Set<Block> set = new HashSet<>(LIGHTS);
            for (Block b : Registries.BLOCK) {
                String id = Registries.BLOCK.getId(b).getPath();
                if (id.endsWith("candle") || id.endsWith("copper_lantern") || id.endsWith("copper_bulb") || id.endsWith("copper_torch")
                    || id.endsWith("copper_wall_torch")) set.add(b);
            }
            all = set;
        }
        return all.contains(s.getBlock());
    }

    @Override
    protected List<BlockPos> scan(WorldChunk chunk) {
        List<BlockPos> out = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        int bx = chunk.getPos().getStartX(), bz = chunk.getPos().getStartZ();
        int limit = undergroundOnly.get() ? maxY.getInt() : Integer.MAX_VALUE;
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            int by = chunk.sectionIndexToCoord(i) << 4;
            if (by > limit) break;
            if (!section.hasAny(LightFinder::isLight)) continue;
            for (int y = 0; y < 16; y++) {
                if (by + y > limit) break;
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        if (isLight(section.getBlockState(x, y, z))) out.add(new BlockPos(bx + x, by + y, bz + z));
            }
        }
        return out.size() >= minCount.getInt() ? out : null;
    }

    @Override
    protected void onFound(ChunkPos pos, List<BlockPos> lights) {
        if (!alert.get()) return;
        BlockPos p = lights.get(0);
        ChatUtil.coords(lights.size() + " placed lights near", p.getX(), p.getY(), p.getZ());
        ChatUtil.ping();
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        for (List<BlockPos> list : results.values())
            for (BlockPos p : list) r.box(new Box(p), 0xFFFFD27A, fill.getInt());
    }
}
