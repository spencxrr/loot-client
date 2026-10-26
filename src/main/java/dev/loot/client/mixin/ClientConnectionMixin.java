package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void loot$onSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (LootClient.modules() != null && LootClient.modules().firePacketSend(packet)) ci.cancel();
    }

    @Inject(method = "handlePacket", at = @At("HEAD"), cancellable = true)
    private static void loot$onReceive(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        if (LootClient.modules() != null && LootClient.modules().firePacketReceive(packet)) ci.cancel();
    }
}
