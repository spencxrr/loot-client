package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.render.Freecam;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    // While Freecam is on the movement keys fly the camera, so the real player gets no input at all.
    @Inject(method = "tick", at = @At("TAIL"))
    private void loot$freezeInFreecam(CallbackInfo ci) {
        if (LootClient.modules() != null && LootClient.modules().get(Freecam.class).isEnabled()) {
            playerInput = PlayerInput.DEFAULT;
            movementVector = Vec2f.ZERO;
        }
    }
}
