package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes a crystal on your screen as soon as you hit it instead of waiting for the server,
 * so the spot is free for the next placement right away.
 */
public class CrystalOptimizer extends Module {
    private final List<Integer> pending = new ArrayList<>();

    public CrystalOptimizer() {
        super("Crystal Optimizer", "Clears hit crystals client-side for faster re-placing.", Category.COMBAT);
    }

    @Override
    public boolean onAttack(Entity target) {
        if (target instanceof EndCrystalEntity) pending.add(target.getId());
        return false;
    }

    @Override
    public void onTick() {
        for (int id : pending) mc.world.removeEntity(id, Entity.RemovalReason.KILLED);
        pending.clear();
    }
}
