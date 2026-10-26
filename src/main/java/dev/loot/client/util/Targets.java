package dev.loot.client.util;

import dev.loot.client.LootClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Comparator;
import java.util.function.Predicate;

public final class Targets {
    private Targets() {}

    /** The most recent entity something in the client attacked or targeted, for Target HUD. */
    public static volatile LivingEntity lastTarget;
    public static volatile long lastTargetTime;

    public static void markTarget(Entity e) {
        if (e instanceof LivingEntity l) {
            lastTarget = l;
            lastTargetTime = System.currentTimeMillis();
        }
    }

    public static boolean validPlayer(Entity e) {
        var mc = MinecraftClient.getInstance();
        return e instanceof PlayerEntity p && p != mc.player && p.isAlive() && !p.isSpectator()
            && !LootClient.friends().isFriend(p.getGameProfile().name());
    }

    public static PlayerEntity closestPlayer(double range) {
        return (PlayerEntity) closest(range, Targets::validPlayer);
    }

    public static Entity closest(double range, Predicate<Entity> filter) {
        var mc = MinecraftClient.getInstance();
        Entity best = null;
        double bestSq = range * range;
        for (Entity e : mc.world.getEntities()) {
            if (!filter.test(e)) continue;
            double d = e.squaredDistanceTo(mc.player);
            if (d <= bestSq) {
                bestSq = d;
                best = e;
            }
        }
        return best;
    }

    public static Comparator<Entity> byDistance() {
        var p = MinecraftClient.getInstance().player;
        return Comparator.comparingDouble(e -> e.squaredDistanceTo(p));
    }
}
