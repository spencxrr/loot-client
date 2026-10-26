package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.basefinding.ChunkScanModule;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BlockListSetting;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pings you in chat the moment a chunk with one of your chosen blocks loads. */
public class BlockNotifier extends ChunkScanModule<List<BlockPos>> {
    private final BlockListSetting blocks = add(new BlockListSetting("Blocks", "Blocks to watch for. Edit with .set BlockNotifier Blocks add <id>.",
        Blocks.SPAWNER, Blocks.BEACON, Blocks.ENDER_CHEST, Blocks.SHULKER_BOX).onChange(this::rescanAll));
    private final BoolSetting sound = add(new BoolSetting("Sound", "Play a ping.", true));
    private final BoolSetting highlight = add(new BoolSetting("Highlight", "Box the found blocks too.", true));

    public BlockNotifier() {
        super("Block Notifier", "Alerts you when chosen blocks load nearby.", Category.RENDER);
    }

    @Override
    protected int refreshRadius() {
        return 1;
    }

    @Override
    protected List<BlockPos> scan(WorldChunk chunk) {
        Set<Block> targets = blocks.get();
        if (targets.isEmpty()) return null;
        List<BlockPos> out = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        int bx = chunk.getPos().getStartX(), bz = chunk.getPos().getStartZ();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty() || !section.hasAny(s -> targets.contains(s.getBlock()))) continue;
            int by = chunk.sectionIndexToCoord(i) << 4;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        if (targets.contains(section.getBlockState(x, y, z).getBlock())) out.add(new BlockPos(bx + x, by + y, bz + z));
        }
        return out.isEmpty() ? null : out;
    }

    @Override
    protected void onFound(ChunkPos pos, List<BlockPos> found) {
        BlockPos p = found.get(0);
        String name = mc.world.getBlockState(p).getBlock().getName().getString();
        ChatUtil.coords((found.size() > 1 ? found.size() + "x " : "") + name + " at", p.getX(), p.getY(), p.getZ());
        if (sound.get()) ChatUtil.ping();
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        if (!highlight.get()) return;
        for (List<BlockPos> list : results.values())
            for (BlockPos p : list) r.box(new Box(p), dev.loot.client.gui.Theme.ACCENT, 40);
    }
}
