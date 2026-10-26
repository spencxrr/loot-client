package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.misc.NameProtect;
import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(TextVisitFactory.class)
public abstract class TextVisitFactoryMixin {
    @ModifyArg(method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/text/TextVisitFactory;visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z", ordinal = 0),
        index = 0)
    private static String loot$nameProtect(String text) {
        if (LootClient.modules() == null) return text;
        return LootClient.modules().get(NameProtect.class).apply(text);
    }
}
