package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.InvUtil;
import dev.loot.client.util.Rotations;
import dev.loot.client.util.Targets;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * While you're falling onto someone, aims at them and smashes with your mace at the last moment.
 * Get up there with wind charges, pearls or an elytra.
 */
public class MaceBomber extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "Max distance to the target.", 4.5, 2, 6, 0.25));
    private final NumberSetting minFall = add(new NumberSetting("MinFall", "Blocks fallen before it arms.", 3, 1.5, 20, 0.5));

    private boolean struck;

    public MaceBomber() {
        super("Mace Bomber", "Auto-aims and smashes targets below you with a mace.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.player.isOnGround()) struck = false;
        if (struck || mc.player.fallDistance < minFall.get() || mc.player.getVelocity().y >= 0) return;
        PlayerEntity target = Targets.closestPlayer(range.get());
        if (target == null || target.getY() > mc.player.getY()) return;
        int mace = InvUtil.findHotbar(Items.MACE);
        if (mace == -1) return;
        InvUtil.select(mace);
        Rotations.face(target.getEntityPos().add(0, target.getHeight() * 0.5, 0));
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        struck = true;
    }
}
