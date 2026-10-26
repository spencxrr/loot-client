package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.combat.HitBox;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getTargetingMargin", at = @At("RETURN"), cancellable = true)
    private void loot$hitbox(CallbackInfoReturnable<Float> cir) {
        if (LootClient.modules() == null) return;
        HitBox hb = LootClient.modules().get(HitBox.class);
        if (!hb.isEnabled()) return;
        Entity self = (Entity) (Object) this;
        if (self == MinecraftClient.getInstance().player) return;
        if (hb.playersOnly.get() && !(self instanceof PlayerEntity)) return;
        cir.setReturnValue(cir.getReturnValue() + hb.expand.getFloat());
    }
}
