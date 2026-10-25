package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.render.NoRender;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void loot$noParticles(Particle particle, CallbackInfo ci) {
        NoRender noRender = LootClient.modules().get(NoRender.class);
        if (noRender.isEnabled() && noRender.particles.get()) ci.cancel();
    }
}
