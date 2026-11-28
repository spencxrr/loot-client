package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.misc.FakeStats;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Fake Stats: swaps stat numbers in every piece of text the HUD and screens draw. */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    private static FakeStats loot$fakeStats() {
        return LootClient.modules() == null ? null : LootClient.modules().get(FakeStats.class);
    }

    @ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V", at = @At("HEAD"), argsOnly = true)
    private String loot$fakeString(String text) {
        FakeStats f = loot$fakeStats();
        return f == null ? text : f.apply(text);
    }

    @ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V", at = @At("HEAD"), argsOnly = true)
    private Text loot$fakeText(Text text) {
        FakeStats f = loot$fakeStats();
        return f == null ? text : f.apply(text);
    }

    @ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V", at = @At("HEAD"), argsOnly = true)
    private OrderedText loot$fakeOrdered(OrderedText text) {
        FakeStats f = loot$fakeStats();
        return f == null ? text : f.apply(text);
    }
}
