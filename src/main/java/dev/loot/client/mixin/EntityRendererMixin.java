package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.render.NameTags;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    // Hide the vanilla label for players while Name Tags draws its own.
    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void loot$hideVanillaTag(Entity entity, CallbackInfoReturnable<Text> cir) {
        if (entity instanceof PlayerEntity && LootClient.modules().get(NameTags.class).isEnabled()) cir.setReturnValue(null);
    }
}
