package dev.loot.client.module.basefinding;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.ChatUtil;
import dev.loot.client.util.WorldUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Scores every loaded chunk by how "player-made" its contents look and flags chunks over a
 * threshold. Natural structures (dungeons, trial chambers, villages) get their loot containers
 * discounted so they don't spam alerts. Hits are logged per server to .minecraft/lootclient/.
 */
public class BaseFinder extends Module {
    private final NumberSetting threshold = add(new NumberSetting("Threshold", "Score a chunk needs to be flagged.", 12, 3, 100, 1));
    private final BoolSetting chat = add(new BoolSetting("ChatAlert", "Post flagged chunks in chat.", true));
    private final BoolSetting sound = add(new BoolSetting("Sound", "Ping when a new base is found.", true));
    private final BoolSetting log = add(new BoolSetting("LogFile", "Append hits to lootclient/bases-<server>.txt.", true));
    private final BoolSetting render = add(new BoolSetting("Render", "Outline flagged chunks.", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", "Draw lines to flagged chunks.", false));
    private final NumberSetting groupRadius = add(new NumberSetting("GroupRadius", "Chunks within this many of a known hit don't alert again.", 4, 0, 16, 1));

    private record Hit(String dim, ChunkPos pos, int score, int minY, int maxY, String summary) {}

    private final Map<String, Hit> hits = new LinkedHashMap<>();
    private final Deque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private int refreshTimer;

    public BaseFinder() {
        super("Base Finder", "Flags chunks that look like player bases or stashes.", Category.BASEFINDING);
    }

    @Override
    public void onEnable() {
        if (!inGame()) return;
        for (WorldChunk chunk : WorldUtil.loadedChunks()) enqueue(chunk.getPos());
    }

    @Override
    public void onDisable() {
        queue.clear();
        queued.clear();
    }

    public void clearHits() {
        hits.clear();
    }

    @Override
    public void onChunkLoad(WorldChunk chunk) {
        if (isEnabled()) enqueue(chunk.getPos());
    }

    private void enqueue(ChunkPos pos) {
        long key = pos.toLong();
        if (queued.add(key)) queue.add(key);
    }

    @Override
    public void onTick() {
        if (refreshTimer-- <= 0) {
            // Entities (item frames, armor stands) load after chunks, so give nearby chunks a second look.
            refreshTimer = 100;
            ChunkPos c = mc.player.getChunkPos();
            for (int x = -3; x <= 3; x++)
                for (int z = -3; z <= 3; z++)
                    enqueue(new ChunkPos(c.x + x, c.z + z));
        }
        int budget = 32;
        while (!queue.isEmpty() && budget-- > 0) {
            long key = queue.poll();
            queued.remove(key);
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
            if (chunk != null) evaluate(chunk);
        }
    }

    private void evaluate(WorldChunk chunk) {
        Map<String, Integer> counts = new TreeMap<>();
        boolean structure = false;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof MobSpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity
                || be instanceof VaultBlockEntity || be instanceof BellBlockEntity) {
                structure = true;
                continue;
            }
            String kind = kind(be);
            if (kind == null) continue;
            counts.merge(kind, 1, Integer::sum);
            minY = Math.min(minY, be.getPos().getY());
            maxY = Math.max(maxY, be.getPos().getY());
        }

        ChunkPos pos = chunk.getPos();
        Box area = new Box(pos.getStartX(), mc.world.getBottomY(), pos.getStartZ(), pos.getEndX() + 1, mc.world.getBottomY() + mc.world.getHeight(), pos.getEndZ() + 1);
        for (Entity e : mc.world.getOtherEntities(null, area)) {
            if (e instanceof ItemFrameEntity) counts.merge("item_frame", 1, Integer::sum);
            else if (e instanceof ArmorStandEntity) counts.merge("armor_stand", 1, Integer::sum);
            else continue;
            minY = Math.min(minY, e.getBlockY());
            maxY = Math.max(maxY, e.getBlockY());
        }

        int score = 0;
        for (var entry : counts.entrySet()) {
            int w = weight(entry.getKey());
            // Loot containers inside generated structures are expected; count them for a lot less.
            if (structure && isLootish(entry.getKey())) w = 0;
            score += w * entry.getValue();
        }

        String dim = mc.world.getRegistryKey().getValue().getPath();
        String id = dim + ":" + pos.x + ":" + pos.z;
        if (score < threshold.getInt()) {
            hits.remove(id);
            return;
        }

        boolean isNew = !hits.containsKey(id);
        boolean nearKnown = isNew && hits.values().stream().anyMatch(h -> h.dim.equals(dim)
            && Math.abs(h.pos.x - pos.x) <= groupRadius.getInt() && Math.abs(h.pos.z - pos.z) <= groupRadius.getInt());
        StringBuilder summary = new StringBuilder();
        counts.forEach((k, v) -> summary.append(summary.isEmpty() ? "" : ", ").append(v).append(' ').append(k));
        Hit hit = new Hit(dim, pos, score, minY, maxY, summary.toString());
        hits.put(id, hit);

        if (isNew && !nearKnown) announce(hit);
    }

    private void announce(Hit hit) {
        int x = hit.pos.getCenterX(), z = hit.pos.getCenterZ();
        if (chat.get()) {
            Text coords = Text.literal(x + ", " + z).setStyle(Style.EMPTY.withColor(Formatting.AQUA).withUnderline(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(x + " " + z)));
            ChatUtil.send(Text.literal("[").formatted(Formatting.DARK_GRAY)
                .append(Text.literal("Loot").styled(s -> s.withColor(0xA3AEFF)))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal("Possible base (score " + hit.score + ") at ").formatted(Formatting.GRAY))
                .append(coords)
                .append(Text.literal(" " + hit.dim + " | " + hit.summary).formatted(Formatting.DARK_GRAY)));
        }
        if (sound.get()) mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1f, 1.6f);
        if (log.get()) writeLog(hit);
    }

    private void writeLog(Hit hit) {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("lootclient");
            Files.createDirectories(dir);
            String line = String.format("%s  %s  x=%d z=%d  score=%d  y=%d..%d  %s%n",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), hit.dim,
                hit.pos.getCenterX(), hit.pos.getCenterZ(), hit.score, hit.minY, hit.maxY, hit.summary);
            Files.writeString(dir.resolve("bases-" + WorldUtil.serverKey() + ".txt"), line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            ChatUtil.error("Couldn't write base log: " + e.getMessage());
        }
    }

    private static String kind(BlockEntity be) {
        if (be instanceof ShulkerBoxBlockEntity) return "shulker";
        if (be instanceof EnderChestBlockEntity) return "ender_chest";
        if (be instanceof BeaconBlockEntity) return "beacon";
        if (be instanceof ConduitBlockEntity) return "conduit";
        if (be instanceof EnchantingTableBlockEntity) return "enchanting_table";
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
        if (be instanceof BrewingStandBlockEntity) return "brewing_stand";
        if (be instanceof DispenserBlockEntity) return "dispenser";
        if (be instanceof ComparatorBlockEntity) return "comparator";
        return null;
    }

    private static int weight(String kind) {
        return switch (kind) {
            case "beacon" -> 10;
            case "shulker" -> 6;
            case "conduit" -> 5;
            case "ender_chest" -> 4;
            case "enchanting_table", "crafter" -> 3;
            case "hopper", "banner", "skull", "jukebox", "armor_stand" -> 2;
            case "sign", "chest", "barrel", "furnace", "bed", "brewing_stand", "dispenser", "comparator", "item_frame" -> 1;
            default -> 0;
        };
    }

    private static boolean isLootish(String kind) {
        return kind.equals("chest") || kind.equals("barrel") || kind.equals("dispenser")
            || kind.equals("bed") || kind.equals("furnace") || kind.equals("brewing_stand");
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        if (!render.get()) return;
        String dim = mc.world.getRegistryKey().getValue().getPath();
        for (Hit hit : hits.values()) {
            if (!hit.dim.equals(dim)) continue;
            int color = hit.score >= threshold.getInt() * 3 ? 0xFFFF2E2E : 0xFFFFC02E;
            Box box = new Box(hit.pos.getStartX(), hit.minY, hit.pos.getStartZ(),
                hit.pos.getEndX() + 1, hit.maxY + 1, hit.pos.getEndZ() + 1);
            r.box(box, color, 25);
            if (tracers.get()) r.line(r.crosshair(), box.getCenter(), color);
        }
    }

    public Collection<String> describeHits() {
        List<String> out = new ArrayList<>();
        for (Hit h : hits.values())
            out.add(h.dim + " " + h.pos.getCenterX() + ", " + h.pos.getCenterZ() + " (score " + h.score + ")");
        return out;
    }

    @Override
    public String getInfo() {
        return String.valueOf(hits.size());
    }
}
