package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.InvUtil;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/** Hold right click on a respawn anchor: charges it with glowstone, then swaps off and detonates. */
public class AnchorMacro extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between steps.", 1, 0, 6, 1));
    private final BoolSetting swapBack = add(new BoolSetting("SwapBack", "Return to your old slot afterwards.", true));

    private int timer;
    private int prevSlot = -1;

    public AnchorMacro() {
        super("Anchor Macro", "Charges and explodes respawn anchors for you.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        boolean active = mc.currentScreen == null && mc.options.useKey.isPressed()
            && mc.crosshairTarget instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && AnchorUtil.isAnchor(bh.getBlockPos());
        if (!active) {
            if (prevSlot != -1 && swapBack.get()) InvUtil.select(prevSlot);
            prevSlot = -1;
            return;
        }
        if (timer-- > 0) return;
        BlockHitResult bh = (BlockHitResult) mc.crosshairTarget;
        if (prevSlot == -1) prevSlot = InvUtil.selected();
        int slot = AnchorUtil.charges(bh.getBlockPos()) == 0 ? InvUtil.findHotbar(Items.GLOWSTONE) : AnchorUtil.explodeSlot();
        if (slot == -1) return;
        InvUtil.select(slot);
        AnchorUtil.click(bh.getBlockPos(), bh.getSide());
        timer = delay.getInt();
    }
}
