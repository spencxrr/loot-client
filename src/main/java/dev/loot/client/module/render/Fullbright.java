package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Applies a client-side night vision effect. The server never hears about it, and it
 * survives respawns because we re-apply it every tick if it's missing.
 */
public class Fullbright extends Module {
    public Fullbright() {
        super("Fullbright", "See in the dark.", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (!mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false));
        }
    }

    @Override
    public void onDisable() {
        if (inGame()) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }
}
