package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.misc.FakeStats;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Fake Stats: measures the swapped text, so the sidebar and tab list size and align to it. */
@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {
    private static FakeStats loot$fakeStats() {
        return LootClient.modules() == null ? null : LootClient.modules().get(FakeStats.class);
    }

    @ModifyVariable(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String loot$widthString(String text) {
        FakeStats f = loot$fakeStats();
        return f == null ? text : f.apply(text);
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/StringVisitable;)I", at = @At("HEAD"), argsOnly = true)
    private StringVisitable loot$widthVisitable(StringVisitable text) {
        FakeStats f = loot$fakeStats();
        return f == null ? text : f.apply(text);
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/OrderedText;)I", at = @At("HEAD"), argsOnly = true)
    private OrderedText loot$widthOrdered(OrderedText text) {
        FakeStats f = loot$fakeStats();
        return f == null ? text : f.apply(text);
    }
}
