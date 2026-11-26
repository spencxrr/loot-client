package dev.loot.client.module.basefinding;

import dev.loot.client.module.Category;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.ModeSetting;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells freshly generated chunks from ones that existed before, which is how you follow trails:
 * a line of old chunks through new terrain is somebody's path, often to their base.
 *
 *  - New: the server sends liquid flow updates right after the chunk, because water and lava in
 *    a just-generated chunk haven't settled yet.
 *  - Old: the chunk arrives with flowing liquid already in it, so it has been ticked before.
 *  - Old gen: overworld chunks generated before 1.18 have plain stone down to y 0 where new
 *    terrain fades into deepslate. On a server that's been running for years these mark the
 *    original world, and anything built there is old.
 */
public class NewChunks extends ChunkScanModule<Integer> {
    private static final int OLD = 1, OLD_GEN = 2;

    private final BoolSetting showNew = add(new BoolSetting("New", "Show newly generated chunks (red).", true));
    private final BoolSetting showOld = add(new BoolSetting("Old", "Show chunks that were loaded before (green).", true));
    private final BoolSetting showOldGen = add(new BoolSetting("OldGen", "Show chunks generated before 1.18 (yellow), overworld only.", true));
    private final ModeSetting height = add(new ModeSetting("DrawAt", "Draw squares below you or at a fixed height.", "Fixed", "BelowMe", "Fixed"));
    private final NumberSetting fixedY = add(new NumberSetting("FixedY", "Height for DrawAt Fixed.", 0, -64, 320, 1));
    private final NumberSetting range = add(new NumberSetting("Range", "Max distance drawn, in chunks.", 64, 8, 256, 8));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Square opacity.", 55, 10, 255, 5));

    /** Filled from the network thread. */
    private final Set<Long> fresh = ConcurrentHashMap.newKeySet();
    private ClientWorld lastWorld;

    public NewChunks() {
        super("New Chunks", "Shows new vs old chunks so you can follow trails.", Category.BASEFINDING);
    }

    @Override
    protected int refreshRadius() {
        return 0;
    }

    @Override
    protected boolean keepUnloaded() {
        return true;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        fresh.clear();
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        // no super: liquids flowing shouldn't trigger rescans, they *are* the signal
        if (packet instanceof BlockUpdateS2CPacket p) {
            if (flowing(p.getState())) fresh.add(ChunkPos.toLong(p.getPos().getX() >> 4, p.getPos().getZ() >> 4));
        } else if (packet instanceof ChunkDeltaUpdateS2CPacket p) {
            p.visitUpdates((pos, state) -> {
                if (flowing(state)) fresh.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
            });
        }
        return false;
    }

    private static boolean flowing(BlockState state) {
        FluidState f = state.getFluidState();
        return !f.isEmpty() && !f.isStill();
    }

    @Override
    protected Integer scan(WorldChunk chunk) {
        World world = chunk.getWorld();
        if (world.getRegistryKey() == World.OVERWORLD && isOldGen(chunk)) return OLD_GEN;
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;
            if (section.hasAny(NewChunks::flowing)) return OLD;
        }
        return null;
    }

    /** Section y 0..15: new terrain is mostly deepslate at the bottom, old terrain is all stone. */
    private static boolean isOldGen(WorldChunk chunk) {
        int index = chunk.getSectionIndex(0);
        ChunkSection[] sections = chunk.getSectionArray();
        if (index < 0 || index >= sections.length) return false;
        ChunkSection s = sections[index];
        if (s == null || s.isEmpty()) return false;
        int stone = 0, deepslate = 0;
        for (int y = 0; y < 4; y++)
            for (int z = 0; z < 16; z += 2)
                for (int x = 0; x < 16; x += 2) {
                    BlockState b = s.getBlockState(x, y, z);
                    if (b.isOf(Blocks.STONE)) stone++;
                    else if (b.isOf(Blocks.DEEPSLATE)) deepslate++;
                }
        return stone > 100 && deepslate == 0;
    }

    @Override
    public void onTick() {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            results.clear();
            fresh.clear();
        }
        super.onTick();
        // forget things far behind you so a long flight doesn't grow memory forever
        if (mc.player.age % 200 == 0) {
            ChunkPos c = mc.player.getChunkPos();
            int max = range.getInt() * 2;
            prune(results.keySet().iterator(), c, max);
            prune(fresh.iterator(), c, max);
        }
    }

    private static void prune(Iterator<Long> it, ChunkPos c, int max) {
        while (it.hasNext()) {
            long k = it.next();
            if (Math.abs(ChunkPos.getPackedX(k) - c.x) > max || Math.abs(ChunkPos.getPackedZ(k) - c.z) > max) it.remove();
        }
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        double y = height.is("Fixed") ? fixedY.get() : Math.floor(mc.player.getLerpedPos(tickDelta).y) - 8;
        ChunkPos c = mc.player.getChunkPos();
        int max = range.getInt();
        int alpha = fill.getInt() << 24;
        if (showNew.get()) {
            for (long k : fresh) {
                if (far(k, c, max)) continue;
                square(r, k, y, alpha | 0xFF4D4D);
            }
        }
        for (var e : results.entrySet()) {
            long k = e.getKey();
            if (fresh.contains(k) || far(k, c, max)) continue;
            int type = e.getValue();
            if (type == OLD && showOld.get()) square(r, k, y, alpha | 0x5CE68A);
            else if (type == OLD_GEN && showOldGen.get()) square(r, k, y, alpha | 0xFFD24D);
        }
    }

    private static boolean far(long k, ChunkPos c, int max) {
        return Math.abs(ChunkPos.getPackedX(k) - c.x) > max || Math.abs(ChunkPos.getPackedZ(k) - c.z) > max;
    }

    private static void square(Render3D r, long k, double y, int argb) {
        int x1 = ChunkPos.getPackedX(k) << 4, z1 = ChunkPos.getPackedZ(k) << 4;
        r.boxFill(new Box(x1 + 0.5, y, z1 + 0.5, x1 + 15.5, y + 0.05, z1 + 15.5), argb);
    }

    @Override
    public String getInfo() {
        return fresh.size() + "/" + results.size();
    }
}
