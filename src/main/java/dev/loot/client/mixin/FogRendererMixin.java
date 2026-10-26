package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.render.Fog;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    private static final String APPLY = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V";

    private static Fog loot$fog() {
        if (LootClient.modules() == null) return null;
        Fog fog = LootClient.modules().get(Fog.class);
        return fog.isEnabled() ? fog : null;
    }

    @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, require = 0)
    private Vector4f loot$color(Vector4f color) {
        Fog fog = loot$fog();
        return fog == null ? color : fog.color(color);
    }

    @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float loot$envStart(float v) {
        Fog fog = loot$fog();
        return fog == null ? v : fog.distance(v);
    }

    @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private float loot$envEnd(float v) {
        Fog fog = loot$fog();
        return fog == null ? v : fog.distance(v);
    }

    @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private float loot$renderStart(float v) {
        Fog fog = loot$fog();
        return fog == null ? v : fog.distance(v);
    }

    @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private float loot$renderEnd(float v) {
        Fog fog = loot$fog();
        return fog == null ? v : fog.distance(v);
    }
}
