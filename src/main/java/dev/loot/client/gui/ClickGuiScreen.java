package dev.loot.client.gui;

import dev.loot.client.LootClient;
import dev.loot.client.config.Config;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BlockListSetting;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.ModeSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.setting.Setting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Category panels. Left click toggles a module, right click expands its settings,
 * middle click starts keybinding. Drag a panel by its header.
 */
public class ClickGuiScreen extends Screen {
    private static final int WIDTH = 112;
    private static final int HEADER = 16;
    private static final int ROW = 13;

    private static final int BG = 0xE0141418;
    private static final int HEADER_BG = 0xFF1E1E24;
    private static final int ACCENT = 0xFFFFAA00;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int MUTED = 0xFF8A8A94;

    private static final List<Panel> PANELS = new ArrayList<>();
    private static final Set<Module> EXPANDED = new HashSet<>();

    private Panel dragging;
    private int dragOffX, dragOffY;
    private NumberSetting sliding;
    private int slideX;
    private Module binding;

    private static final class Panel {
        final Category category;
        int x, y;
        boolean open = true;

        Panel(Category category, int x, int y) {
            this.category = category;
            this.x = x;
            this.y = y;
        }
    }

    public ClickGuiScreen() {
        super(Text.literal("Loot Client"));
        if (PANELS.isEmpty()) {
            int x = 8;
            for (Category c : Category.values()) {
                PANELS.add(new Panel(c, x, 8));
                x += WIDTH + 6;
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x60000000);
        if (sliding != null) applySlider(mouseX);

        for (Panel p : PANELS) {
            ctx.fill(p.x, p.y, p.x + WIDTH, p.y + HEADER, HEADER_BG);
            ctx.fill(p.x, p.y + HEADER - 1, p.x + WIDTH, p.y + HEADER, ACCENT);
            ctx.drawTextWithShadow(textRenderer, p.category.displayName, p.x + 5, p.y + 4, TEXT);
            ctx.drawTextWithShadow(textRenderer, p.open ? "-" : "+", p.x + WIDTH - 10, p.y + 4, MUTED);
            if (!p.open) continue;

            int y = p.y + HEADER;
            for (Module m : LootClient.modules().getModules(p.category)) {
                boolean hover = inside(mouseX, mouseY, p.x, y, WIDTH, ROW);
                ctx.fill(p.x, y, p.x + WIDTH, y + ROW, hover ? 0xE0262630 : BG);
                if (m.isEnabled()) ctx.fill(p.x, y, p.x + 2, y + ROW, ACCENT);
                String label = binding == m ? "Press a key..." : m.getName();
                ctx.drawTextWithShadow(textRenderer, label, p.x + 6, y + 3, m.isEnabled() ? ACCENT : TEXT);
                if (m.getKey() != GLFW.GLFW_KEY_UNKNOWN && binding != m) {
                    String key = keyName(m.getKey());
                    ctx.drawTextWithShadow(textRenderer, key, p.x + WIDTH - 4 - textRenderer.getWidth(key), y + 3, MUTED);
                }
                if (hover) hoverText = m.getDescription();
                y += ROW;

                if (!EXPANDED.contains(m)) continue;
                for (Setting<?> s : m.getSettings()) {
                    ctx.fill(p.x, y, p.x + WIDTH, y + ROW, 0xE00E0E12);
                    renderSetting(ctx, s, p.x + 8, y, WIDTH - 12);
                    if (inside(mouseX, mouseY, p.x, y, WIDTH, ROW)) hoverText = s.getDescription();
                    y += ROW;
                }
            }
            ctx.fill(p.x, y, p.x + WIDTH, y + 2, HEADER_BG);
        }

        if (hoverText != null) {
            ctx.drawTextWithShadow(textRenderer, hoverText, 6, height - 12, MUTED);
            hoverText = null;
        }
    }

    private String hoverText;

    private void renderSetting(DrawContext ctx, Setting<?> s, int x, int y, int w) {
        if (s instanceof NumberSetting n) {
            double pct = (n.get() - n.getMin()) / (n.getMax() - n.getMin());
            ctx.fill(x, y + ROW - 3, x + (int) (w * pct), y + ROW - 1, ACCENT);
        }
        String value = s instanceof BlockListSetting b ? b.get().size() + " blocks" : s.display();
        int valueColor = s instanceof BoolSetting b ? (b.get() ? 0xFF5BE37A : 0xFFE35B5B) : ACCENT;
        ctx.drawTextWithShadow(textRenderer, s.getName(), x, y + 2, MUTED);
        ctx.drawTextWithShadow(textRenderer, value, x + w - textRenderer.getWidth(value), y + 2, valueColor);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        for (Panel p : PANELS) {
            if (inside(mx, my, p.x, p.y, WIDTH, HEADER)) {
                if (button == 0) {
                    dragging = p;
                    dragOffX = (int) mx - p.x;
                    dragOffY = (int) my - p.y;
                } else if (button == 1) {
                    p.open = !p.open;
                }
                return true;
            }
            if (!p.open) continue;

            int y = p.y + HEADER;
            for (Module m : LootClient.modules().getModules(p.category)) {
                if (inside(mx, my, p.x, y, WIDTH, ROW)) {
                    switch (button) {
                        case 0 -> m.toggle();
                        case 1 -> {
                            if (!EXPANDED.remove(m) && !m.getSettings().isEmpty()) EXPANDED.add(m);
                        }
                        case 2 -> binding = m;
                        default -> {}
                    }
                    return true;
                }
                y += ROW;
                if (!EXPANDED.contains(m)) continue;
                for (Setting<?> s : m.getSettings()) {
                    if (inside(mx, my, p.x, y, WIDTH, ROW)) {
                        clickSetting(s, button, p.x + 8);
                        return true;
                    }
                    y += ROW;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void clickSetting(Setting<?> s, int button, int x) {
        if (s instanceof BoolSetting b) b.toggle();
        else if (s instanceof ModeSetting mode) mode.cycle(button == 0);
        else if (s instanceof NumberSetting n && button == 0) {
            sliding = n;
            slideX = x;
        }
        // Block lists are edited with .set <module> Blocks add <id>; too fiddly for a GUI row.
    }

    private void applySlider(double mx) {
        double pct = MathHelper.clamp((mx - slideX) / (WIDTH - 12), 0, 1);
        sliding.set(sliding.getMin() + pct * (sliding.getMax() - sliding.getMin()));
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging != null) {
            dragging.x = (int) click.x() - dragOffX;
            dragging.y = (int) click.y() - dragOffY;
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = null;
        sliding = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (binding != null) {
            boolean clear = keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE;
            binding.setKey(clear ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
            binding = null;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        Config.save();
    }

    private static String keyName(int key) {
        return InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
