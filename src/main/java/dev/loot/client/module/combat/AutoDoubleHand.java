package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.InvUtil;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;

/** Puts a totem in your main hand too when you're in danger, so a second pop doesn't kill you. */
public class AutoDoubleHand extends Module {
    private final NumberSetting health = add(new NumberSetting("Health", "Switch at or below this health.", 8, 1, 20, 1));
    private final BoolSetting crystalCheck = add(new BoolSetting("NearCrystals", "Also switch when a crystal is within 6 blocks.", true));

    public AutoDoubleHand() {
        super("Auto Double Hand", "Swaps to a totem in your main hand when low.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (InvUtil.holding(Items.TOTEM_OF_UNDYING)) return;
        boolean danger = mc.player.getHealth() + mc.player.getAbsorptionAmount() <= health.get();
        if (!danger && crystalCheck.get()) {
            for (var e : mc.world.getEntities()) {
                if (e instanceof EndCrystalEntity && e.squaredDistanceTo(mc.player) < 36) {
                    danger = true;
                    break;
                }
            }
        }
        if (!danger) return;
        int slot = InvUtil.findHotbar(Items.TOTEM_OF_UNDYING);
        if (slot != -1) InvUtil.select(slot);
    }
}
