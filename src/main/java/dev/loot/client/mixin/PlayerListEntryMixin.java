package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.misc.SkinProtect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {
    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void loot$skinProtect(CallbackInfoReturnable<SkinTextures> cir) {
        if (LootClient.modules() == null) return;
        SkinProtect sp = LootClient.modules().get(SkinProtect.class);
        if (!sp.isEnabled()) return;
        var mc = MinecraftClient.getInstance();
        boolean self = mc.player != null && getProfile().id().equals(mc.player.getUuid());
        if (self ? sp.self.get() : sp.others.get()) cir.setReturnValue(DefaultSkinHelper.getSkinTextures(getProfile()));
    }
}
