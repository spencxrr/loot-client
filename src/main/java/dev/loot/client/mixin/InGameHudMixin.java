package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.render.Crosshair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void loot$customCrosshair(DrawContext ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (LootClient.modules().get(Crosshair.class).isEnabled()) ci.cancel();
    }
}
