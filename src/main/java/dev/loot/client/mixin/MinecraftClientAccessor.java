package dev.loot.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("attackCooldown")
    int loot$getAttackCooldown();

    @Accessor("attackCooldown")
    void loot$setAttackCooldown(int value);

    @Accessor("itemUseCooldown")
    void loot$setItemUseCooldown(int value);

    @Invoker("doAttack")
    boolean loot$doAttack();

    @Invoker("doItemUse")
    void loot$doItemUse();
}
