package dev.loot.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.function.Predicate;

public final class InvUtil {
    private InvUtil() {}

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    /** Hotbar slot (0-8) holding a matching item, or -1. */
    public static int findHotbar(Predicate<ItemStack> filter) {
        PlayerInventory inv = mc().player.getInventory();
        for (int i = 0; i < 9; i++) if (filter.test(inv.getStack(i))) return i;
        return -1;
    }

    public static int findHotbar(Item item) {
        return findHotbar(s -> s.isOf(item));
    }

    /** Main inventory slot (9-35) holding a matching item, or -1. */
    public static int findInventory(Predicate<ItemStack> filter) {
        PlayerInventory inv = mc().player.getInventory();
        for (int i = 9; i < 36; i++) if (filter.test(inv.getStack(i))) return i;
        return -1;
    }

    /** Any slot 0-35, hotbar first. */
    public static int findAny(Predicate<ItemStack> filter) {
        int slot = findHotbar(filter);
        return slot != -1 ? slot : findInventory(filter);
    }

    public static int selected() {
        return mc().player.getInventory().getSelectedSlot();
    }

    public static void select(int hotbarSlot) {
        if (hotbarSlot >= 0 && hotbarSlot < 9) mc().player.getInventory().setSelectedSlot(hotbarSlot);
    }

    public static boolean holding(Item item) {
        return mc().player.getMainHandStack().isOf(item);
    }

    /** Converts a PlayerInventory index to the player screen handler slot id. */
    public static int toHandlerSlot(int invIndex) {
        if (invIndex < 9) return 36 + invIndex;
        if (invIndex == 40) return 45;
        if (invIndex >= 36 && invIndex <= 39) return 8 - (invIndex - 36); // boots..helmet -> slots 8..5
        return invIndex;
    }

    /** Swaps an inventory index with a hotbar slot (or 40 for offhand) via the SWAP click. */
    public static void swap(int invIndex, int hotbarOrOffhand) {
        var mc = mc();
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, toHandlerSlot(invIndex), hotbarOrOffhand, SlotActionType.SWAP, mc.player);
    }

    /** Selects the item, uses it once, then restores the previous slot. Returns false if not found. */
    public static boolean useHotbarItem(Item item, boolean swapBack) {
        int slot = findHotbar(item);
        if (slot == -1) return false;
        int prev = selected();
        select(slot);
        mc().interactionManager.interactItem(mc().player, Hand.MAIN_HAND);
        mc().player.swingHand(Hand.MAIN_HAND);
        if (swapBack) select(prev);
        return true;
    }
}
