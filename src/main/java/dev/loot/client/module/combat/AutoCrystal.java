package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.InvUtil;
import dev.loot.client.util.Rotations;
import dev.loot.client.util.Targets;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Places and breaks end crystals around the nearest enemy. Picks the spot with the most
 * damage to them that keeps your own damage under the limit.
 */
public class AutoCrystal extends Module {
    private final NumberSetting targetRange = add(new NumberSetting("TargetRange", "Max distance to a target.", 10, 4, 16, 0.5));
    private final NumberSetting placeRange = add(new NumberSetting("PlaceRange", "Max distance to place crystals.", 4.5, 2, 6, 0.25));
    private final NumberSetting breakRange = add(new NumberSetting("BreakRange", "Max distance to break crystals.", 4.5, 2, 6, 0.25));
    private final NumberSetting minDamage = add(new NumberSetting("MinDamage", "Minimum damage to the target for a spot to count.", 6, 0, 36, 0.5));
    private final NumberSetting maxSelf = add(new NumberSetting("MaxSelf", "Maximum damage you'll accept.", 8, 0, 36, 0.5));
    private final NumberSetting placeDelay = add(new NumberSetting("PlaceDelay", "Ticks between placements.", 1, 0, 10, 1));
    private final NumberSetting breakDelay = add(new NumberSetting("BreakDelay", "Ticks between breaks.", 1, 0, 10, 1));
    private final BoolSetting autoSwitch = add(new BoolSetting("AutoSwitch", "Switch to crystals when placing.", true));
    private final BoolSetting rotate = add(new BoolSetting("Rotate", "Face the crystal when placing/breaking.", true));
    private final BoolSetting antiSuicide = add(new BoolSetting("AntiSuicide", "Never act if it would drop you to 0.", true));
    private final BoolSetting render = add(new BoolSetting("Render", "Highlight where it's placing.", true));

    private int placeTimer, breakTimer;
    private BlockPos lastPlace;
    private PlayerEntity target;

    public AutoCrystal() {
        super("Auto Crystal", "Automatically places and detonates end crystals.", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        target = null;
        lastPlace = null;
    }

    @Override
    public void onTick() {
        target = Targets.closestPlayer(targetRange.get());
        if (target == null) {
            lastPlace = null;
            return;
        }
        Targets.markTarget(target);
        if (breakTimer > 0) breakTimer--;
        if (placeTimer > 0) placeTimer--;
        if (breakTimer == 0) doBreak();
        if (placeTimer == 0) doPlace();
    }

    private float selfLimit() {
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        return antiSuicide.get() ? Math.min(maxSelf.getFloat(), hp - 1f) : maxSelf.getFloat();
    }

    private void doBreak() {
        EndCrystalEntity best = null;
        float bestDmg = 0;
        double range = breakRange.get();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity c) || !c.isAlive()) continue;
            if (mc.player.getEyePos().distanceTo(c.getEntityPos()) > range) continue;
            float self = CrystalUtil.damage(c.getEntityPos(), mc.player);
            if (self > selfLimit()) continue;
            float dmg = CrystalUtil.damage(c.getEntityPos(), target);
            if (dmg > bestDmg) {
                bestDmg = dmg;
                best = c;
            }
        }
        if (best == null || bestDmg < Math.min(minDamage.get(), 2)) return;
        if (rotate.get()) Rotations.face(best.getEntityPos().add(0, 0.5, 0));
        CrystalUtil.hit(best);
        breakTimer = breakDelay.getInt();
    }

    private void doPlace() {
        boolean holding = InvUtil.holding(Items.END_CRYSTAL) || mc.player.getOffHandStack().isOf(Items.END_CRYSTAL);
        int slot = InvUtil.findHotbar(Items.END_CRYSTAL);
        if (!holding && (!autoSwitch.get() || slot == -1)) return;

        int r = (int) Math.ceil(placeRange.get());
        BlockPos center = mc.player.getBlockPos();
        BlockPos best = null;
        float bestDmg = minDamage.getFloat();
        float limit = selfLimit();
        double rangeSq = placeRange.get() * placeRange.get();
        Box targetBox = target.getBoundingBox();
        for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
            if (mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > rangeSq) continue;
            if (!CrystalUtil.canPlaceOn(pos)) continue;
            var cpos = CrystalUtil.crystalPos(pos);
            if (targetBox.intersects(new Box(pos.up()))) continue;
            float self = CrystalUtil.damage(cpos, mc.player);
            if (self > limit) continue;
            float dmg = CrystalUtil.damage(cpos, target);
            if (dmg > bestDmg) {
                bestDmg = dmg;
                best = pos.toImmutable();
            }
        }
        lastPlace = best;
        if (best == null) return;
        if (!holding) InvUtil.select(slot);
        if (rotate.get()) Rotations.face(best.toCenterPos().add(0, 0.5, 0));
        CrystalUtil.place(best);
        placeTimer = placeDelay.getInt();
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        if (render.get() && lastPlace != null) r.box(new Box(lastPlace), dev.loot.client.gui.Theme.ACCENT, 50);
    }

    @Override
    public String getInfo() {
        return target == null ? null : target.getGameProfile().name();
    }
}
