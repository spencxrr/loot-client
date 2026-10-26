package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.render.NoRender;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void loot$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        NoRender noRender = LootClient.modules().get(NoRender.class);
        if (noRender.isEnabled() && noRender.hurtCam.get()) ci.cancel();
    }

    @ModifyArg(method = "getBasicProjectionMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"), index = 1, require = 0)
    private float loot$aspect(float aspect) {
        var ar = LootClient.modules().get(dev.loot.client.module.render.AspectRatio.class);
        return ar.isEnabled() ? ar.ratio() : aspect;
    }
}
