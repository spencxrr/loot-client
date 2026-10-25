package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.render.NoRender;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin {
    private boolean loot$hideWeather() {
        if (!((Object) this instanceof ClientWorld)) return false;
        NoRender noRender = LootClient.modules().get(NoRender.class);
        return noRender.isEnabled() && noRender.weather.get();
    }

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void loot$noRain(float delta, CallbackInfoReturnable<Float> cir) {
        if (loot$hideWeather()) cir.setReturnValue(0f);
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void loot$noThunder(float delta, CallbackInfoReturnable<Float> cir) {
        if (loot$hideWeather()) cir.setReturnValue(0f);
    }
}
