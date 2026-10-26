package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

/** Opens your elytra automatically as soon as you start falling. */
public class ElytraGlide extends Module {
    private final NumberSetting fall = add(new NumberSetting("FallDistance", "Blocks fallen before deploying.", 1.5, 0.5, 10, 0.5));

    private int cooldown;

    public ElytraGlide() {
        super("Elytra Glide", "Auto-deploys your elytra when you fall.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (cooldown > 0) cooldown--;
        if (mc.player.isGliding() || mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.getAbilities().flying) return;
        if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return;
        if (mc.player.fallDistance < fall.get() || cooldown > 0) return;
        // checkGliding() runs vanilla's own "can I open my elytra" check and starts gliding client-side.
        if (mc.player.checkGliding()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
        cooldown = 10;
    }
}
