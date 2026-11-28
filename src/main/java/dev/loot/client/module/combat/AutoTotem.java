package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.ModeSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.InvUtil;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Keeps a totem of undying in your offhand.
 *
 * Re-equips the instant a pop packet arrives instead of waiting for the next tick, and checks
 * that the server actually accepted the move: if the offhand isn't a totem once the server has
 * had time to answer (based on your ping), the attempt counts as rejected and it retries, moving
 * on to a different way of getting the totem there after a few failures. Three equip paths:
 *  - Swap: inventory click with the offhand swap button (vanilla's "F over a slot").
 *  - Pickup: pick the totem up and drop it on the offhand slot (needs the player inventory).
 *  - Hotkey: select a hotbar totem and send the swap-hands action (vanilla's F key), which isn't
 *    an inventory click at all, so it gets past anything that only looks at clicks.
 * Works with chests and other containers open (Swap and Hotkey), with a full inventory, and
 * keeps an optional spare totem in a hotbar slot so Hotkey always has one to use.
 */
public class AutoTotem extends Module {
    private static final int SWAP = 0, PICKUP = 1, HOTKEY = 2;
    private static final String[] METHOD_NAMES = {"Swap", "Pickup", "Hotkey"};

    private final ModeSetting mode = add(new ModeSetting("Mode", "Always keep a totem, or only once health drops (lets you hold other things).", "Always", "Always", "Health"));
    private final NumberSetting health = add(new NumberSetting("Health", "Health + absorption at or below which Health mode equips.", 12, 1, 36, 1));
    private final ModeSetting method = add(new ModeSetting("Method", "How totems get moved. Auto tries each path and sticks with the one the server accepts.", "Auto", "Auto", "Swap", "Pickup", "Hotkey"));
    private final NumberSetting retries = add(new NumberSetting("Retries", "Rejected attempts before Auto moves on to the next method.", 2, 1, 6, 1));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Extra ticks to wait between attempts.", 0, 0, 20, 1));
    private final NumberSetting popDelay = add(new NumberSetting("PopDelay", "Milliseconds to wait after a pop before re-equipping (0 = instant).", 0, 0, 500, 10));
    private final NumberSetting confirm = add(new NumberSetting("Confirm", "Ticks to wait for the server to accept a move (0 = from your ping).", 0, 0, 40, 1));
    private final BoolSetting inScreens = add(new BoolSetting("InScreens", "Keep working with chests and other containers open.", true));
    private final BoolSetting backup = add(new BoolSetting("HotbarBackup", "Keep a spare totem in a hotbar slot for the Hotkey method.", true));
    private final NumberSetting backupSlot = add(new NumberSetting("BackupSlot", "Hotbar slot for the spare totem (1-9).", 9, 1, 9, 1));
    private final BoolSetting respectUse = add(new BoolSetting("RespectUse", "In Health mode, don't take the offhand while you're eating or using it.", true));

    private volatile boolean popped;
    private volatile long popTime;

    private int current = SWAP;
    private int failures;
    /** Tick count when the last attempt can be judged, or -1 when nothing is in flight. */
    private int awaitUntil = -1;
    private int awaitMethod;
    private int cooldown;
    private int ticks;

    public AutoTotem() {
        super("Auto Totem", "Keeps a totem in your offhand, fast and lag/blocker resistant.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        awaitUntil = -1;
        failures = 0;
        cooldown = 0;
        popped = false;
    }

    // ---- triggers ----

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        // network thread: status 35 is the totem pop animation, ours means the offhand is now empty
        if (packet instanceof EntityStatusS2CPacket p && p.getStatus() == 35 && mc.world != null && mc.player != null
            && p.getEntity(mc.world) == mc.player) {
            popped = true;
            popTime = System.currentTimeMillis();
            mc.execute(() -> {
                if (isEnabled() && inGame()) run(true);
            });
        }
        return false;
    }

    @Override
    public void onTick() {
        ticks++;
        if (cooldown > 0) cooldown--;
        run(false);
    }

    // ---- core ----

    private void run(boolean fromPop) {
        if (mc.player.isDead() || mc.player.isSpectator() || mc.currentScreen instanceof CreativeInventoryScreen) return;
        boolean hasTotem = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        // right after a pop the client may still show the old totem until the slot update lands
        boolean offhandEmpty = !hasTotem || popped && fromPop;

        if (awaitUntil != -1) {
            if (fromPop) {
                awaitUntil = -1; // a fresh pop beats waiting on the previous attempt
            } else if (ticks < awaitUntil) {
                return;
            } else {
                judge(hasTotem);
            }
        }
        if (!offhandEmpty) {
            popped = false;
            maintainBackup();
            return;
        }
        if (!wanted()) return;
        if (popped && popDelay.get() > 0 && System.currentTimeMillis() - popTime < popDelay.get()) return;
        if (cooldown > 0 && !fromPop) return;
        attempt();
    }

    private boolean wanted() {
        if (mode.is("Always") || popped) return true;
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (hp > health.get()) return false;
        return !(respectUse.get() && mc.player.isUsingItem() && mc.player.getActiveHand() == net.minecraft.util.Hand.OFF_HAND);
    }

    private void judge(boolean hasTotem) {
        awaitUntil = -1;
        if (hasTotem) {
            failures = 0;
            current = awaitMethod;
            popped = false;
            return;
        }
        // the server put it back: count it against this method and maybe try the next one
        if (++failures >= retries.getInt() && method.is("Auto")) {
            current = (awaitMethod + 1) % 3;
            failures = 0;
        }
    }

    private void attempt() {
        int src = findTotem();
        if (src == -1) return;
        boolean ownInventory = mc.currentScreen == null || mc.player.currentScreenHandler == mc.player.playerScreenHandler;
        if (!ownInventory && !inScreens.get()) return;

        int m = method.is("Auto") ? current : switch (method.get()) {
            case "Pickup" -> PICKUP;
            case "Hotkey" -> HOTKEY;
            default -> SWAP;
        };
        // fall through to a method that can work in the current situation
        for (int i = 0; i < 3; i++) {
            int tryMethod = (m + i) % 3;
            if (!method.is("Auto") && i > 0) break;
            if (perform(tryMethod, src, ownInventory)) {
                awaitMethod = tryMethod;
                awaitUntil = ticks + confirmTicks();
                cooldown = delay.getInt();
                return;
            }
        }
    }

    private boolean perform(int m, int src, boolean ownInventory) {
        return switch (m) {
            case SWAP -> swapClick(src);
            case PICKUP -> ownInventory && pickup(src);
            default -> hotkey(src);
        };
    }

    /** Any slot 0-35 holding a totem, preferring the main inventory so the hotbar backup survives. */
    private int findTotem() {
        int backupIdx = backup.get() ? backupSlot.getInt() - 1 : -1;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 9; i < 36; i++) if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        for (int i = 0; i < 9; i++) if (i != backupIdx && inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        return backupIdx != -1 && inv.getStack(backupIdx).isOf(Items.TOTEM_OF_UNDYING) ? backupIdx : -1;
    }

    /** The slot id of a player inventory index in whatever screen handler is open, or -1. */
    private int handlerSlot(ScreenHandler handler, int invIndex) {
        if (handler == mc.player.playerScreenHandler) return InvUtil.toHandlerSlot(invIndex);
        for (Slot slot : handler.slots) {
            if (slot.inventory == mc.player.getInventory() && slot.getIndex() == invIndex) return slot.id;
        }
        return -1;
    }

    private boolean swapClick(int src) {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!handler.getCursorStack().isEmpty() && handler != mc.player.playerScreenHandler) return false;
        int slot = handlerSlot(handler, src);
        if (slot == -1) return false;
        // button 40 = the offhand, works in every screen handler, not just the inventory
        mc.interactionManager.clickSlot(handler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
        return true;
    }

    private boolean pickup(int src) {
        ScreenHandler handler = mc.player.playerScreenHandler;
        int sync = handler.syncId;
        var cursor = handler.getCursorStack();
        if (!cursor.isEmpty()) {
            if (!cursor.isOf(Items.TOTEM_OF_UNDYING)) return false;
            mc.interactionManager.clickSlot(sync, 45, 0, SlotActionType.PICKUP, mc.player);
            return true;
        }
        int from = InvUtil.toHandlerSlot(src);
        mc.interactionManager.clickSlot(sync, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, 45, 0, SlotActionType.PICKUP, mc.player);
        // whatever was in the offhand is on the cursor now; park it where the totem came from
        if (!handler.getCursorStack().isEmpty()) mc.interactionManager.clickSlot(sync, from, 0, SlotActionType.PICKUP, mc.player);
        return true;
    }

    private boolean hotkey(int src) {
        int hotbar = src < 9 ? src : -1;
        if (hotbar == -1) {
            int b = backupSlot.getInt() - 1;
            if (mc.player.getInventory().getStack(b).isOf(Items.TOTEM_OF_UNDYING)) hotbar = b;
            else hotbar = InvUtil.findHotbar(Items.TOTEM_OF_UNDYING);
        }
        if (hotbar == -1) return false;
        var net = mc.getNetworkHandler();
        int prev = InvUtil.selected();
        if (hotbar != prev) net.sendPacket(new UpdateSelectedSlotC2SPacket(hotbar));
        net.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        if (hotbar != prev) net.sendPacket(new UpdateSelectedSlotC2SPacket(prev));
        return true;
    }

    /** Keeps a spare totem in the hotbar backup slot, only while nothing urgent is going on. */
    private void maintainBackup() {
        if (!backup.get() || cooldown > 0 || awaitUntil != -1) return;
        if (mc.currentScreen != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        int b = backupSlot.getInt() - 1;
        PlayerInventory inv = mc.player.getInventory();
        if (inv.getStack(b).isOf(Items.TOTEM_OF_UNDYING)) return;
        // don't shove a totem over something important unless the slot is free or holds junk we can swap
        int src = InvUtil.findInventory(s -> s.isOf(Items.TOTEM_OF_UNDYING));
        if (src == -1 || !inv.getStack(b).isEmpty() && InvUtil.findHotbar(Items.TOTEM_OF_UNDYING) != -1) return;
        InvUtil.swap(src, b);
        cooldown = Math.max(2, delay.getInt());
    }

    private int confirmTicks() {
        if (confirm.getInt() > 0) return confirm.getInt();
        int ping = 100;
        var net = mc.getNetworkHandler();
        if (net != null) {
            PlayerListEntry e = net.getPlayerListEntry(mc.player.getUuid());
            if (e != null && e.getLatency() > 0) ping = e.getLatency();
        }
        // round trip plus a server tick or two of slack
        return Math.min(40, (int) Math.ceil(ping / 50.0) + 3);
    }

    @Override
    public String getInfo() {
        if (!inGame()) return null;
        int count = mc.player.getInventory().count(Items.TOTEM_OF_UNDYING);
        return method.is("Auto") ? count + " " + METHOD_NAMES[current] : String.valueOf(count);
    }
}
