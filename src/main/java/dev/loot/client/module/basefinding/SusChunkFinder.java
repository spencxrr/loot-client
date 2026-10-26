package dev.loot.client.module.basefinding;

import dev.loot.client.module.Category;
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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The main base finder. Every loaded chunk gets a score from three kinds of evidence, top of
 * the world to bedrock:
 *  - things players build: storage, shulkers, ender chests, beacons, anvils, item frames...
 *  - player-placed light underground (torches, lanterns) where caves have none
 *  - plant growth that only happens while someone keeps the chunk loaded for a long time
 *    (max-age kelp, aged sugar cane and cactus)
 * Loot in generated structures (dungeons, trial chambers, villages) counts for little.
 * Flagged chunks are drawn as big red squares below you, so you can spot them from an elytra,
 * merged into one shape when they touch, with a column showing how deep the evidence goes.
 */
public class SusChunkFinder extends ChunkScanModule<SusChunkFinder.Hit> {
    public record Hit(int score, int minY, int maxY, String summary) {}

    private final NumberSetting sensitivity = add(new NumberSetting("Sensitivity", "Higher flags more chunks (and more false alarms).", 6, 1, 10, 1));
    private final ModeSetting height = add(new ModeSetting("DrawAt", "Draw squares below you (good on elytra) or at a fixed height.", "BelowMe", "BelowMe", "Fixed"));
    private final NumberSetting fixedY = add(new NumberSetting("FixedY", "Height for DrawAt Fixed.", 64, -64, 320, 1));
    private final BoolSetting depth = add(new BoolSetting("ShowDepth", "Column showing the Y range the evidence was found in.", true));
    private final BoolSetting alert = add(new BoolSetting("Alert", "Chat + sound for new finds.", true));
    private final BoolSetting log = add(new BoolSetting("LogFile", "Append finds to .minecraft/lootclient/bases-<server>.txt.", true));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Square opacity.", 90, 20, 255, 5));

    private ClientWorld lastWorld;

    public SusChunkFinder() {
        super("Sus Chunk Finder", "Finds chunks with signs of a player base, at any depth.", Category.BASEFINDING);
    }

    private int threshold() {
        // sensitivity 1 -> 24 points needed, 6 -> 13, 10 -> 4
        return Math.max(3, Math.round(26.2f - sensitivity.getFloat() * 2.2f));
    }

    @Override
    protected int refreshRadius() {
        return 3;
    }

    @Override
    public void onChunkUnload(WorldChunk chunk) {
        // keep hits after the chunk unloads so the map of finds survives flying away
    }

    @Override
    public void onTick() {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            results.clear();
        }
        super.onTick();
    }

    public void clearHits() {
        results.clear();
    }

    // ---- scoring ----

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

    private static int weight(String kind) {
        return switch (kind) {
            case "beacon" -> 10;
            case "shulker" -> 6;
            case "conduit", "netherite block" -> 5;
            case "ender chest", "respawn anchor", "diamond block" -> 4;
            case "enchanting table", "crafter", "anvil" -> 3;
            case "hopper", "banner", "skull", "jukebox", "armor stand", "item frame" -> 2;
            case "sign", "chest", "barrel", "furnace", "bed", "brewing stand", "dispenser", "comparator", "crafting table", "light", "obsidian" -> 1;
            default -> 0;
        };
    }

    private static boolean lootish(String kind) {
        return kind.equals("chest") || kind.equals("barrel") || kind.equals("dispenser") || kind.equals("bed")
            || kind.equals("furnace") || kind.equals("brewing stand") || kind.equals("crafting table") || kind.equals("light")
            || kind.equals("banner") || kind.equals("anvil") || kind.equals("obsidian");
    }

    private static String blockKind(BlockState s, int y) {
        Block b = s.getBlock();
        if (b == Blocks.NETHERITE_BLOCK) return "netherite block";
        if (b == Blocks.DIAMOND_BLOCK) return "diamond block";
        if (b == Blocks.RESPAWN_ANCHOR) return "respawn anchor";
        if (b instanceof AnvilBlock) return "anvil";
        if (y < 40) {
            if (b == Blocks.CRAFTING_TABLE) return "crafting table";
            if (b == Blocks.OBSIDIAN) return "obsidian"; // lava pools make some, so it's weak on its own
            if (b == Blocks.TORCH || b == Blocks.WALL_TORCH || b == Blocks.LANTERN || b == Blocks.SOUL_TORCH
                || b == Blocks.SOUL_WALL_TORCH || b == Blocks.SOUL_LANTERN || b == Blocks.SEA_LANTERN) return "light";
        }
        return null;
    }

    private static boolean interesting(BlockState s) {
        Block b = s.getBlock();
        return b == Blocks.NETHERITE_BLOCK || b == Blocks.DIAMOND_BLOCK || b == Blocks.RESPAWN_ANCHOR || b instanceof AnvilBlock
            || b == Blocks.CRAFTING_TABLE || b == Blocks.OBSIDIAN || b == Blocks.TORCH || b == Blocks.WALL_TORCH
            || b == Blocks.LANTERN || b == Blocks.SOUL_TORCH || b == Blocks.SOUL_WALL_TORCH || b == Blocks.SOUL_LANTERN
            || b == Blocks.SEA_LANTERN || b == Blocks.KELP || b == Blocks.SUGAR_CANE || b == Blocks.CACTUS;
    }

    @Override
    protected Hit scan(WorldChunk chunk) {
        Map<String, Integer> counts = new TreeMap<>();
        boolean structure = false;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof MobSpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity
                || be instanceof VaultBlockEntity || be instanceof BellBlockEntity) {
                structure = true;
                continue;
            }
            String k = kind(be);
            if (k == null) continue;
            counts.merge(k, 1, Integer::sum);
            minY = Math.min(minY, be.getPos().getY());
            maxY = Math.max(maxY, be.getPos().getY());
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
                        String k = blockKind(s, by + y);
                        if (k == null) continue;
                        counts.merge(k, 1, Integer::sum);
                        minY = Math.min(minY, by + y);
                        maxY = Math.max(maxY, by + y);
                    }
        }

        ChunkPos pos = chunk.getPos();
        Box area = new Box(pos.getStartX(), chunk.getBottomY(), pos.getStartZ(), pos.getEndX() + 1, chunk.getBottomY() + chunk.getHeight(), pos.getEndZ() + 1);
        for (Entity e : mc.world.getOtherEntities(null, area)) {
            String k = e instanceof ItemFrameEntity ? "item frame" : e instanceof ArmorStandEntity ? "armor stand" : null;
            if (k == null) continue;
            counts.merge(k, 1, Integer::sum);
            minY = Math.min(minY, e.getBlockY());
            maxY = Math.max(maxY, e.getBlockY());
        }

        int score = 0;
        for (var entry : counts.entrySet()) {
            int w = weight(entry.getKey());
            if (structure && lootish(entry.getKey())) w = 0;
            int n = entry.getValue();
            if (entry.getKey().equals("light")) n = Math.min(n, 8);
            if (entry.getKey().equals("obsidian")) n = Math.min(n, 4);
            score += w * n;
        }
        // ~68s of loaded time per growth tick; kelp at max age needs a few minutes at least
        int minutes = (int) Math.round(growthTicks * 1.14);
        if (grownKelp >= 3) minutes = Math.max(minutes, 16);
        if (minutes >= 5) {
            score += Math.min(10, minutes / 3);
            counts.put("~" + minutes + " min loaded", 1);
        }

        if (score < threshold()) return null;
        if (minY == Integer.MAX_VALUE) {
            minY = 50;
            maxY = 70;
        }
        StringBuilder summary = new StringBuilder();
        counts.forEach((k, v) -> summary.append(summary.isEmpty() ? "" : ", ").append(k.startsWith("~") ? k : v + " " + k));
        return new Hit(score, minY, maxY, summary.toString());
    }

    @Override
    protected void onFound(ChunkPos pos, Hit hit) {
        boolean nearKnown = false;
        for (var e : results.entrySet()) {
            ChunkPos o = new ChunkPos(e.getKey());
            if (!o.equals(pos) && Math.abs(o.x - pos.x) <= 2 && Math.abs(o.z - pos.z) <= 2) {
                nearKnown = true;
                break;
            }
        }
        if (log.get()) writeLog(pos, hit);
        if (!alert.get() || nearKnown) return;
        ChatUtil.coords("Sus chunk (score " + hit.score() + ", y " + hit.minY() + " to " + hit.maxY() + ") at",
            pos.getCenterX(), hit.minY(), pos.getCenterZ());
        ChatUtil.info(hit.summary());
        ChatUtil.ping();
    }

    private void writeLog(ChunkPos pos, Hit hit) {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("lootclient");
            Files.createDirectories(dir);
            String dim = mc.world.getRegistryKey().getValue().getPath();
            String line = String.format("%s  %s  x=%d z=%d  score=%d  y=%d..%d  %s%n",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), dim,
                pos.getCenterX(), pos.getCenterZ(), hit.score(), hit.minY(), hit.maxY(), hit.summary());
            Files.writeString(dir.resolve("bases-" + WorldUtil.serverKey() + ".txt"), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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
            int alpha = (int) Math.min(255, fill.get() * (hit.score() >= strong ? 1.3 : 1.0));
            int x1 = c.getStartX(), z1 = c.getStartZ(), x2 = x1 + 16, z2 = z1 + 16;
            r.boxFill(new Box(x1, y, z1, x2, y + 0.05, z2), (alpha << 24) | 0xFF2E2E);

            // outline only the edges that don't touch another flagged chunk, so clusters read as one shape
            int edge = 0xFFFF4040;
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
    }

    public List<String> describeHits() {
        List<String> out = new ArrayList<>();
        for (var e : results.entrySet()) {
            ChunkPos c = new ChunkPos(e.getKey());
            Hit h = e.getValue();
            out.add(c.getCenterX() + ", " + c.getCenterZ() + "  y " + h.minY() + ".." + h.maxY() + "  score " + h.score() + "  " + h.summary());
        }
        return out;
    }
}
