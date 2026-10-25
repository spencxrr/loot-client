package dev.loot.client.module.render;

import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.VehicleEntity;

/** Shared target picking and colors for ESP and Tracers. */
final class EntityFilter {
    final BoolSetting players;
    final BoolSetting hostiles;
    final BoolSetting passives;
    final BoolSetting items;
    final BoolSetting vehicles;

    EntityFilter(Module owner, boolean playersOn, boolean mobsOn, boolean itemsOn) {
        players = owner.addSetting(new BoolSetting("Players", "Target other players.", playersOn));
        hostiles = owner.addSetting(new BoolSetting("Hostiles", "Target hostile mobs.", mobsOn));
        passives = owner.addSetting(new BoolSetting("Passives", "Target animals and villagers.", mobsOn));
        items = owner.addSetting(new BoolSetting("Items", "Target dropped items.", itemsOn));
        vehicles = owner.addSetting(new BoolSetting("Vehicles", "Target boats and minecarts.", false));
    }

    boolean test(Entity e, Entity self) {
        if (e == self || !e.isAlive()) return false;
        if (e instanceof PlayerEntity) return players.get();
        if (e instanceof Monster) return hostiles.get();
        if (e instanceof PassiveEntity) return passives.get();
        if (e instanceof ItemEntity) return items.get();
        if (e instanceof VehicleEntity) return vehicles.get();
        return false;
    }

    static int color(Entity e) {
        if (e instanceof PlayerEntity) return 0xFFFF3B3B;
        if (e instanceof Monster) return 0xFFFF9A2E;
        if (e instanceof PassiveEntity) return 0xFF4CE06A;
        if (e instanceof ItemEntity) return 0xFF52C8FF;
        return 0xFFDDDDDD;
    }
}
