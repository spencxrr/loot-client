package dev.loot.client.gui;

import dev.loot.client.config.Config;
import dev.loot.client.setting.BlockListSetting;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Searchable list of every block in the game. Click a block to add or remove it from the
 * setting; selected ones are listed first. Esc goes back to the menu.
 */
public class BlockPickerScreen extends Screen {
    private static final int W = 260, ROW = 18, SEARCH = 30;

    private final Screen parent;
    private final BlockListSetting setting;
    private String query = "";
    private int scroll;
    private float scale = 1f;
    private int vw, vh;

    public BlockPickerScreen(Screen parent, BlockListSetting setting) {
        super(Text.literal("Pick blocks"));
        this.parent = parent;
        this.setting = setting;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void updateScale() {
        scale = (float) MathHelper.clamp(super.height / 540.0, 0.1, 4.0);
        vw = (int) (super.width / scale);
        vh = (int) (super.height / scale);
    }

    private List<Block> cached;
    private String cachedKey;

    private List<Block> matches() {
        String key = query + "|" + setting.get().hashCode();
        if (key.equals(cachedKey)) return cached;
        cachedKey = key;
        cached = compute();
        return cached;
    }

    private List<Block> compute() {
        String q = query.toLowerCase(Locale.ROOT).replace(' ', '_');
        List<Block> selected = new ArrayList<>(), rest = new ArrayList<>();
        for (Block b : Registries.BLOCK) {
            String id = Registries.BLOCK.getId(b).getPath();
            String name = b.getName().getString().toLowerCase(Locale.ROOT).replace(' ', '_');
            if (!q.isEmpty() && !id.contains(q) && !name.contains(q)) continue;
            if (b.getDefaultState().isAir()) continue;
            (setting.contains(b) ? selected : rest).add(b);
        }
        selected.addAll(rest);
        return selected;
    }

    private int x0() {
        return (vw - W) / 2;
    }

    private int listTop() {
        return 20 + 20 + SEARCH;
    }

    private int listHeight() {
        return vh - listTop() - 30;
    }

    @Override
    public void render(DrawContext ctx, int rawX, int rawY, float delta) {
        updateScale();
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.scale(scale, scale);
        int mx = (int) (rawX / scale), my = (int) (rawY / scale);
        ctx.fill(0, 0, vw, vh, 0x99000000 | 0x06070D);

        int x = x0(), y = 20;
        UI.roundRect(ctx, x, y, x + W, y + 20, 5, Theme.SURFACE_RAISED, true, false);
        UI.roundRect(ctx, x + 8, y + 8, x + 12, y + 12, 2, Theme.ACCENT);
        UI.drawBold(ctx, "Pick blocks", x + 18, y + 5, Theme.TEXT);
        UI.drawTextRight(ctx, setting.get().size() + " selected", x + W - 8, y + 5, Theme.TEXT_FAINT);
        y += 20;
        UI.roundRect(ctx, x, y, x + W, y + SEARCH, 0, Theme.SURFACE);
        UI.roundRect(ctx, x + 6, y + 6, x + W - 6, y + SEARCH - 6, 4, Theme.SETTINGS_BG);
        String shown = query.isEmpty() ? "Type to search any block..." : query + ((System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "");
        UI.drawText(ctx, shown, x + 12, y + 11, query.isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT);

        List<Block> list = matches();
        int top = listTop(), h = listHeight();
        int content = list.size() * ROW;
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, content - h));
        UI.roundRect(ctx, x, top, x + W, top + h + 5, 5, Theme.SURFACE, false, true);
        ctx.enableScissor(x, top, x + W, top + h);
        int ry = top - scroll;
        for (Block b : list) {
            if (ry + ROW >= top && ry <= top + h) {
                boolean on = setting.contains(b);
                boolean hover = mx >= x && mx < x + W && my >= ry && my < ry + ROW && my >= top && my < top + h;
                if (hover) ctx.fill(x, ry, x + W, ry + ROW, Theme.ROW_HOVER);
                else if (on) ctx.fill(x, ry, x + W, ry + ROW, Theme.ROW_ACTIVE);
                if (on) ctx.fill(x, ry + 3, x + 2, ry + ROW - 3, Theme.ACCENT);
                ItemStack icon = new ItemStack(b.asItem());
                if (!icon.isEmpty()) {
                    m.pushMatrix();
                    m.translate(x + 8, ry + 1);
                    ctx.drawItem(icon, 0, 0);
                    m.popMatrix();
                }
                UI.drawText(ctx, b.getName().getString(), x + 30, ry + 5, on ? Theme.ACCENT : Theme.TEXT);
                UI.drawTextRight(ctx, Registries.BLOCK.getId(b).getPath(), x + W - 28, ry + 5, Theme.TEXT_FAINT);
                UI.toggle(ctx, x + W - 24, ry + 5, on ? 1 : 0);
            }
            ry += ROW;
        }
        ctx.disableScissor();
        if (list.isEmpty()) UI.drawText(ctx, "No blocks match", x + 10, top + 6, Theme.TEXT_FAINT);
        UI.drawText(ctx, "Click to add or remove. Esc to go back.", x, vh - 18, Theme.TEXT_FAINT);
        m.popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        updateScale();
        double mx = click.x() / scale, my = click.y() / scale;
        int x = x0(), top = listTop(), h = listHeight();
        if (mx >= x && mx < x + W && my >= top && my < top + h) {
            int index = (int) ((my - top + scroll) / ROW);
            List<Block> list = matches();
            if (index >= 0 && index < list.size()) {
                setting.toggle(list.get(index));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scroll -= (int) (vertical * ROW * 2);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            scroll = 0;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) return false;
        query += input.asString();
        scroll = 0;
        return true;
    }

    @Override
    public void close() {
        Config.save();
        client.setScreen(parent);
    }
}
