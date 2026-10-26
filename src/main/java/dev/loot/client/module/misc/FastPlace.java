package dev.loot.client.module.misc;

import dev.loot.client.mixin.MinecraftClientAccessor;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;

public class FastPlace extends Module {
    private final BoolSetting blocksOnly = add(new BoolSetting("BlocksOnly", "Only speed up placing blocks.", false));
    private final BoolSetting crystals = add(new BoolSetting("Crystals", "Speed up end crystals even with BlocksOnly.", true));

    public FastPlace() {
        super("Fast Place", "Removes the delay between right clicks.", Category.MISC);
    }

    @Override
    public void onTick() {
        var held = mc.player.getMainHandStack();
        if (blocksOnly.get() && !(held.getItem() instanceof BlockItem) && !(crystals.get() && held.isOf(Items.END_CRYSTAL))) return;
        ((MinecraftClientAccessor) mc).loot$setItemUseCooldown(0);
    }
}
