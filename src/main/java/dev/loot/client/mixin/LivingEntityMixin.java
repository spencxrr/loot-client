package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.render.SwingSpeed;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
    private void loot$swingSpeed(CallbackInfoReturnable<Integer> cir) {
        SwingSpeed swing = LootClient.modules().get(SwingSpeed.class);
        if (swing.isEnabled() && (Object) this == MinecraftClient.getInstance().player) cir.setReturnValue(swing.duration(cir.getReturnValue()));
    }
}
