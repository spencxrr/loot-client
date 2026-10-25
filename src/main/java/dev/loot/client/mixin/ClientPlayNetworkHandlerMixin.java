package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.player.Velocity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
    private void loot$velocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // Packet handlers first run on the netty thread and re-queue themselves; only act on the main-thread pass.
        if (!mc.isOnThread() || mc.player == null || packet.getEntityId() != mc.player.getId()) return;
        Velocity velocity = LootClient.modules().get(Velocity.class);
        if (!velocity.isEnabled()) return;
        double h = velocity.horizontal.get() / 100.0;
        double v = velocity.vertical.get() / 100.0;
        if (h != 0 || v != 0) {
            mc.player.setVelocity(packet.getVelocityX() * h, packet.getVelocityY() * v, packet.getVelocityZ() * h);
        }
        ci.cancel();
    }
}
