package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class AutoTool extends Module {
    private final BoolSetting switchBack = add(new BoolSetting("SwitchBack", "Go back to your old slot after mining.", true));
    private final NumberSetting minDurability = add(new NumberSetting("MinDurability", "Skip tools with this little durability left.", 10, 0, 100, 1));

    private int prevSlot = -1;

    public AutoTool() {
        super("Auto Tool", "Switches to the best tool for the block you're mining.", Category.MISC);
    }

    @Override
    public void onTick() {
        boolean mining = mc.options.attackKey.isPressed() && mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK;
        if (!mining) {
            if (switchBack.get() && prevSlot != -1) mc.player.getInventory().setSelectedSlot(prevSlot);
            prevSlot = -1;
            return;
        }
        BlockState state = mc.world.getBlockState(((BlockHitResult) mc.crosshairTarget).getBlockPos());
        if (state.isAir()) return;
        int best = -1;
        float bestSpeed = 1.0001f;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isDamageable() && s.getMaxDamage() - s.getDamage() <= minDurability.getInt()) continue;
            float speed = s.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        int current = mc.player.getInventory().getSelectedSlot();
        if (best != -1 && best != current) {
            if (prevSlot == -1) prevSlot = current;
            mc.player.getInventory().setSelectedSlot(best);
        }
    }
}
