package dev.loot.client.module.basefinding;

import dev.loot.client.LootClient;
import dev.loot.client.gui.Theme;
import dev.loot.client.gui.UI;
import dev.loot.client.module.Category;
import dev.loot.client.render.Projection;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.ModeSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.ChunkLightingView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
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
 * Only artificial or unexplained light ever counts. Natural light (lava and lava falls, fire,
 * magma, glow lichen, glow berries, amethyst, sculk, sea pickles, lit redstone ore, nether
 * glowstone and shroomlight, end rods in the end) is never a "placed light", and any light-giving
 * block within reach of a bright spot explains it, so lit caves never become hidden light. A
 * spot whose possible source lies in an unloaded chunk is never flagged either. The rules live
 * in {@link LightMath} and are unit tested. Structure chunks and spawn can
 * be skipped. Hits are fed into Sus Chunk Finder so they join the same base list, beams and HUD.
 */
public class LightFinder extends ChunkScanModule<LightFinder.Result> {
    static final int PLACED = 0, FURNACE = 1, HIDDEN = 2;

    record Mark(BlockPos pos, int kind) {}

    public record Result(List<Mark> marks, int placed, int furnaces, int hidden, int confirmedHidden, int minY, int maxY, int score, boolean structure, boolean fakeLight) {}

    /** Touching lit chunks merged into one place, like Sus Chunk Finder's bases. */
    public record Area(List<Long> chunks, int score, int x, int y, int z, int minY, int maxY, int placed, int furnaces, int hidden) {
        double distanceTo(Vec3d p) {
            double dx = x - p.x, dz = z - p.z;
            return Math.sqrt(dx * dx + dz * dz);
        }

        String summary() {
            StringBuilder sb = new StringBuilder();
            if (placed > 0) sb.append(placed).append(placed == 1 ? " placed light" : " placed lights");
            if (furnaces > 0) sb.append(sb.isEmpty() ? "" : ", ").append(furnaces).append(furnaces == 1 ? " lit furnace" : " lit furnaces");
            if (hidden > 0) sb.append(sb.isEmpty() ? "" : ", ").append("hidden light (").append(hidden).append(" spots)");
            return sb.toString();
        }
    }

    private final NumberSetting sensitivity = add(new NumberSetting("Sensitivity", "Higher flags dimmer, smaller lit spots (and more false alarms).", 6, 1, 10, 1));
    private final ModeSetting underground = add(new ModeSetting("Underground", "Where lights count: SkyLight = anywhere the open sky doesn't reach (caves, buried rooms, roofed builds, at any depth), BelowY = below MaxY, Anywhere = everything.", "SkyLight", "SkyLight", "BelowY", "Anywhere"));
    private final NumberSetting maxY = add(new NumberSetting("MaxY", "Height limit for Underground BelowY.", 40, -64, 320, 1));
    private final BoolSetting hidden = add(new BoolSetting("HiddenLight", "Flag bright light with no visible source (hidden or fake blocks).", true));
    private final NumberSetting hiddenLevel = add(new NumberSetting("HiddenLevel", "Block light a spot needs to count as hidden light (15 = right next to a source).", 13, 10, 15, 1));
    private final BoolSetting furnaces = add(new BoolSetting("LitFurnaces", "Count furnaces that are burning right now.", true));
    private final BoolSetting ignoreStructures = add(new BoolSetting("IgnoreStructures", "Skip chunks with strongholds, ancient cities, trial chambers and other generated lights.", true));
    private final NumberSetting spawnRadius = add(new NumberSetting("SpawnRadius", "Ignore everything this close to 0,0 (overworld blocks; nether is /8). 0 = off.", 1000, 0, 20000, 250));
    private final BoolSetting feedBases = add(new BoolSetting("FeedBaseFinder", "Add light evidence to Sus Chunk Finder's bases (when it's on).", true));
    private final BoolSetting alert = add(new BoolSetting("Alert", "Chat + sound for new lit areas (skipped when Sus Chunk Finder already pinged a base there).", true));
    private final ModeSetting height = add(new ModeSetting("DrawAt", "Draw squares below you (good on elytra) or at a fixed height. Same as Sus Chunk Finder so they layer.", "BelowMe", "BelowMe", "Fixed"));
    private final NumberSetting fixedY = add(new NumberSetting("FixedY", "Height for DrawAt Fixed.", 64, -64, 320, 1));
    private final BoolSetting depth = add(new BoolSetting("ShowDepth", "Column showing the Y range the light was found in.", true));
    private final BoolSetting beams = add(new BoolSetting("Beams", "Tall yellow beam and label on each lit area so you can fly to it.", true));
    private final BoolSetting list = add(new BoolSetting("List", "HUD list of the closest lit areas.", true));
    private final NumberSetting listRows = add(new NumberSetting("ListRows", "Lit areas shown in the HUD list.", 5, 1, 15, 1));
    private final ModeSetting sortBy = add(new ModeSetting("SortBy", "Order of the HUD list: closest first or biggest first.", "Distance", "Distance", "Score"));
    private final BoolSetting blockBoxes = add(new BoolSetting("Blocks", "Also box each light, furnace and hidden-light spot up close.", true));
    private final NumberSetting blockRange = add(new NumberSetting("BlockRange", "Max distance for the block boxes.", 128, 16, 512, 16));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Square opacity.", 80, 20, 255, 5));

    private final Set<Long> alerted = new HashSet<>();
    private List<Area> areas = List.of();
    private boolean areasDirty;
    /** Sky light at or above this means open sky (or right next to it); fainter sky light leaking into caves doesn't. */
    private static final int OPEN_SKY = 11;
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
    protected boolean keepUnloaded() {
        // keep finds after flying away, like Sus Chunk Finder
        return true;
    }

    private int threshold() {
        // sensitivity 1 -> 8 points, 6 -> 4, 10 -> 1
        return Math.max(1, Math.round(9.4f - sensitivity.getFloat() * 0.95f));
    }

    /** Set once the server's light data stops matching the blocks it sends (stripped or faked light). */
    private volatile boolean lightLooksFake;
    private final java.util.concurrent.atomic.AtomicInteger fakeChunks = new java.util.concurrent.atomic.AtomicInteger();
    private boolean warnedFake;

    @Override
    public void onDisable() {
        super.onDisable();
        alerted.clear();
        SusChunkFinder sus = sus();
        if (sus != null) sus.clearFed("light");
        pendingAlerts.clear();
        areas = List.of();
    }

    @Override
    protected dev.loot.client.setting.Setting<?>[] rescanOn() {
        return new dev.loot.client.setting.Setting<?>[]{maxY, hidden, hiddenLevel, furnaces, ignoreStructures, spawnRadius};
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
            results.clear();
            alerted.clear();
            lightRetries.clear();
            lightLooksFake = false;
            warnedFake = false;
            fakeChunks.set(0);
            pendingAlerts.clear();
            areas = List.of();
            SusChunkFinder sus = sus();
            if (sus != null) sus.clearFed("light");
        }
        // sensitivity only changes which results count, not what was found: re-filter and re-feed, no rescan
        int t = threshold();
        boolean feeding = feedBases.get();
        if (t != lastThreshold || feeding != lastFeeding) {
            lastThreshold = t;
            lastFeeding = feeding;
            SusChunkFinder sus = sus();
            if (!feeding && sus != null) sus.clearFed("light");
            else results.forEach((k, res) -> feed(k, counts(res) ? res : null));
            areasDirty = true;
        }
        super.onTick();
        if (areasDirty) {
            areasDirty = false;
            rebuildAreas();
        }
        flushAlerts();
    }

    private int lastThreshold = -1;
    private boolean lastFeeding;
    /** New areas waiting a moment to see whether Sus Chunk Finder pings a base there instead. */
    private final java.util.Map<Long, Integer> pendingAlerts = new java.util.HashMap<>();

    private void flushAlerts() {
        if (pendingAlerts.isEmpty()) return;
        SusChunkFinder sus = sus();
        var it = pendingAlerts.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (mc.player.age < e.getValue()) continue;
            it.remove();
            Area a = areaOf(e.getKey());
            if (a == null || a.chunks().stream().anyMatch(alerted::contains)) continue;
            alerted.addAll(a.chunks());
            // Sus Chunk Finder already pinged for a base here, ours would be a duplicate
            if (sus != null && sus.isEnabled() && feedBases.get() && a.chunks().stream().anyMatch(sus::alertedNear)) continue;
            if (!alert.get()) continue;
            ChatUtil.coords("Lit area (score " + a.score() + ", y " + a.minY() + " to " + a.maxY() + ") at", a.x(), a.y(), a.z());
            ChatUtil.info(a.summary());
            ChatUtil.ping();
        }
    }

    private Area areaOf(long key) {
        for (Area a : areas) if (a.chunks().contains(key)) return a;
        return null;
    }

    @Override
    protected void onStored(long key, Result old, Result now) {
        if (!java.util.Objects.equals(old, now)) areasDirty = true;
    }

    /** Groups counting chunks within 2 of each other, the same way Sus Chunk Finder builds bases. */
    private void rebuildAreas() {
        Set<Long> seen = new HashSet<>();
        List<Area> out = new ArrayList<>();
        for (var start : results.entrySet()) {
            if (!counts(start.getValue()) || !seen.add(start.getKey())) continue;
            List<Long> group = new ArrayList<>();
            Deque<Long> stack = new ArrayDeque<>();
            stack.push(start.getKey());
            while (!stack.isEmpty()) {
                long k = stack.pop();
                group.add(k);
                int cx = ChunkPos.getPackedX(k), cz = ChunkPos.getPackedZ(k);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) {
                        long n = ChunkPos.toLong(cx + dx, cz + dz);
                        Result nr = results.get(n);
                        if (nr != null && counts(nr) && seen.add(n)) stack.push(n);
                    }
            }
            int score = 0, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, placed = 0, lit = 0, hiddenSpots = 0;
            long sx = 0, sy = 0, sz = 0;
            int marks = 0;
            for (long k : group) {
                Result r = results.get(k);
                score += r.score();
                minY = Math.min(minY, r.minY());
                maxY = Math.max(maxY, r.maxY());
                placed += r.placed();
                lit += r.furnaces();
                hiddenSpots += r.confirmedHidden();
                for (Mark m : r.marks()) {
                    if (m.kind() == HIDDEN && r.confirmedHidden() == 0) continue;
                    sx += m.pos().getX();
                    sy += m.pos().getY();
                    sz += m.pos().getZ();
                    marks++;
                }
            }
            if (marks == 0) continue;
            out.add(new Area(group, score, (int) (sx / marks), (int) (sy / marks), (int) (sz / marks), minY, maxY, placed, lit, hiddenSpots));
        }
        out.sort(Comparator.comparingInt(Area::score).reversed());
        areas = out;
        if (!alert.get()) {
            for (Area a : out) alerted.addAll(a.chunks());
            return;
        }
        SusChunkFinder sus = sus();
        boolean wait = feedBases.get() && sus != null && sus.isEnabled();
        for (Area a : out) {
            if (a.chunks().stream().anyMatch(alerted::contains)) {
                alerted.addAll(a.chunks()); // grew: no second ping
                continue;
            }
            // with Sus on, give it a couple of seconds to score these chunks; we only ping if it doesn't
            pendingAlerts.putIfAbsent(a.chunks().get(0), mc.player.age + (wait ? 50 : 0));
        }
    }

    public List<Area> sortedAreas() {
        Vec3d me = mc.player == null ? Vec3d.ZERO : mc.player.getEntityPos();
        Comparator<Area> order = sortBy.is("Score")
            ? Comparator.comparingInt(Area::score).reversed()
            : Comparator.comparingDouble(a -> a.distanceTo(me));
        return areas.stream().sorted(order).toList();
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
        int candidates = 0, mismatched = 0;
        LightMath.Grid grid = new WorldGrid(world, blockLight);

        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null) continue;
            // an all-air section can't hold a light but can still be lit (a big dug-out room)
            boolean airOnly = section.isEmpty();
            int sy = chunk.sectionIndexToCoord(i);
            int by = sy << 4;
            if (by > limit) break;
            ChunkNibbleArray sky = useSky ? skyLight.getLightSection(ChunkSectionPos.from(cp.x, sy, cp.z)) : null;
            ChunkNibbleArray light = blockLight.getLightSection(ChunkSectionPos.from(cp.x, sy, cp.z));
            boolean haveLight = light != null && !light.isUninitialized();

            // placed lights and lit furnaces, straight from the block palette
            if (!airOnly && section.hasAny(s -> classify(s, nether, end) >= 0)) {
                for (int y = 0; y < 16; y++) {
                    if (by + y > limit) break;
                    for (int z = 0; z < 16; z++)
                        for (int x = 0; x < 16; x++) {
                            BlockState s = section.getBlockState(x, y, z);
                            int kind = classify(s, nether, end);
                            if (kind < 0 || kind == FURNACE && !furnaces.get()) continue;
                            // a bright light whose own cell is dark means the server isn't sending real light
                            if (haveLight && s.getLuminance() >= 10 && light.get(x, y, z) < s.getLuminance() - 2) mismatched++;
                            // open sky reaches it. Faint sky light leaking down a ravine or cave mouth doesn't count as open
                            if (useSky && sky != null && !sky.isUninitialized() && maxSky(sky, x, y, z) >= OPEN_SKY) continue;
                            // belt and braces for missing sky data: nothing at or above the surface counts
                            if (useSky && by + y >= chunk.sampleHeightmap(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z) - 1) continue;
                            marks.add(new Mark(new BlockPos(bx + x, by + y, bz + z), kind));
                            if (kind == PLACED) placed++;
                            else lit++;
                            minY = Math.min(minY, by + y);
                            maxYFound = Math.max(maxYFound, by + y);
                        }
                }
            }

            if (!hidden.get()) continue;
            if (!haveLight) {
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
                        if (!airOnly && !section.getBlockState(x, y, z).isAir()) continue;
                        if (useSky && sky != null && !sky.isUninitialized() && sky.get(x, y, z) >= OPEN_SKY) continue;
                        int wx = bx + x, wy = by + y, wz = bz + z;
                        if (nearMark(marks, wx, wy, wz, 4)) continue;
                        // any light-giving block in reach (lava, glow berries, lichen, fire...) explains it
                        if (LightMath.explained(grid, wx, wy, wz, level)) continue;
                        candidates++;
                        // real light falls off by exactly one per block; random or flat fake light doesn't
                        if (!LightMath.realGradient(grid, wx, wy, wz, level)) continue;
                        if (hiddenCount < 16) marks.add(new Mark(new BlockPos(wx, wy, wz), HIDDEN));
                        hiddenCount++;
                        minY = Math.min(minY, wy);
                        maxYFound = Math.max(maxYFound, wy);
                    }
            }
        }
        if (lightMissing && !anyLight && lightRetries.merge(cp.toLong(), 1, Integer::sum) <= 5) markDirty(cp.x, cp.z);
        boolean fake = mismatched >= 2 || candidates > 200;
        if (fake) {
            // don't trust light-based evidence from this chunk, keep what the blocks themselves show
            // a single chunk can just be mid-update; a few of them means the server is doing it on purpose
            if (fakeChunks.incrementAndGet() >= 3) lightLooksFake = true;
            marks.removeIf(m -> m.kind() == HIDDEN);
            hiddenCount = 0;
        }
        if (marks.isEmpty()) return null;
        boolean structure = ignoreStructures.get() && Structures.detect(chunk) != null;
        return new Result(marks, placed, lit, hiddenCount, 0, minY, maxYFound, 0, structure, fake);
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

    /** The live world seen through {@link LightMath.Grid}; unloaded chunks report as not loaded. */
    private record WorldGrid(World world, ChunkLightingView light, BlockPos.Mutable pos) implements LightMath.Grid {
        WorldGrid(World world, ChunkLightingView light) {
            this(world, light, new BlockPos.Mutable());
        }

        @Override
        public boolean loaded(int x, int y, int z) {
            return world.getChunkManager().isChunkLoaded(x >> 4, z >> 4);
        }

        @Override
        public int luminance(int x, int y, int z) {
            return world.getBlockState(pos.set(x, y, z)).getLuminance();
        }

        @Override
        public int light(int x, int y, int z) {
            return light.getLightLevel(pos.set(x, y, z));
        }

        @Override
        public boolean opaque(int x, int y, int z) {
            return world.getBlockState(pos.set(x, y, z)).isOpaqueFullCube();
        }
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
        Result out = new Result(r.marks(), r.placed(), r.furnaces(), r.hidden(), confirmed, r.minY(), r.maxY(), score, false, r.fakeLight());
        feed(pos.toLong(), score >= threshold() ? out : null);
        if (lightLooksFake && !warnedFake) {
            warnedFake = true;
            ChatUtil.info("Light Finder: this server's light data doesn't match its blocks (anti light-finder?). Hidden light is off for those chunks; placed lights and furnaces still work.");
        }
        // keep everything (only what passes the threshold is drawn) so a sensitivity change works on
        // chunks you already left, and so the confirming hidden-light scan can see the first one
        return r.marks().isEmpty() && r.hidden() == 0 ? null : out;
    }

    private void feed(long key, Result r) {
        SusChunkFinder sus = sus();
        if (sus == null || !feedBases.get()) return;
        java.util.Map<String, Integer> c = new java.util.TreeMap<>();
        if (r != null && r.confirmedHidden() > 0) c.put("hidden light", r.confirmedHidden());
        if (r != null && r.furnaces() > 0) c.put("lit furnace", r.furnaces());
        sus.feed("light", key, c.isEmpty() ? null : new SusChunkFinder.Evidence(c, r.minY(), r.maxY()));
    }

    private boolean ignored(ChunkPos pos) {
        double r = spawnRadius.get();
        if (r <= 0) return false;
        if (mc.world.getRegistryKey() == World.NETHER) r /= 8;
        double cx = pos.getCenterX(), cz = pos.getCenterZ();
        return cx * cx + cz * cz < r * r;
    }

    private boolean counts(Result r) {
        return r.score() >= threshold();
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        SusChunkFinder sus = sus();
        boolean layered = sus != null && sus.isEnabled();
        double y = height.is("Fixed") ? fixedY.get() : Math.floor(mc.player.getLerpedPos(tickDelta).y) - 12;
        // with Sus Chunk Finder on, draw a touch higher and inset so its red reads as a ring around our yellow
        double drawY = layered ? y + 0.02 : y;
        double inset = layered ? 3 : 0;
        int strong = threshold() * 3;
        java.util.function.LongPredicate flagged = k -> {
            Result res = results.get(k);
            return res != null && counts(res);
        };
        double boxSq = blockRange.get() * blockRange.get();
        for (var e : results.entrySet()) {
            Result res = e.getValue();
            if (!counts(res)) continue;
            long key = e.getKey();
            int alpha = (int) Math.min(255, fill.get() * (res.score() >= strong ? 1.3 : 1.0));
            ChunkHighlight.region(r, key, flagged, drawY, YELLOW, alpha, inset);
            int x1 = ChunkPos.getPackedX(key) << 4, z1 = ChunkPos.getPackedZ(key) << 4;
            if (depth.get()) {
                // same column as Sus Chunk Finder; beside its column when both are on
                int c = layered ? 3 : 6, w = layered ? 2 : 4;
                r.box(new Box(x1 + c, res.minY(), z1 + c, x1 + c + w, res.maxY() + 1, z1 + c + w), 0xFF000000 | YELLOW, 40);
                double mid = x1 + c + w / 2.0, midZ = z1 + c + w / 2.0;
                r.line(mid, drawY, midZ, mid, res.maxY() + 1, midZ, 0x99000000 | YELLOW);
            }
            if (!blockBoxes.get()) continue;
            for (Mark m : res.marks()) {
                if (m.pos().getSquaredDistance(mc.player.getEntityPos()) > boxSq) continue;
                switch (m.kind()) {
                    case PLACED -> r.box(new Box(m.pos()), 0xFFFFD27A, 50);
                    case FURNACE -> r.box(new Box(m.pos()), 0xFFFF9A3C, 50);
                    default -> {
                        if (res.confirmedHidden() > 0) r.box(new Box(m.pos()).contract(0.2), 0xFFFF4DFF, 60);
                    }
                }
            }
        }
        if (beams.get()) {
            int top = mc.world.getBottomY() + mc.world.getHeight();
            for (Area a : areas) {
                // a block off Sus Chunk Finder's beam so both stay visible on the same base
                double bx = a.x() + (layered ? 1.5 : 0.5), bz = a.z() + 0.5;
                r.line(bx, a.minY(), bz, bx, top, bz, 0xCC000000 | YELLOW);
                r.line(bx - 0.1, a.minY(), bz, bx - 0.1, top, bz, 0xCC000000 | YELLOW);
            }
        }
    }

    private static final int YELLOW = 0xFFD24D;

    @Override
    public void onRenderHud(DrawContext ctx, float tickDelta) {
        if (mc.options.hudHidden || areas.isEmpty()) return;
        Vec3d me = mc.player.getLerpedPos(tickDelta);
        SusChunkFinder sus = sus();
        boolean layered = sus != null && sus.isEnabled();
        if (beams.get()) {
            for (Area a : areas) {
                double labelY = Math.min(Math.max(a.maxY() + 3, me.y), me.y + 40);
                Vec3d s = Projection.toScreen(new Vec3d(a.x() + 0.5, labelY, a.z() + 0.5));
                if (s == null) continue;
                String text = "light " + a.score() + "  " + Math.round(a.distanceTo(me)) + "m  y" + a.y();
                int w = UI.width(text);
                // under Sus Chunk Finder's label when both are on
                int x = (int) s.x - w / 2 - 4, y = (int) s.y - 6 + (layered ? 14 : 0);
                UI.roundRect(ctx, x, y, x + w + 8, y + 12, 4, Theme.SURFACE);
                UI.drawText(ctx, text, x + 4, y + 2, 0xFF000000 | YELLOW);
            }
        }
        if (list.get()) {
            List<Area> sorted = sortedAreas();
            List<Area> near = sorted.stream().limit(listRows.getInt()).toList();
            int w = 160, x = 4;
            int y = layered ? sus.hudBottom() + 4 : 90;
            UI.roundRect(ctx, x, y, x + w, y + 16 + near.size() * 10, 5, Theme.SURFACE);
            UI.drawBold(ctx, "Lit areas", x + 6, y + 4, 0xFF000000 | YELLOW);
            UI.drawTextRight(ctx, String.valueOf(sorted.size()), x + w - 6, y + 4, 0xFF000000 | YELLOW);
            for (int i = 0; i < near.size(); i++) {
                Area a = near.get(i);
                String left = (i + 1) + ". " + a.x() + ", " + a.y() + ", " + a.z();
                String right = a.score() + "  " + Math.round(a.distanceTo(me)) + "m " + direction(me, a);
                UI.drawText(ctx, left, x + 6, y + 15 + i * 10, Theme.TEXT_MUTED);
                UI.drawTextRight(ctx, right, x + w - 6, y + 15 + i * 10, Theme.TEXT);
            }
        }
    }

    private static String direction(Vec3d me, Area a) {
        double angle = Math.toDegrees(Math.atan2(a.z() - me.z, a.x() - me.x)) - 90;
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return names[Math.floorMod((int) Math.round(MathHelper.wrapDegrees(angle) / 45.0), 8)];
    }

    public List<String> describeAreas() {
        List<String> out = new ArrayList<>();
        Vec3d me = mc.player == null ? Vec3d.ZERO : mc.player.getEntityPos();
        int i = 1;
        for (Area a : sortedAreas())
            out.add("#" + i++ + "  " + a.x() + ", " + a.y() + ", " + a.z() + "  y " + a.minY() + ".." + a.maxY() + "  score " + a.score() + "  "
                + Math.round(a.distanceTo(me)) + "m " + direction(me, a) + "  " + a.summary());
        return out;
    }

    @Override
    public String getInfo() {
        int n = areas.size();
        return lightLooksFake ? n + " (fake light)" : String.valueOf(n);
    }
}
