package dev.loot.client.module.basefinding;

import dev.loot.client.LootClient;
import dev.loot.client.module.Category;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.ModeSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.ChunkLightingView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds bases by their light. Three kinds of evidence, all in places that should be dark:
 *  - Placed lights: torches, lanterns, lamps, froglights, glowstone outside the nether... that
 *    sit where the sky can't reach (true underground, not just "below some Y"), or anywhere in
 *    the nether and end.
 *  - Lit furnaces, smokers and blast furnaces: someone was smelting there recently.
 *  - Hidden light: bright block light in open air with no light-giving block anywhere near it.
 *    The server still sends real light data when it hides or fakes the blocks themselves, so
 *    this catches lit rooms you can't otherwise see. Each one has to show up on two scans a few
 *    seconds apart before it counts, so a torch you just broke doesn't trigger it.
 *
 * Natural light (lava, fire, glow lichen, glow berries, amethyst, sculk, nether glowstone...)
 * is ignored and also counts as an explanation for nearby light. Structure chunks and spawn can
 * be skipped. Hits are fed into Sus Chunk Finder so they join the same base list, beams and HUD.
 */
public class LightFinder extends ChunkScanModule<LightFinder.Result> {
    static final int PLACED = 0, FURNACE = 1, HIDDEN = 2;

    record Mark(BlockPos pos, int kind) {}

    public record Result(List<Mark> marks, int placed, int furnaces, int hidden, int confirmedHidden, int minY, int maxY, int score, boolean structure) {}

    private final ModeSetting underground = add(new ModeSetting("Underground", "Where lights count: under the sky only (SkyLight), below MaxY, or anywhere.", "SkyLight", "SkyLight", "BelowY", "Anywhere"));
    private final NumberSetting maxY = add(new NumberSetting("MaxY", "Height limit for Underground BelowY.", 40, -64, 320, 1));
    private final BoolSetting hidden = add(new BoolSetting("HiddenLight", "Flag bright light with no visible source (hidden or fake blocks).", true));
    private final NumberSetting hiddenLevel = add(new NumberSetting("HiddenLevel", "Block light a spot needs to count as hidden light (15 = right next to a source).", 13, 10, 15, 1));
    private final BoolSetting furnaces = add(new BoolSetting("LitFurnaces", "Count furnaces that are burning right now.", true));
    private final NumberSetting minScore = add(new NumberSetting("MinScore", "Score a chunk needs (placed light 1, lit furnace 3, hidden light 4).", 3, 1, 30, 1));
    private final BoolSetting ignoreStructures = add(new BoolSetting("IgnoreStructures", "Skip chunks with strongholds, ancient cities, trial chambers and other generated lights.", true));
    private final NumberSetting spawnRadius = add(new NumberSetting("SpawnRadius", "Ignore everything this close to 0,0 (overworld blocks; nether is /8). 0 = off.", 1000, 0, 20000, 250));
    private final BoolSetting feedBases = add(new BoolSetting("FeedBaseFinder", "Add light evidence to Sus Chunk Finder's bases (when it's on).", true));
    private final BoolSetting alert = add(new BoolSetting("Alert", "Chat + sound for new lit areas (Sus Chunk Finder alerts instead when it's fed).", true));
    private final NumberSetting range = add(new NumberSetting("Range", "Max render distance.", 256, 16, 1024, 16));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity.", 50, 0, 255, 5));

    private final Set<Long> alerted = new HashSet<>();
    private final java.util.Map<Long, Integer> lightRetries = new java.util.concurrent.ConcurrentHashMap<>();
    private ClientWorld lastWorld;

    public LightFinder() {
        super("Light Finder", "Finds bases from player lights, lit furnaces and hidden light.", Category.BASEFINDING);
        underground.onChange(this::rescanAll);
    }

    @Override
    protected int refreshRadius() {
        return 2;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        alerted.clear();
        SusChunkFinder sus = sus();
        if (sus != null) sus.clearLight();
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        // light arrives in its own packets, often after the blocks; rescan when it changes
        if (packet instanceof LightUpdateS2CPacket p) markDirty(p.getChunkX(), p.getChunkZ());
        return super.onPacketReceive(packet);
    }

    @Override
    public void onTick() {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            alerted.clear();
            lightRetries.clear();
            SusChunkFinder sus = sus();
            if (sus != null) sus.clearLight();
        }
        super.onTick();
    }

    private static SusChunkFinder sus() {
        return LootClient.modules() == null ? null : LootClient.modules().get(SusChunkFinder.class);
    }

    // ---- classification ----

    private static volatile Set<Block> playerLights;

    private static Set<Block> playerLights() {
        if (playerLights != null) return playerLights;
        Set<Block> set = new HashSet<>(List.of(
            Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH,
            Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.SEA_LANTERN, Blocks.JACK_O_LANTERN, Blocks.REDSTONE_LAMP, Blocks.END_ROD,
            Blocks.BEACON, Blocks.CONDUIT, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT,
            Blocks.PEARLESCENT_FROGLIGHT, Blocks.RESPAWN_ANCHOR, Blocks.GLOWSTONE, Blocks.SHROOMLIGHT));
        for (Block b : Registries.BLOCK) {
            String id = Registries.BLOCK.getId(b).getPath();
            if (id.endsWith("candle") || id.endsWith("candle_cake") || id.contains("copper_bulb") || id.contains("copper_lantern")
                || id.contains("copper_torch") || id.contains("copper_wall_torch")) set.add(b);
        }
        playerLights = set;
        return set;
    }

    /** PLACED / FURNACE for player light, -1 for natural or no light. */
    private static int classify(BlockState s, boolean nether, boolean end) {
        if (s.getLuminance() <= 0) return -1;
        Block b = s.getBlock();
        if (b instanceof AbstractFurnaceBlock) return FURNACE;
        if (!playerLights().contains(b)) return -1;
        // the nether grows its own glowstone and shroomlights, the end its own end rods
        if (nether && (b == Blocks.GLOWSTONE || b == Blocks.SHROOMLIGHT)) return -1;
        if (end && b == Blocks.END_ROD) return -1;
        return PLACED;
    }

    // ---- scanning (worker thread) ----

    @Override
    protected Result scan(WorldChunk chunk) {
        World world = chunk.getWorld();
        boolean overworld = world.getRegistryKey() == World.OVERWORLD;
        boolean nether = world.getRegistryKey() == World.NETHER;
        boolean end = world.getRegistryKey() == World.END;
        ChunkLightingView blockLight = world.getLightingProvider().get(LightType.BLOCK);
        ChunkLightingView skyLight = world.getLightingProvider().get(LightType.SKY);
        boolean useSky = overworld && underground.is("SkyLight");
        int limit = overworld && underground.is("BelowY") ? maxY.getInt() : Integer.MAX_VALUE;
        int threshold = hiddenLevel.getInt();

        ChunkPos cp = chunk.getPos();
        int bx = cp.getStartX(), bz = cp.getStartZ();
        List<Mark> marks = new ArrayList<>();
        int placed = 0, lit = 0, hiddenCount = 0;
        int minY = Integer.MAX_VALUE, maxYFound = Integer.MIN_VALUE;
        boolean anyLight = false, lightMissing = false;
        BlockPos.Mutable probe = new BlockPos.Mutable();

        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            int sy = chunk.sectionIndexToCoord(i);
            int by = sy << 4;
            if (by > limit) break;
            ChunkNibbleArray sky = useSky ? skyLight.getLightSection(ChunkSectionPos.from(cp.x, sy, cp.z)) : null;

            // placed lights and lit furnaces, straight from the block palette
            if (section.hasAny(s -> classify(s, nether, end) >= 0)) {
                for (int y = 0; y < 16; y++) {
                    if (by + y > limit) break;
                    for (int z = 0; z < 16; z++)
                        for (int x = 0; x < 16; x++) {
                            BlockState s = section.getBlockState(x, y, z);
                            int kind = classify(s, nether, end);
                            if (kind < 0 || kind == FURNACE && !furnaces.get()) continue;
                            if (useSky && sky != null && !sky.isUninitialized() && maxSky(sky, x, y, z) > 0) continue; // the sky reaches it
                            marks.add(new Mark(new BlockPos(bx + x, by + y, bz + z), kind));
                            if (kind == PLACED) placed++;
                            else lit++;
                            minY = Math.min(minY, by + y);
                            maxYFound = Math.max(maxYFound, by + y);
                        }
                }
            }

            if (!hidden.get()) continue;
            ChunkNibbleArray light = blockLight.getLightSection(ChunkSectionPos.from(cp.x, sy, cp.z));
            if (light == null || light.isUninitialized()) {
                // light for this chunk hasn't arrived yet; look again shortly
                lightMissing = true;
                continue;
            }
            anyLight = true;
            for (int y = 0; y < 16; y++) {
                if (by + y > limit) break;
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        int level = light.get(x, y, z);
                        if (level < threshold) continue;
                        if (!section.getBlockState(x, y, z).isAir()) continue;
                        if (useSky && sky != null && !sky.isUninitialized() && sky.get(x, y, z) > 0) continue;
                        int wx = bx + x, wy = by + y, wz = bz + z;
                        if (nearMark(marks, wx, wy, wz, 4)) continue;
                        if (explained(world, probe, wx, wy, wz, 16 - level)) continue;
                        if (hiddenCount < 16) marks.add(new Mark(new BlockPos(wx, wy, wz), HIDDEN));
                        hiddenCount++;
                        minY = Math.min(minY, wy);
                        maxYFound = Math.max(maxYFound, wy);
                    }
            }
        }
        if (lightMissing && !anyLight && lightRetries.merge(cp.toLong(), 1, Integer::sum) <= 5) markDirty(cp.x, cp.z);
        if (marks.isEmpty()) return null;
        boolean structure = ignoreStructures.get() && Structures.detect(chunk) != null;
        return new Result(marks, placed, lit, hiddenCount, 0, minY, maxYFound, 0, structure);
    }

    /** Highest sky light around a non-opaque block (the light block's own cell may read 0 for some blocks). */
    private static int maxSky(ChunkNibbleArray sky, int x, int y, int z) {
        int best = sky.get(x, y, z);
        if (y < 15) best = Math.max(best, sky.get(x, y + 1, z));
        return best;
    }

    private static boolean nearMark(List<Mark> marks, int x, int y, int z, int r) {
        for (Mark m : marks) {
            BlockPos p = m.pos();
            if (Math.abs(p.getX() - x) <= r && Math.abs(p.getY() - y) <= r && Math.abs(p.getZ() - z) <= r) return true;
        }
        return false;
    }

    /** Is there any light-giving block (player or natural) close enough to cause this light? */
    private static boolean explained(World world, BlockPos.Mutable probe, int x, int y, int z, int r) {
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > r) continue;
                    probe.set(x + dx, y + dy, z + dz);
                    if (world.getBlockState(probe).getLuminance() > 0) return true;
                }
        return false;
    }

    // ---- scoring (main thread) ----

    @Override
    protected Result postScan(WorldChunk chunk, Result r) {
        ChunkPos pos = chunk.getPos();
        if (ignored(pos) || r.structure()) {
            feed(pos.toLong(), null);
            return null;
        }
        // hidden light only counts once a second scan agrees, so stale light after a block break doesn't count
        Result prev = results.get(pos.toLong());
        int confirmed = 0;
        if (r.hidden() > 0) {
            if (prev != null && prev.hidden() > 0) confirmed = r.hidden();
            else markDirty(pos.x, pos.z);
        }
        int score = Math.min(10, r.placed()) + 3 * Math.min(4, r.furnaces()) + 4 * Math.min(4, confirmed);
        Result out = new Result(r.marks(), r.placed(), r.furnaces(), r.hidden(), confirmed, r.minY(), r.maxY(), score, false);
        feed(pos.toLong(), score >= minScore.getInt() ? out : null);
        if (counts(out)) announce(pos, out);
        // keep unconfirmed results around (not drawn) so the confirming scan can see them
        return score >= minScore.getInt() || r.hidden() > 0 ? out : null;
    }

    private void feed(long key, Result r) {
        SusChunkFinder sus = sus();
        if (sus == null || !feedBases.get()) return;
        sus.feedLight(key, r == null ? null : new SusChunkFinder.LightEvidence(r.confirmedHidden(), r.furnaces(), r.minY(), r.maxY()));
    }

    private boolean ignored(ChunkPos pos) {
        double r = spawnRadius.get();
        if (r <= 0) return false;
        if (mc.world.getRegistryKey() == World.NETHER) r /= 8;
        double cx = pos.getCenterX(), cz = pos.getCenterZ();
        return cx * cx + cz * cz < r * r;
    }

    private boolean counts(Result r) {
        return r.score() >= minScore.getInt();
    }

    private void announce(ChunkPos pos, Result r) {
        if (!alert.get()) return;
        SusChunkFinder sus = sus();
        if (feedBases.get() && sus != null && sus.isEnabled()) return; // it'll announce the base
        for (long k : alerted) {
            if (Math.abs(ChunkPos.getPackedX(k) - pos.x) <= 2 && Math.abs(ChunkPos.getPackedZ(k) - pos.z) <= 2) return;
        }
        alerted.add(pos.toLong());
        BlockPos p = r.marks().get(0).pos();
        StringBuilder what = new StringBuilder();
        if (r.placed() > 0) what.append(r.placed()).append(" placed lights");
        if (r.furnaces() > 0) what.append(what.isEmpty() ? "" : ", ").append(r.furnaces()).append(" lit furnaces");
        if (r.confirmedHidden() > 0) what.append(what.isEmpty() ? "" : ", ").append("hidden light");
        ChatUtil.coords(what + " at", p.getX(), p.getY(), p.getZ());
        ChatUtil.ping();
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        double maxSq = range.get() * range.get();
        for (Result res : results.values()) {
            if (!counts(res)) continue;
            for (Mark m : res.marks()) {
                if (m.pos().getSquaredDistance(mc.player.getEntityPos()) > maxSq) continue;
                switch (m.kind()) {
                    case PLACED -> r.box(new Box(m.pos()), 0xFFFFD27A, fill.getInt());
                    case FURNACE -> r.box(new Box(m.pos()), 0xFFFF9A3C, fill.getInt());
                    default -> {
                        if (res.confirmedHidden() > 0) r.box(new Box(m.pos()).contract(0.2), 0xFFFF4DFF, fill.getInt());
                    }
                }
            }
        }
    }

    @Override
    public String getInfo() {
        int n = 0;
        for (Result res : results.values()) if (counts(res)) n++;
        return String.valueOf(n);
    }
}
