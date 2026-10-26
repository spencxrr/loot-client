package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.InvUtil;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Hold right click while looking at a block: places obsidian, puts a crystal on it and
 * breaks it, over and over. The classic "hit crystal" done for you.
 */
public class AutoHitCrystal extends Module {
    private final BoolSetting placeObsidian = add(new BoolSetting("PlaceObsidian", "Place obsidian first if you're not looking at any.", true));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between steps.", 1, 0, 6, 1));

    private int timer;

    public AutoHitCrystal() {
        super("Auto Hit Crystal", "Obsidian, crystal, hit, on a loop while you hold right click.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null || !mc.options.useKey.isPressed()) return;
        if (timer-- > 0) return;
        HitResult hit = mc.crosshairTarget;
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystalEntity crystal) {
            CrystalUtil.hit(crystal);
            timer = delay.getInt();
            return;
        }
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return;
        var state = mc.world.getBlockState(bh.getBlockPos());
        boolean base = state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
        if (base && CrystalUtil.canPlaceOn(bh.getBlockPos())) {
            int slot = InvUtil.findHotbar(Items.END_CRYSTAL);
            if (slot == -1) return;
            InvUtil.select(slot);
            CrystalUtil.place(bh.getBlockPos());
        } else if (!base && placeObsidian.get()) {
            int slot = InvUtil.findHotbar(Items.OBSIDIAN);
            if (slot == -1) return;
            InvUtil.select(slot);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bh);
            mc.player.swingHand(Hand.MAIN_HAND);
        } else {
            return;
        }
        timer = delay.getInt();
    }
}
