package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.Targets;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.util.Hand;

public class TriggerBot extends Module {
    private final BoolSetting players = add(new BoolSetting("Players", "Attack players under your crosshair.", true));
    private final BoolSetting mobs = add(new BoolSetting("Mobs", "Attack hostile mobs under your crosshair.", false));
    private final BoolSetting crystals = add(new BoolSetting("Crystals", "Break end crystals under your crosshair.", false));
    private final NumberSetting cooldown = add(new NumberSetting("Cooldown", "Attack charge needed, 1.0 = full.", 0.95, 0, 1, 0.05));
    private final BoolSetting critsOnly = add(new BoolSetting("CritsOnly", "Only swing while falling, for crits.", false));

    public TriggerBot() {
        super("Trigger Bot", "Attacks whatever is under your crosshair.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null || mc.player.isUsingItem()) return;
        Entity e = mc.targetedEntity;
        if (e == null || !e.isAlive()) return;
        boolean ok = (players.get() && Targets.validPlayer(e)) || (mobs.get() && e instanceof Monster) || (crystals.get() && e instanceof EndCrystalEntity);
        if (!ok) return;
        if (!(e instanceof EndCrystalEntity) && mc.player.getAttackCooldownProgress(0.5f) < cooldown.get()) return;
        if (critsOnly.get() && !(e instanceof EndCrystalEntity) && (mc.player.isOnGround() || mc.player.getVelocity().y >= 0)) return;
        mc.interactionManager.attackEntity(mc.player, e);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
