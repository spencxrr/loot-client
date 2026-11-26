package dev.loot.client.module.basefinding;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Cheap "is this a generated structure?" check from block palettes, so loot chests, village beds
 * and stronghold torches don't get mistaken for a base. Every marker here is a block that world
 * generation only places inside one kind of structure and that players rarely build with.
 * Works off-thread (palette reads only).
 */
final class Structures {
    private Structures() {}

    private static volatile Map<Block, String> markers;

    private static Map<Block, String> markers() {
        if (markers != null) return markers;
        Map<Block, String> m = new HashMap<>();
        m.put(Blocks.BELL, "village");
        m.put(Blocks.END_PORTAL_FRAME, "stronghold");
        m.put(Blocks.INFESTED_STONE_BRICKS, "stronghold");
        m.put(Blocks.INFESTED_MOSSY_STONE_BRICKS, "stronghold");
        m.put(Blocks.INFESTED_CRACKED_STONE_BRICKS, "stronghold");
        m.put(Blocks.INFESTED_CHISELED_STONE_BRICKS, "stronghold");
        m.put(Blocks.REINFORCED_DEEPSLATE, "ancient city");
        m.put(Blocks.SCULK_SHRIEKER, "ancient city");
        m.put(Blocks.GILDED_BLACKSTONE, "bastion");
        m.put(Blocks.NETHER_BRICK_FENCE, "fortress");
        m.put(Blocks.TRIAL_SPAWNER, "trial chamber");
        m.put(Blocks.VAULT, "trial chamber");
        m.put(Blocks.SPAWNER, "dungeon");
        m.put(Blocks.SUSPICIOUS_GRAVEL, "ruins");
        m.put(Blocks.SUSPICIOUS_SAND, "ruins");
        m.put(Blocks.CRYING_OBSIDIAN, "ruined portal");
        m.put(Blocks.PRISMARINE_BRICKS, "monument");
        m.put(Blocks.DARK_PRISMARINE, "monument");
        m.put(Blocks.PURPUR_BLOCK, "end city");
        m.put(Blocks.PURPUR_PILLAR, "end city");
        m.put(Blocks.COBWEB, "mineshaft");
        m.put(Blocks.TRIPWIRE_HOOK, "temple");
        m.put(Blocks.CHISELED_SANDSTONE, "temple");
        for (Block b : Registries.BLOCK) {
            String id = Registries.BLOCK.getId(b).getPath();
            // trial chambers are full of waxed copper; tuff bricks alone are too common in builds
            if (id.startsWith("waxed_") && id.contains("copper_bulb")) m.put(b, "trial chamber");
        }
        markers = m;
        return m;
    }

    /** Name of the first structure marker found in the chunk, or null. */
    static String detect(WorldChunk chunk) {
        Map<Block, String> m = markers();
        ChunkSection[] sections = chunk.getSectionArray();
        for (ChunkSection section : sections) {
            if (section == null || section.isEmpty()) continue;
            if (!section.hasAny(s -> m.containsKey(s.getBlock()))) continue;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        BlockState s = section.getBlockState(x, y, z);
                        String name = m.get(s.getBlock());
                        if (name == null) continue;
                        // a single cobweb or tripwire hook is too weak; mineshafts and temples come with more
                        if (name.equals("mineshaft") && !section.hasAny(st -> st.isOf(Blocks.RAIL) || st.isOf(Blocks.OAK_FENCE) || st.isOf(Blocks.DARK_OAK_FENCE))) continue;
                        if (name.equals("temple") && !section.hasAny(st -> st.isOf(Blocks.MOSSY_COBBLESTONE) || st.isOf(Blocks.ORANGE_TERRACOTTA) || st.isOf(Blocks.TNT))) continue;
                        return name;
                    }
        }
        return null;
    }

    /** How far (in chunks) a structure's influence spreads to neighbouring chunks. */
    static int spread(String name) {
        if (name == null) return 0;
        return switch (name) {
            case "village", "ancient city", "monument", "end city", "bastion", "fortress", "stronghold", "trial chamber" -> 2;
            case "dungeon", "ruined portal", "temple", "ruins", "mineshaft" -> 1;
            default -> 0;
        };
    }
}
