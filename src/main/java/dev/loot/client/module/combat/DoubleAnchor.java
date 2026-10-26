package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.ChatUtil;
import dev.loot.client.util.InvUtil;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Bind it to a key and look at the spot: place anchor, charge, blow, place again, charge,
 * blow. Two anchors of damage in about six ticks. Turns itself off when done.
 */
public class DoubleAnchor extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between steps.", 1, 0, 5, 1));

    private BlockPos support;
    private Direction side;
    private int step, timer, prevSlot;

    public DoubleAnchor() {
        super("Double Anchor", "Places and detonates two respawn anchors in a row.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        if (!inGame() || !(mc.crosshairTarget instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) {
            if (inGame()) ChatUtil.error("Look at a block first.");
            setEnabled(false);
            return;
        }
        if (InvUtil.findHotbar(Items.RESPAWN_ANCHOR) == -1 || InvUtil.findHotbar(Items.GLOWSTONE) == -1) {
            ChatUtil.error("Need respawn anchors and glowstone in your hotbar.");
            setEnabled(false);
            return;
        }
        support = bh.getBlockPos();
        side = bh.getSide();
        step = 0;
        timer = 0;
        prevSlot = InvUtil.selected();
    }

    @Override
    public void onTick() {
        if (timer-- > 0) return;
        BlockPos anchor = AnchorUtil.isAnchor(support) ? support : support.offset(side);
        switch (step % 3) {
            case 0 -> { // place
                if (!AnchorUtil.isAnchor(anchor)) {
                    InvUtil.select(InvUtil.findHotbar(Items.RESPAWN_ANCHOR));
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(
                        support.toCenterPos().add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5), side, support, false));
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
            case 1 -> { // charge
                int glow = InvUtil.findHotbar(Items.GLOWSTONE);
                if (glow == -1 || !AnchorUtil.isAnchor(anchor)) {
                    finish();
                    return;
                }
                InvUtil.select(glow);
                AnchorUtil.click(anchor, Direction.UP);
            }
            default -> { // explode
                int s = AnchorUtil.explodeSlot();
                if (s != -1 && AnchorUtil.isAnchor(anchor)) {
                    InvUtil.select(s);
                    AnchorUtil.click(anchor, Direction.UP);
                }
            }
        }
        step++;
        timer = delay.getInt();
        if (step >= 6) finish();
    }

    private void finish() {
        InvUtil.select(prevSlot);
        setEnabled(false);
    }
}
