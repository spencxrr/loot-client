package dev.loot.client.module.basefinding;

import dev.loot.client.gui.Theme;
import dev.loot.client.gui.UI;
import dev.loot.client.module.Category;
import dev.loot.client.render.Projection;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.ModeSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.ChatUtil;
import dev.loot.client.util.WorldUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractPlantStemBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The main base finder. Every loaded chunk gets a score from the evidence in it, top of the world
 * to bedrock:
 *  - things players build: storage, shulkers, ender chests, beacons, anvils, portals, item frames...
 *  - blocks world generation never places (concrete, glazed terracotta, stained glass...)
 *  - player-placed light underground where caves have none
 *  - plant growth that only happens while someone keeps the chunk loaded for a long time
 * Generated structures (villages, strongholds, ancient cities, trial chambers, bastions...) are
 * detected from their blocks and mobs, and loot-type evidence in and around them is ignored.
 *
 * Flagged chunks that sit close together are merged into one "base" with a combined score, shown
 * as a beam with a label, listed on the HUD by distance, logged to a file per server, and
 * remembered across sessions so you don't get pinged twice for the same place.
 */
public class SusChunkFinder extends ChunkScanModule<SusChunkFinder.Hit> {
    /** {@code raw} is the chunk's own evidence (before fed evidence), kept so the hit can be re-scored offline. */
    public record Hit(int score, int minY, int maxY, Map<String, Integer> counts, boolean nearStructure, Raw raw) {
        String summary() {
            StringBuilder sb = new StringBuilder();
            counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(weight(b.getKey()) * b.getValue(), weight(a.getKey()) * a.getValue()))
                .forEach(e -> sb.append(sb.isEmpty() ? "" : ", ").append(e.getKey().startsWith("~") ? e.getKey() : e.getValue() + " " + e.getKey()));
            return sb.toString();
        }
    }

    /** What the worker thread hands back: raw evidence, scored on the main thread. */
    public record Raw(Map<String, Integer> counts, String structure, int minY, int maxY, int minutes) {}

    /**
     * Evidence another finder hands over for one chunk (things this module can't see itself, like
     * hidden light or dug tunnels). Kinds are scored with {@link #weight}.
     */
    public record Evidence(Map<String, Integer> counts, int minY, int maxY) {}

    public record Base(List<Long> chunks, int score, int x, int z, int minY, int maxY, String summary, boolean known) {
        double distanceTo(Vec3d p) {
            double dx = x - p.x, dz = z - p.z;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    private final NumberSetting sensitivity = add(new NumberSetting("Sensitivity", "Higher flags more chunks (and more false alarms).", 6, 1, 10, 1));
    private final BoolSetting structures = add(new BoolSetting("IgnoreStructures", "Ignore loot in villages, strongholds, trial chambers, ancient cities and other generated structures.", true));
    private final NumberSetting spawnRadius = add(new NumberSetting("SpawnRadius", "Ignore everything this close to 0,0 (overworld blocks; nether is /8). 0 = off.", 1000, 0, 20000, 250));
    private final BoolSetting hideOwn = add(new BoolSetting("HideOwn", "Don't flag chunks you placed blocks in this session.", true));
    private final ModeSetting height = add(new ModeSetting("DrawAt", "Draw squares below you (good on elytra) or at a fixed height.", "BelowMe", "BelowMe", "Fixed"));
    private final NumberSetting fixedY = add(new NumberSetting("FixedY", "Height for DrawAt Fixed.", 64, -64, 320, 1));
    private final BoolSetting depth = add(new BoolSetting("ShowDepth", "Column showing the Y range the evidence was found in.", true));
    private final BoolSetting beams = add(new BoolSetting("Beams", "Tall beam and label on each base so you can fly to it.", true));
    private final BoolSetting list = add(new BoolSetting("List", "HUD list of the closest bases.", true));
    private final NumberSetting listRows = add(new NumberSetting("ListRows", "Bases shown in the HUD list.", 5, 1, 15, 1));
    private final ModeSetting sortBy = add(new ModeSetting("SortBy", "Order of the HUD list and .bases: closest first or biggest first.", "Distance", "Distance", "Score"));
    private final NumberSetting minBaseScore = add(new NumberSetting("MinBaseScore", "Combined score a group of chunks needs to show as a base (0 = any flagged chunk).", 0, 0, 100, 1));
    private final BoolSetting alert = add(new BoolSetting("Alert", "Chat + sound for new bases.", true));
    private final BoolSetting alertKnown = add(new BoolSetting("AlertKnown", "Also alert for bases already in the log from earlier sessions.", false));
    private final BoolSetting log = add(new BoolSetting("LogFile", "Append finds to .minecraft/lootclient/bases-<server>.txt.", true));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Square opacity.", 90, 20, 255, 5));

    private ClientWorld lastWorld;
    /** Chunks that contain a structure marker, with the structure's name. */
    private final Map<Long, String> structureChunks = new HashMap<>();
    /** Chunks you placed blocks in, filled from the network thread. */
    private final Set<Long> ownChunks = ConcurrentHashMap.newKeySet();
    /** Chunk keys logged in earlier sessions for this server + dimension. */
    private final Set<Long> known = new HashSet<>();
    private final Set<Long> alerted = new HashSet<>();
    private final Set<Long> alertedStrong = new HashSet<>();
    /** source module -> chunk -> evidence it found there. */
    private final Map<String, Map<Long, Evidence>> fed = new ConcurrentHashMap<>();
    private List<Base> bases = List.of();
    private boolean basesDirty;

    public SusChunkFinder() {
        super("Sus Chunk Finder", "Finds player bases and stashes at any depth.", Category.BASEFINDING);
    }

    private int threshold() {
        // sensitivity 1 -> 24 points needed, 6 -> 13, 10 -> 4
        return Math.max(3, Math.round(26.2f - sensitivity.getFloat() * 2.2f));
    }

    @Override
    protected int refreshRadius() {
        return 2;
    }

    @Override
    protected boolean keepUnloaded() {
        // keep hits after the chunk unloads so the map of finds survives flying away
        return true;
    }

    @Override
    public void onEnable() {
        lastWorld = null;
        basesDirty = true;
        super.onEnable();
    }

    @Override
    public void onTick() {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            clearHits();
            loadKnown();
        }
        super.onTick();
        if (basesDirty) {
            basesDirty = false;
            rebuildBases();
        }
    }

    public void clearHits() {
        results.clear();
        structureChunks.clear();
        alerted.clear();
        alertedStrong.clear();
        bases = List.of();
    }

    /** Another finder's evidence for a chunk (null or empty clears it); the chunk gets re-scored. */
    public void feed(String source, long key, Evidence ev) {
        if (ev != null && ev.counts().isEmpty()) ev = null;
        Map<Long, Evidence> map = fed.computeIfAbsent(source, s -> new ConcurrentHashMap<>());
        Evidence old = ev == null ? map.remove(key) : map.put(key, ev);
        if (!Objects.equals(old, ev)) rescore(key);
    }

    /** Drops everything a finder fed (it was turned off); affected chunks get re-scored. */
    public void clearFed(String source) {
        Map<Long, Evidence> map = fed.remove(source);
        if (map != null) map.keySet().forEach(this::rescore);
    }

    private void rescore(long key) {
        if (!isEnabled() || !inGame()) return;
        if (mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key)) != null) enqueue(key);
        else rescoreOffline(key);
    }

    /** Unloaded chunk: re-score from what was stored. Returns false if it no longer counts. */
    private boolean rescoreOffline(long key) {
        Hit h = results.get(key);
        if (h == null || h.raw() == null) return h != null;
        Hit now = ignored(new ChunkPos(key)) ? null : score(key, h.raw(), structures.get() && h.nearStructure());
        if (now == null) results.remove(key);
        else results.put(key, now);
        if (!Objects.equals(h, now)) basesDirty = true;
        return now != null;
    }

    @Override
    protected dev.loot.client.setting.Setting<?>[] rescanOn() {
        return new dev.loot.client.setting.Setting<?>[]{sensitivity, structures, spawnRadius, hideOwn};
    }

    @Override
    protected void onScanSettingsChanged() {
        // re-score what's stored (covers chunks you already flew away from), then rescan what's loaded
        for (Long k : new ArrayList<>(results.keySet())) rescoreOffline(k);
        rescanAll();
        basesDirty = true;
    }

    /** Whether a base alert already went out for this chunk or one right next to it. */
    public boolean alertedNear(long key) {
        int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (alerted.contains(ChunkPos.toLong(cx + dx, cz + dz))) return true;
        return false;
    }

    private boolean hasFed(long key) {
        for (Map<Long, Evidence> m : fed.values()) if (m.containsKey(key)) return true;
        return false;
    }

    @Override
    protected void onStored(long key, Hit old, Hit now) {
        if (!Objects.equals(old, now)) basesDirty = true;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (hideOwn.get() && packet instanceof PlayerInteractBlockC2SPacket p) {
            BlockPos pos = p.getBlockHitResult().getBlockPos();
            ownChunks.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
            BlockPos placed = pos.offset(p.getBlockHitResult().getSide());
            ownChunks.add(ChunkPos.toLong(placed.getX() >> 4, placed.getZ() >> 4));
        }
        return false;
    }

    // ---- evidence ----

    private static String kind(BlockEntity be) {
        if (be instanceof ShulkerBoxBlockEntity) return "shulker";
        if (be instanceof EnderChestBlockEntity) return "ender chest";
        if (be instanceof BeaconBlockEntity) return "beacon";
        if (be instanceof ConduitBlockEntity) return "conduit";
        if (be instanceof EnchantingTableBlockEntity) return "enchanting table";
        if (be instanceof CrafterBlockEntity) return "crafter";
        if (be instanceof HopperBlockEntity) return "hopper";
        if (be instanceof BannerBlockEntity) return "banner";
        if (be instanceof SkullBlockEntity) return "skull";
        if (be instanceof JukeboxBlockEntity) return "jukebox";
        if (be instanceof SignBlockEntity) return "sign";
        if (be instanceof ChestBlockEntity) return "chest";
        if (be instanceof BarrelBlockEntity) return "barrel";
        if (be instanceof AbstractFurnaceBlockEntity) return "furnace";
        if (be instanceof BedBlockEntity) return "bed";
        if (be instanceof BrewingStandBlockEntity) return "brewing stand";
        if (be instanceof DispenserBlockEntity) return "dispenser";
        if (be instanceof ComparatorBlockEntity) return "comparator";
        return null;
    }

    static int weight(String kind) {
        return switch (kind) {
            case "beacon" -> 10;
            case "stash" -> 8;
            case "shulker" -> 6;
            case "conduit", "netherite block" -> 5;
            case "ender chest", "respawn anchor", "diamond block", "hidden light" -> 4;
            case "tunnel" -> 2;
            case "shaft" -> 1;
            case "enchanting table", "crafter", "anvil", "nether portal" -> 3;
            case "hopper", "banner", "skull", "lit furnace", "jukebox", "armor stand", "item frame", "emerald block" -> 2;
            case "sign", "chest", "barrel", "furnace", "bed", "brewing stand", "dispenser", "comparator", "crafting table",
                 "light", "obsidian", "build blocks", "underground build" -> 1;
            default -> 0;
        };
    }

    /** Evidence that generated structures also produce, ignored in and around them. */
    private static boolean lootish(String kind) {
        return switch (kind) {
            case "chest", "barrel", "dispenser", "bed", "furnace", "brewing stand", "crafting table", "light", "banner",
                 "anvil", "obsidian", "sign", "skull", "item frame", "build blocks", "underground build", "stash", "hopper", "hidden light", "tunnel", "shaft" -> true;
            default -> false;
        };
    }

    private static volatile Set<Block> buildBlocks;

    /** Blocks world generation never places, only players. */
    private static boolean buildBlock(Block b) {
        if (buildBlocks == null) {
            Set<Block> set = new HashSet<>();
            for (Block block : Registries.BLOCK) {
                String id = Registries.BLOCK.getId(block).getPath();
                if (id.endsWith("_concrete") || id.endsWith("_glazed_terracotta") || id.endsWith("stained_glass")
                    || id.endsWith("stained_glass_pane") || id.endsWith("_wool") || id.endsWith("_carpet") && !id.equals("moss_carpet")
                    || id.equals("glass") || id.equals("tinted_glass") || id.endsWith("quartz_block") || id.equals("quartz_bricks")
                    || id.equals("lapis_block") || id.equals("redstone_block") || id.equals("iron_block") || id.equals("honeycomb_block")
                    || id.equals("slime_block") || id.equals("honey_block") || id.equals("target") || id.equals("note_block")
                    || id.equals("scaffolding") || id.equals("observer") || id.equals("piston") || id.equals("sticky_piston")) set.add(block);
            }
            buildBlocks = set;
        }
        return buildBlocks.contains(b);
    }

    private static volatile Set<Block> craftedBlocks;

    /**
     * Crafted building blocks (bricks, polished stone, planks, stairs, doors...). Common on the
     * surface (villages) but underground they mean a player, except the ones strongholds,
     * mineshafts, trial chambers, ancient cities and fortresses are made of, which are left out.
     */
    private static boolean craftedBlock(Block b) {
        if (craftedBlocks == null) {
            Set<Block> set = new HashSet<>();
            for (Block block : Registries.BLOCK) {
                String id = Registries.BLOCK.getId(block).getPath();
                boolean crafted = id.startsWith("polished_") || id.endsWith("_bricks") || id.endsWith("_tiles") || id.startsWith("smooth_")
                    || id.startsWith("cut_") || id.endsWith("_planks") || id.startsWith("stripped_") || id.endsWith("_slab")
                    || id.endsWith("_stairs") || id.endsWith("_door") || id.endsWith("_trapdoor") || id.endsWith("_fence_gate")
                    || id.equals("chiseled_bookshelf");
                boolean generated = id.contains("stone_brick") || id.contains("nether_brick") || id.contains("tuff") || id.contains("deepslate")
                    || id.contains("prismarine") || id.contains("purpur") || id.contains("blackstone") || id.contains("mud_brick")
                    || id.contains("sandstone") || id.equals("oak_planks") || id.contains("spruce") || id.contains("dark_oak")
                    || id.contains("oak_door") || id.contains("iron_door") || id.contains("smooth_stone");
                if (crafted && !generated) set.add(block);
            }
            craftedBlocks = set;
        }
        return craftedBlocks.contains(b);
    }

    private static boolean isLight(Block b) {
        return b == Blocks.TORCH || b == Blocks.WALL_TORCH || b == Blocks.LANTERN || b == Blocks.SOUL_TORCH
            || b == Blocks.SOUL_WALL_TORCH || b == Blocks.SOUL_LANTERN || b == Blocks.SEA_LANTERN || b == Blocks.REDSTONE_LAMP
            || b == Blocks.JACK_O_LANTERN || b == Blocks.OCHRE_FROGLIGHT || b == Blocks.VERDANT_FROGLIGHT || b == Blocks.PEARLESCENT_FROGLIGHT;
    }

    private static String blockKind(Block b, int y, boolean overworld, boolean end) {
        if (b == Blocks.NETHERITE_BLOCK) return "netherite block";
        if (b == Blocks.DIAMOND_BLOCK) return "diamond block";
        if (b == Blocks.EMERALD_BLOCK) return "emerald block";
        if (b == Blocks.RESPAWN_ANCHOR) return "respawn anchor";
        if (b instanceof AnvilBlock) return "anvil";
        if (b == Blocks.NETHER_PORTAL) return "nether portal";
        if (buildBlock(b)) return "build blocks";
        if ((overworld && y < 40 || !overworld && !end) && craftedBlock(b)) return "underground build";
        // caves have no torches, the nether and end have none at all
        if (isLight(b) && (!overworld || y < 40)) return "light";
        if (overworld && y < 40 && b == Blocks.CRAFTING_TABLE) return "crafting table";
        // lava pools make some obsidian, the end is full of it
        if (!end && b == Blocks.OBSIDIAN && (!overworld || y < 40)) return "obsidian";
        return null;
    }

    private static boolean interesting(BlockState s) {
        Block b = s.getBlock();
        return b == Blocks.NETHERITE_BLOCK || b == Blocks.DIAMOND_BLOCK || b == Blocks.EMERALD_BLOCK || b == Blocks.RESPAWN_ANCHOR
            || b instanceof AnvilBlock || b == Blocks.NETHER_PORTAL || b == Blocks.CRAFTING_TABLE || b == Blocks.OBSIDIAN
            || isLight(b) || buildBlock(b) || craftedBlock(b) || b == Blocks.KELP || b == Blocks.SUGAR_CANE || b == Blocks.CACTUS;
    }

    @Override
    protected Hit scan(WorldChunk chunk) {
        // worker thread: gather raw evidence; Hit is a carrier here and gets re-scored in postScan
        Raw raw = scanRaw(chunk);
        return raw == null ? null : new Hit(0, raw.minY(), raw.maxY(), raw.counts(), false, raw);
    }

    private Raw scanRaw(WorldChunk chunk) {
        World world = chunk.getWorld();
        boolean overworld = world.getRegistryKey() == World.OVERWORLD;
        boolean end = world.getRegistryKey() == World.END;
        Map<String, Integer> counts = new TreeMap<>();
        String structure = structures.get() ? Structures.detect(chunk) : null;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
            if (be instanceof MobSpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity || be instanceof VaultBlockEntity
                || be instanceof BellBlockEntity) {
                if (structure == null && structures.get()) structure = be instanceof BellBlockEntity ? "village" : "dungeon";
                continue;
            }
            String k = kind(be);
            if (k == null) continue;
            counts.merge(k, 1, Integer::sum);
            int y = be.getPos().getY();
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        int growthTicks = 0, grownKelp = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty() || !section.hasAny(SusChunkFinder::interesting)) continue;
            int by = chunk.sectionIndexToCoord(i) << 4;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        BlockState s = section.getBlockState(x, y, z);
                        if (!interesting(s)) continue;
                        if (s.isOf(Blocks.SUGAR_CANE) || s.isOf(Blocks.CACTUS)) {
                            growthTicks = Math.max(growthTicks, s.get(Properties.AGE_15));
                            continue;
                        }
                        if (s.isOf(Blocks.KELP)) {
                            if (s.get(AbstractPlantStemBlock.AGE) >= 25) grownKelp++;
                            continue;
                        }
                        String k = blockKind(s.getBlock(), by + y, overworld, end);
                        if (k == null) continue;
                        counts.merge(k, 1, Integer::sum);
                        minY = Math.min(minY, by + y);
                        maxY = Math.max(maxY, by + y);
                    }
        }

        // ~68s of loaded time per growth tick; kelp at max age needs a few minutes at least
        int minutes = (int) Math.round(growthTicks * 1.14);
        if (grownKelp >= 3) minutes = Math.max(minutes, 16);
        if (counts.isEmpty() && minutes < 5 && structure == null && !hasFed(chunk.getPos().toLong())) return null;
        return new Raw(counts, structure, minY, maxY, minutes);
    }

    @Override
    protected Hit postScan(WorldChunk chunk, Hit carrier) {
        Raw raw = carrier.raw();
        if (raw == null) return null;
        long key = chunk.getPos().toLong();
        Map<String, Integer> counts = new TreeMap<>(raw.counts());
        int minY = raw.minY(), maxY = raw.maxY();
        String structure = raw.structure();

        // entities aren't safe to read off-thread
        ChunkPos pos = chunk.getPos();
        Box area = new Box(pos.getStartX(), chunk.getBottomY(), pos.getStartZ(), pos.getEndX() + 1, chunk.getBottomY() + chunk.getHeight(), pos.getEndZ() + 1);
        for (Entity e : mc.world.getOtherEntities(null, area)) {
            if (structures.get() && structure == null) {
                if (e instanceof VillagerEntity || e instanceof IronGolemEntity || e instanceof WanderingTraderEntity) structure = "village";
                else if (e instanceof ChestMinecartEntity) structure = "mineshaft";
            }
            String k = e instanceof ItemFrameEntity ? "item frame" : e instanceof ArmorStandEntity ? "armor stand" : null;
            if (k == null) continue;
            counts.merge(k, 1, Integer::sum);
            minY = Math.min(minY, e.getBlockY());
            maxY = Math.max(maxY, e.getBlockY());
        }

        // a structure found here also taints its neighbours; re-check them once
        String old = structure == null ? structureChunks.remove(key) : structureChunks.put(key, structure);
        if (!Objects.equals(old, structure)) {
            int s = Math.max(Structures.spread(old), Structures.spread(structure));
            for (int dx = -s; dx <= s; dx++)
                for (int dz = -s; dz <= s; dz++)
                    if ((dx != 0 || dz != 0) && results.containsKey(ChunkPos.toLong(pos.x + dx, pos.z + dz))) enqueue(ChunkPos.toLong(pos.x + dx, pos.z + dz));
        }

        if (ignored(pos)) return null;
        boolean nearStructure = structures.get() && nearStructure(pos);
        return score(key, new Raw(counts, structure, minY, maxY, raw.minutes()), nearStructure);
    }

    /**
     * Scores a chunk's own evidence plus whatever other finders fed for it. The chunk's own part is
     * kept in the hit, so it can be re-scored after the chunk unloads (sensitivity or fed evidence changed).
     */
    private Hit score(long key, Raw own, boolean nearStructure) {
        Map<String, Integer> counts = new TreeMap<>(own.counts());
        int minY = own.minY(), maxY = own.maxY();
        for (Map<Long, Evidence> m : fed.values()) {
            Evidence ev = m.get(key);
            if (ev == null) continue;
            ev.counts().forEach((k, n) -> counts.merge(k, n, Math::max));
            minY = Math.min(minY, ev.minY());
            maxY = Math.max(maxY, ev.maxY());
        }

        int storage = counts.getOrDefault("chest", 0) + counts.getOrDefault("barrel", 0) + counts.getOrDefault("shulker", 0) * 2;
        if (storage >= 8) counts.put("stash", 1);

        int score = 0;
        for (var entry : counts.entrySet()) {
            String k = entry.getKey();
            int w = weight(k);
            if (nearStructure && lootish(k)) w = 0;
            int n = entry.getValue();
            switch (k) {
                case "light" -> n = Math.min(n, 8);
                case "obsidian" -> n = Math.min(n, 4);
                case "nether portal" -> n = Math.min(2, (n + 5) / 6);
                case "build blocks" -> n = Math.min(6, n / 12);
                case "underground build" -> n = Math.min(6, n / 16);
                case "sign" -> n = Math.min(n, 4);
                case "hidden light", "lit furnace" -> n = Math.min(n, 4);
                case "tunnel", "shaft" -> n = Math.min(n, 3);
                // diminishing returns: the 40th chest says less than the 4th
                default -> n = n <= 6 ? n : 6 + (int) Math.sqrt(n - 6);
            }
            score += w * n;
        }
        if (own.minutes() >= 5) {
            score += Math.min(10, own.minutes() / 3);
            counts.put("~" + own.minutes() + " min loaded", 1);
        }
        counts.remove("stash");
        if (storage >= 8) counts.put("stash", 1);

        if (score < threshold()) return null;
        if (minY == Integer.MAX_VALUE) {
            minY = 50;
            maxY = 70;
        }
        return new Hit(score, minY, maxY, counts, nearStructure, own);
    }

    private boolean ignored(ChunkPos pos) {
        if (hideOwn.get() && ownChunks.contains(pos.toLong())) return true;
        double r = spawnRadius.get();
        if (r <= 0) return false;
        if (mc.world.getRegistryKey() == World.NETHER) r /= 8;
        double cx = pos.getCenterX(), cz = pos.getCenterZ();
        return cx * cx + cz * cz < r * r;
    }

    private boolean nearStructure(ChunkPos pos) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                String s = structureChunks.get(ChunkPos.toLong(pos.x + dx, pos.z + dz));
                if (s != null && Structures.spread(s) >= Math.max(Math.abs(dx), Math.abs(dz))) return true;
            }
        return false;
    }

    @Override
    protected void onFound(ChunkPos pos, Hit hit) {
        basesDirty = true;
    }

    // ---- bases (clusters of hits) ----

    private void rebuildBases() {
        List<Long> keys = new ArrayList<>(results.keySet());
        Set<Long> seen = new HashSet<>();
        List<Base> out = new ArrayList<>();
        for (long start : keys) {
            if (!seen.add(start)) continue;
            List<Long> group = new ArrayList<>();
            Deque<Long> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                long k = stack.pop();
                group.add(k);
                int cx = ChunkPos.getPackedX(k), cz = ChunkPos.getPackedZ(k);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) {
                        long n = ChunkPos.toLong(cx + dx, cz + dz);
                        if (results.containsKey(n) && seen.add(n)) stack.push(n);
                    }
            }
            int score = 0, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            double wx = 0, wz = 0;
            boolean isKnown = true;
            Map<String, Integer> merged = new TreeMap<>();
            for (long k : group) {
                Hit h = results.get(k);
                score += h.score();
                ChunkPos c = new ChunkPos(k);
                wx += c.getCenterX() * (double) h.score();
                wz += c.getCenterZ() * (double) h.score();
                minY = Math.min(minY, h.minY());
                maxY = Math.max(maxY, h.maxY());
                if (!known.contains(k)) isKnown = false;
                h.counts().forEach((name, n) -> {
                    if (name.startsWith("~")) merged.merge(name, 1, (a, b) -> a);
                    else merged.merge(name, n, Integer::sum);
                });
            }
            String summary = new Hit(score, minY, maxY, merged, false, null).summary();
            out.add(new Base(group, score, (int) Math.round(wx / score), (int) Math.round(wz / score), minY, maxY, summary, isKnown));
        }
        out.sort(Comparator.comparingInt(Base::score).reversed());
        bases = out;
        announce(out);
    }

    private void announce(List<Base> list) {
        int strong = threshold() * 4;
        for (Base b : list) {
            // too small to show yet: don't mark it alerted, so it alerts once it grows past MinBaseScore
            if (b.score() < minBaseScore.getInt()) continue;
            boolean already = b.chunks().stream().anyMatch(alerted::contains);
            boolean fresh = !b.known();
            if (!already) {
                alerted.addAll(b.chunks());
                if (fresh && log.get()) writeLog(b);
                if (alert.get() && (fresh || alertKnown.get())) {
                    ChatUtil.coords((fresh ? "Possible base" : "Known base") + " (score " + b.score() + ", y " + b.minY() + " to " + b.maxY() + ") at",
                        b.x(), b.minY(), b.z());
                    ChatUtil.info(b.summary());
                    ChatUtil.ping();
                }
            } else {
                alerted.addAll(b.chunks());
            }
            if (b.score() >= strong && b.chunks().stream().noneMatch(alertedStrong::contains)) {
                alertedStrong.addAll(b.chunks());
                if (already && alert.get() && (fresh || alertKnown.get())) {
                    ChatUtil.coords("Base confirmed, score now " + b.score() + " at", b.x(), b.minY(), b.z());
                    ChatUtil.ping();
                }
            } else if (b.score() >= strong) {
                alertedStrong.addAll(b.chunks());
            }
        }
    }

    // ---- waypoint target ----

    /** A chunk inside the base you're heading to; follows the base as it grows or merges. */
    private long targetKey = Long.MIN_VALUE;

    private Base target() {
        if (targetKey == Long.MIN_VALUE) return null;
        for (Base b : bases) if (b.chunks().contains(targetKey)) return b;
        return null;
    }

    /** Sorted the way the HUD and .bases show them. */
    public List<Base> sortedBases() {
        Vec3d me = mc.player == null ? Vec3d.ZERO : mc.player.getEntityPos();
        Comparator<Base> order = sortBy.is("Score") ? Comparator.comparingInt(Base::score).reversed()
            : Comparator.comparingDouble(x -> x.distanceTo(me));
        return bases.stream().filter(b -> b.score() >= minBaseScore.getInt()).sorted(order).toList();
    }

    /** Points the HUD at base number {@code index} (1-based, as .bases lists them). */
    public Base setTarget(int index) {
        List<Base> list = sortedBases();
        if (index < 1 || index > list.size()) return null;
        Base b = list.get(index - 1);
        targetKey = b.chunks().get(0);
        return b;
    }

    public void clearTarget() {
        targetKey = Long.MIN_VALUE;
    }

    /** Writes the current bases as CSV plus Xaero's minimap waypoint lines. Returns the CSV path. */
    public Path export() throws IOException {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("lootclient");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String dim = mc.world.getRegistryKey().getValue().getPath();
        StringBuilder csv = new StringBuilder("x,y,z,score,chunks,min_y,max_y,dimension,logged_before,evidence\n");
        StringBuilder xaero = new StringBuilder("#waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination\n");
        int i = 1;
        for (Base b : sortedBases()) {
            csv.append(b.x()).append(',').append(b.minY()).append(',').append(b.z()).append(',').append(b.score()).append(',')
                .append(b.chunks().size()).append(',').append(b.minY()).append(',').append(b.maxY()).append(',').append(dim).append(',')
                .append(b.known()).append(",\"").append(b.summary().replace("\"", "'")).append("\"\n");
            xaero.append("waypoint:Base ").append(i).append(" (").append(b.score()).append("):B:").append(b.x()).append(':').append(b.minY())
                .append(':').append(b.z()).append(":12:false:0:gui.xaero_default:false:0:0:false\n");
            i++;
        }
        Path file = dir.resolve("bases-" + WorldUtil.serverKey() + "-" + dim + "-" + stamp + ".csv");
        Files.writeString(file, csv.toString());
        Files.writeString(dir.resolve("bases-" + WorldUtil.serverKey() + "-" + dim + "-" + stamp + "-xaero.txt"), xaero.toString());
        return file;
    }

    public List<Base> bases() {
        return bases;
    }

    // ---- log file ----

    private static final Pattern LOG_LINE = Pattern.compile("\\s(\\S+)\\s+x=(-?\\d+) z=(-?\\d+)");

    private Path logFile() {
        return FabricLoader.getInstance().getGameDir().resolve("lootclient").resolve("bases-" + WorldUtil.serverKey() + ".txt");
    }

    private void loadKnown() {
        known.clear();
        if (mc.world == null) return;
        Path file = logFile();
        if (!Files.exists(file)) return;
        String dim = mc.world.getRegistryKey().getValue().getPath();
        try {
            for (String line : Files.readAllLines(file)) {
                Matcher m = LOG_LINE.matcher(line);
                if (!m.find() || !m.group(1).equals(dim)) continue;
                int x = Integer.parseInt(m.group(2)), z = Integer.parseInt(m.group(3));
                // mark the logged chunk and its neighbours, a base's center can shift a little between visits
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) known.add(ChunkPos.toLong((x >> 4) + dx, (z >> 4) + dz));
            }
        } catch (IOException | RuntimeException e) {
            ChatUtil.error("Couldn't read base log: " + e.getMessage());
        }
    }

    private void writeLog(Base b) {
        try {
            Path file = logFile();
            Files.createDirectories(file.getParent());
            String dim = mc.world.getRegistryKey().getValue().getPath();
            String line = String.format("%s  %s  x=%d z=%d  score=%d  y=%d..%d  chunks=%d  %s%n",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), dim,
                b.x(), b.z(), b.score(), b.minY(), b.maxY(), b.chunks().size(), b.summary());
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            ChatUtil.error("Couldn't write base log: " + e.getMessage());
        }
    }

    // ---- rendering ----

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        double y = height.is("Fixed") ? fixedY.get() : Math.floor(mc.player.getLerpedPos(tickDelta).y) - 12;
        int strong = threshold() * 3;
        for (var e : results.entrySet()) {
            ChunkPos c = new ChunkPos(e.getKey());
            Hit hit = e.getValue();
            boolean old = known.contains(e.getKey());
            int rgb = old ? 0xFF9A2E : 0xFF2E2E;
            int alpha = (int) Math.min(255, fill.get() * (hit.score() >= strong ? 1.3 : old ? 0.6 : 1.0));
            int x1 = c.getStartX(), z1 = c.getStartZ(), x2 = x1 + 16, z2 = z1 + 16;
            r.boxFill(new Box(x1, y, z1, x2, y + 0.05, z2), (alpha << 24) | rgb);

            // outline only the edges that don't touch another flagged chunk, so clusters read as one shape
            int edge = 0xFF000000 | rgb;
            if (!results.containsKey(ChunkPos.toLong(c.x, c.z - 1))) r.line(x1, y + 0.06, z1, x2, y + 0.06, z1, edge);
            if (!results.containsKey(ChunkPos.toLong(c.x, c.z + 1))) r.line(x1, y + 0.06, z2, x2, y + 0.06, z2, edge);
            if (!results.containsKey(ChunkPos.toLong(c.x - 1, c.z))) r.line(x1, y + 0.06, z1, x1, y + 0.06, z2, edge);
            if (!results.containsKey(ChunkPos.toLong(c.x + 1, c.z))) r.line(x2, y + 0.06, z1, x2, y + 0.06, z2, edge);

            if (depth.get()) {
                Box col = new Box(x1 + 6, hit.minY(), z1 + 6, x1 + 10, hit.maxY() + 1, z1 + 10);
                r.box(col, 0xFFFF6B6B, 40);
                double cx = x1 + 8, cz = z1 + 8;
                r.line(cx, y, cz, cx, hit.maxY() + 1, cz, 0x99FF6B6B);
            }
        }
        if (beams.get()) {
            int top = mc.world.getBottomY() + mc.world.getHeight();
            Base t = target();
            for (Base b : bases) {
                if (b.score() < minBaseScore.getInt()) continue;
                int color = b == t ? 0xFFFFFFFF : b.known() ? 0xCCFF9A2E : 0xCCFF4D4D;
                r.line(b.x() + 0.5, b.minY(), b.z() + 0.5, b.x() + 0.5, top, b.z() + 0.5, color);
                r.line(b.x() + 0.4, b.minY(), b.z() + 0.5, b.x() + 0.4, top, b.z() + 0.5, color);
            }
        }
    }

    private int hudBottom = 86;

    /** Bottom edge of the HUD list drawn this frame, so Light Finder can stack its list under it. */
    public int hudBottom() {
        return hudBottom;
    }

    @Override
    public void onRenderHud(DrawContext ctx, float tickDelta) {
        hudBottom = 86;
        if (mc.options.hudHidden || bases.isEmpty()) return;
        Vec3d me = mc.player.getLerpedPos(tickDelta);
        if (beams.get()) {
            for (Base b : bases) {
                double dist = b.distanceTo(me);
                double labelY = Math.min(Math.max(b.maxY() + 3, me.y), me.y + 40);
                Vec3d s = Projection.toScreen(new Vec3d(b.x() + 0.5, labelY, b.z() + 0.5));
                if (s == null) continue;
                String text = b.score() + " pts  " + Math.round(dist) + "m";
                int w = UI.width(text);
                int x = (int) s.x - w / 2 - 4, y = (int) s.y - 6;
                UI.roundRect(ctx, x, y, x + w + 8, y + 12, 4, Theme.SURFACE);
                UI.drawText(ctx, text, x + 4, y + 2, b.known() ? 0xFFFFB86B : 0xFFFF8080);
            }
        }
        if (list.get()) {
            List<Base> sorted = sortedBases();
            List<Base> near = sorted.stream().limit(listRows.getInt()).toList();
            Base t = target();
            int extra = t != null ? 10 : 0;
            int w = 160, x = 4, y = 90;
            hudBottom = y + 16 + extra + near.size() * 10;
            UI.roundRect(ctx, x, y, x + w, y + 16 + extra + near.size() * 10, 5, Theme.SURFACE);
            UI.drawBold(ctx, "Bases", x + 6, y + 4, Theme.TEXT);
            UI.drawTextRight(ctx, String.valueOf(sorted.size()), x + w - 6, y + 4, Theme.ACCENT);
            if (t != null) {
                UI.drawText(ctx, "Going to " + t.x() + ", " + t.z(), x + 6, y + 15, Theme.ACCENT);
                UI.drawTextRight(ctx, Math.round(t.distanceTo(me)) + "m " + direction(me, t), x + w - 6, y + 15, Theme.ACCENT);
                y += 10;
            }
            for (int i = 0; i < near.size(); i++) {
                Base b = near.get(i);
                String dir = direction(me, b);
                String left = (sorted.indexOf(b) + 1) + ". " + b.x() + ", " + b.z();
                String right = b.score() + "  " + Math.round(b.distanceTo(me)) + "m " + dir;
                UI.drawText(ctx, left, x + 6, y + 15 + i * 10, b.known() ? Theme.TEXT_FAINT : Theme.TEXT_MUTED);
                UI.drawTextRight(ctx, right, x + w - 6, y + 15 + i * 10, Theme.TEXT);
            }
        }
    }

    private static String direction(Vec3d me, Base b) {
        double angle = Math.toDegrees(Math.atan2(b.z() - me.z, b.x() - me.x)) - 90;
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return names[Math.floorMod((int) Math.round(MathHelper.wrapDegrees(angle) / 45.0), 8)];
    }

    public List<String> describeHits() {
        List<String> out = new ArrayList<>();
        Vec3d me = mc.player == null ? Vec3d.ZERO : mc.player.getEntityPos();
        int i = 1;
        for (Base b : sortedBases()) {
            out.add("#" + i++ + "  " + b.x() + ", " + b.z() + "  y " + b.minY() + ".." + b.maxY() + "  score " + b.score() + "  "
                + Math.round(b.distanceTo(me)) + "m " + direction(me, b) + (b.known() ? "  (logged before)" : "") + "  " + b.summary());
        }
        return out;
    }

    @Override
    public String getInfo() {
        return String.valueOf(bases.size());
    }
}
