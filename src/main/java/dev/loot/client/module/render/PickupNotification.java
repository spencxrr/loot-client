package dev.loot.client.module.render;

import dev.loot.client.gui.Theme;
import dev.loot.client.gui.UI;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Little "+12 Diamond" cards on the right side of the screen for everything you pick up. */
public class PickupNotification extends Module {
    private final NumberSetting duration = add(new NumberSetting("Duration", "Seconds each card stays.", 3, 1, 10, 0.5));
    private final NumberSetting max = add(new NumberSetting("Max", "Cards shown at once.", 6, 1, 15, 1));

    private static final class Card {
        final ItemStack stack;
        int count;
        long time;

        Card(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
            this.time = System.currentTimeMillis();
        }
    }

    private final ConcurrentLinkedQueue<Card> incoming = new ConcurrentLinkedQueue<>();
    private final List<Card> cards = new ArrayList<>();

    public PickupNotification() {
        super("Pickup Notification", "Shows what you pick up.", Category.RENDER);
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (!(packet instanceof ItemPickupAnimationS2CPacket p) || mc.player == null || mc.world == null) return false;
        if (p.getCollectorEntityId() != mc.player.getId()) return false;
        // Read the item now: by the time the main thread handles this packet the entity is gone.
        Entity e = mc.world.getEntityById(p.getEntityId());
        if (e instanceof ItemEntity item) incoming.add(new Card(item.getStack().copyWithCount(1), p.getStackAmount()));
        return false;
    }

    @Override
    public void onTick() {
        Card c;
        while ((c = incoming.poll()) != null) {
            Card add = c;
            Card same = cards.stream().filter(k -> ItemStack.areItemsAndComponentsEqual(k.stack, add.stack)).findFirst().orElse(null);
            if (same != null) {
                same.count += add.count;
                same.time = add.time;
            } else {
                cards.add(0, add);
            }
        }
        long life = (long) (duration.get() * 1000);
        cards.removeIf(k -> System.currentTimeMillis() - k.time > life);
        while (cards.size() > max.getInt()) cards.remove(cards.size() - 1);
    }

    @Override
    public void onRenderHud(DrawContext ctx, float tickDelta) {
        if (mc.options.hudHidden) return;
        long now = System.currentTimeMillis();
        long life = (long) (duration.get() * 1000);
        int y = ctx.getScaledWindowHeight() / 2 - 20;
        int right = ctx.getScaledWindowWidth() - 4;
        for (Card k : cards) {
            String label = "+" + k.count + " " + k.stack.getName().getString();
            int w = UI.width(label) + 28;
            float age = (now - k.time) / (float) life;
            float slide = Math.min(1f, (now - k.time) / 150f);
            int x = right - (int) (w * slide);
            int alpha = age > 0.8f ? (int) (255 * (1 - (age - 0.8f) / 0.2f)) : 255;
            int a = (Theme.SURFACE >>> 24) * alpha / 255;
            UI.roundRect(ctx, x, y, x + w, y + 18, 5, (Theme.SURFACE & 0xFFFFFF) | (a << 24));
            ctx.fill(x + 2, y + 4, x + 3, y + 14, (Theme.ACCENT & 0xFFFFFF) | (alpha << 24));
            ctx.drawItem(k.stack, x + 5, y + 1);
            UI.drawText(ctx, label, x + 24, y + 5, (Theme.TEXT & 0xFFFFFF) | (alpha << 24));
            y += 20;
        }
    }
}
