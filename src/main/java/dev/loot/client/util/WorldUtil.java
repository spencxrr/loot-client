package dev.loot.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

public final class WorldUtil {
    private WorldUtil() {}

    /** All full chunks the client currently has loaded around the player. */
    public static List<WorldChunk> loadedChunks() {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<WorldChunk> out = new ArrayList<>();
        if (mc.world == null || mc.player == null) return out;
        int radius = mc.options.getClampedViewDistance() + 1;
        ChunkPos center = mc.player.getChunkPos();
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk != null && !chunk.isEmpty()) out.add(chunk);
            }
        }
        return out;
    }

    public static String serverKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String key = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "singleplayer";
        return key.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
