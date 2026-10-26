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
import dev.loot.client.setting.StringSetting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Category panels plus a search panel. Left click toggles, right click opens settings,
 * middle click binds a key. Drag panels by the header, scroll a panel with the wheel.
 */
public class ClickGuiScreen extends Screen {
    private static final int WIDTH = 124;
    private static final int HEADER = 20;
    private static final int ROW = 16;
    private static final int SROW = 14;
    private static final int GAP = 8;

    private static final List<Panel> PANELS = new ArrayList<>();
    private static final Set<Module> EXPANDED = new HashSet<>();
    private static final Map<Object, Float> ANIM = new HashMap<>();
    private static Panel searchPanel;
    private static String query = "";

    private Panel dragging;
    private int dragOffX, dragOffY;
    private NumberSetting sliding;
    private int slideX, slideW;
    private Module binding;
    private StringSetting editing;
    private String editBuffer = "";
    private boolean searchFocused;
    private String hoverText;
    private long openedAt;
    private float scale = 1f;
    private int vw, vh;

    /** Menu scale: MenuScale 100 renders like GUI scale 2 no matter what GUI scale the game uses. */
    private void updateScale() {
        var g = LootClient.modules().get(dev.loot.client.module.client.ClickGUI.class);
        double factor = client != null ? client.getWindow().getScaleFactor() : 2;
        scale = (float) MathHelper.clamp(2.0 * g.scale.get() / 100.0 / factor, 0.25, 3.0);
        vw = (int) (super.width / scale);
        vh = (int) (super.height / scale);
    }

    private static final class Panel {
        final Category category; // null for the search panel
        int x, y;
        boolean open = true;
        int scroll;
        int contentHeight;

        Panel(Category category, int x, int y) {
            this.category = category;
            this.x = x;
            this.y = y;
        }

        String title() {
            return category == null ? "Search" : category.displayName;
        }
    }

    public ClickGuiScreen() {
        super(Text.literal("Loot Client"));
        if (PANELS.isEmpty()) {
            int x = GAP;
            for (Category c : Category.values()) {
                PANELS.add(new Panel(c, x, GAP));
                x += WIDTH + GAP;
            }
            searchPanel = new Panel(null, x, GAP);
            PANELS.add(searchPanel);
        }
        openedAt = System.currentTimeMillis();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private List<Module> modulesFor(Panel p) {
        if (p.category != null) return LootClient.modules().getModules(p.category);
        if (query.isBlank()) return List.of();
        String q = query.toLowerCase(Locale.ROOT).replace(" ", "");
        return LootClient.modules().getModules().stream()
            .filter(m -> m.getName().toLowerCase(Locale.ROOT).replace(" ", "").contains(q))
            .toList();
    }

    private static float anim(Object key, boolean target) {
        float cur = ANIM.getOrDefault(key, target ? 1f : 0f);
        float next = cur + ((target ? 1f : 0f) - cur) * 0.25f;
        if (Math.abs(next - (target ? 1f : 0f)) < 0.01f) next = target ? 1f : 0f;
        ANIM.put(key, next);
        return next;
    }

    @Override
    public void render(DrawContext ctx, int rawMouseX, int rawMouseY, float delta) {
        updateScale();
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale, scale);
        renderScaled(ctx, (int) (rawMouseX / scale), (int) (rawMouseY / scale));
        ctx.getMatrices().popMatrix();
    }

    private void renderScaled(DrawContext ctx, int mouseX, int mouseY) {
        float fade = MathHelper.clamp((System.currentTimeMillis() - openedAt) / 150f, 0f, 1f);
        ctx.fill(0, 0, vw, vh, (int) (0x99 * fade) << 24 | 0x06070D);
        if (sliding != null) applySlider(mouseX);
        hoverText = null;

        for (Panel p : PANELS) renderPanel(ctx, p, mouseX, mouseY);

        if (hoverText != null) {
            int w = UI.width(hoverText) + 14;
            int x = (vw - w) / 2, y = vh - 22;
            UI.roundRect(ctx, x, y, x + w, y + 15, 7, Theme.SURFACE_RAISED);
            UI.drawText(ctx, hoverText, x + 7, y + 3, Theme.TEXT_MUTED);
        }
        UI.drawText(ctx, "Loot Client " + LootClient.VERSION, 6, vh - 13, Theme.TEXT_FAINT);
    }

    private void renderPanel(DrawContext ctx, Panel p, int mouseX, int mouseY) {
        List<Module> mods = modulesFor(p);
        boolean search = p.category == null;

        // Header
        UI.roundRect(ctx, p.x, p.y, p.x + WIDTH, p.y + HEADER, 5, Theme.SURFACE_RAISED, true, !p.open);
        UI.roundRect(ctx, p.x + 8, p.y + 8, p.x + 12, p.y + 12, 2, Theme.ACCENT);
        UI.drawBold(ctx, p.title(), p.x + 18, p.y + 5, Theme.TEXT);
        if (!search) {
            long on = mods.stream().filter(Module::isEnabled).count();
            UI.drawTextRight(ctx, on + "/" + mods.size(), p.x + WIDTH - 8, p.y + 5, Theme.TEXT_FAINT);
        }
        if (!p.open) return;

        int bodyTop = p.y + HEADER;
        int maxBody = Math.max(40, vh - bodyTop - 30);
        int y = bodyTop;

        if (search) {
            boolean focus = searchFocused;
            UI.roundRect(ctx, p.x, y, p.x + WIDTH, y + 24, 0, Theme.SURFACE);
            UI.roundRect(ctx, p.x + 6, y + 4, p.x + WIDTH - 6, y + 20, 4, focus ? Theme.ROW_HOVER : Theme.SETTINGS_BG);
            String shown = query.isEmpty() && !focus ? "Type to search..." : query + (focus && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "");
            UI.drawText(ctx, shown, p.x + 12, y + 8, query.isEmpty() && !focus ? Theme.TEXT_FAINT : Theme.TEXT);
            y += 24;
        }

        int listTop = y;
        int contentHeight = 0;
        for (Module m : mods) {
            contentHeight += ROW;
            if (EXPANDED.contains(m)) contentHeight += m.getSettings().size() * SROW + 4;
        }
        p.contentHeight = contentHeight;
        int visible = Math.min(contentHeight, maxBody);
        p.scroll = MathHelper.clamp(p.scroll, 0, Math.max(0, contentHeight - visible));

        UI.roundRect(ctx, p.x, listTop, p.x + WIDTH, listTop + visible + 5, 5, Theme.SURFACE, false, true);
        ctx.enableScissor(p.x, listTop, p.x + WIDTH, listTop + visible);
        y = listTop - p.scroll;
        for (Module m : mods) {
            boolean hover = inside(mouseX, mouseY, p.x, y, WIDTH, ROW) && mouseY >= listTop && mouseY < listTop + visible;
            float t = anim(m, m.isEnabled());
            if (hover) ctx.fill(p.x, y, p.x + WIDTH, y + ROW, Theme.ROW_HOVER);
            else if (t > 0) ctx.fill(p.x, y, p.x + WIDTH, y + ROW, Theme.lerp(0x00000000, Theme.ROW_ACTIVE, t));
            if (t > 0) UI.roundRect(ctx, p.x, y + 3, p.x + 2, y + ROW - 3, 1, Theme.lerp(Theme.ACCENT & 0xFFFFFF, Theme.ACCENT, t));

            String label = binding == m ? "Press a key..." : m.getName();
            UI.drawText(ctx, label, p.x + 8, y + 4, Theme.lerp(Theme.TEXT, Theme.ACCENT, t));
            int right = p.x + WIDTH - 8;
            UI.toggle(ctx, right - 16, y + 4, t);
            if (m.getKey() != GLFW.GLFW_KEY_UNKNOWN && binding != m) {
                UI.drawTextRight(ctx, keyName(m.getKey()), right - 20, y + 4, Theme.TEXT_FAINT);
            }
            if (hover) hoverText = m.getDescription();
            y += ROW;

            if (!EXPANDED.contains(m)) continue;
            int settingsTop = y;
            ctx.fill(p.x, y, p.x + WIDTH, y + m.getSettings().size() * SROW + 4, Theme.SETTINGS_BG);
            y += 2;
            for (Setting<?> s : m.getSettings()) {
                renderSetting(ctx, s, p.x + 10, y, WIDTH - 18);
                if (inside(mouseX, mouseY, p.x, y, WIDTH, SROW)) hoverText = s.getDescription();
                y += SROW;
            }
            y += 2;
            ctx.fill(p.x + 6, settingsTop, p.x + 7, y - 2, Theme.ACCENT_DIM);
        }
        ctx.disableScissor();

        if (contentHeight > visible) {
            int barH = Math.max(10, visible * visible / contentHeight);
            int barY = listTop + (visible - barH) * p.scroll / Math.max(1, contentHeight - visible);
            UI.roundRect(ctx, p.x + WIDTH - 3, barY, p.x + WIDTH - 1, barY + barH, 1, Theme.ACCENT_DIM);
        }
        if (search && !query.isBlank() && mods.isEmpty()) {
            UI.roundRect(ctx, p.x, listTop, p.x + WIDTH, listTop + 22, 5, Theme.SURFACE, false, true);
            UI.drawText(ctx, "No matches", p.x + 10, listTop + 6, Theme.TEXT_FAINT);
        }
    }

    private void renderSetting(DrawContext ctx, Setting<?> s, int x, int y, int w) {
        UI.drawText(ctx, s.getName(), x, y + 3, Theme.TEXT_MUTED);
        if (s instanceof BoolSetting b) {
            UI.toggle(ctx, x + w - 16, y + 3, anim(s, b.get()));
        } else if (s instanceof NumberSetting n) {
            String v = n.display();
            int barX = x + w - 44, barW = 44 - UI.width(v) - 4;
            double pct = (n.get() - n.getMin()) / (n.getMax() - n.getMin());
            UI.roundRect(ctx, barX, y + 6, barX + barW, y + 8, 1, Theme.TOGGLE_OFF);
            UI.roundRect(ctx, barX, y + 6, barX + Math.max(2, (int) (barW * pct)), y + 8, 1, Theme.ACCENT);
            UI.drawTextRight(ctx, v, x + w, y + 3, Theme.TEXT);
        } else if (s instanceof StringSetting str) {
            String v = editing == str ? editBuffer + "|" : str.display();
            if (UI.width(v) > w / 2) v = "..." + v.substring(Math.max(0, v.length() - 10));
            UI.drawTextRight(ctx, v, x + w, y + 3, editing == str ? Theme.ACCENT : Theme.TEXT);
        } else {
            String v = s instanceof BlockListSetting b ? b.get().size() + " blocks" : s.display();
            UI.drawTextRight(ctx, v, x + w, y + 3, Theme.ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        updateScale();
        double mx = click.x() / scale, my = click.y() / scale;
        int button = click.button();
        commitEdit();
        searchFocused = false;

        for (int i = PANELS.size() - 1; i >= 0; i--) {
            Panel p = PANELS.get(i);
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
            if (p.category == null) {
                if (inside(mx, my, p.x, y, WIDTH, 24)) {
                    searchFocused = true;
                    if (button == 1) query = "";
                    return true;
                }
                y += 24;
            }
            int listTop = y;
            int visible = Math.min(p.contentHeight, Math.max(40, vh - listTop - 30));
            if (my < listTop || my >= listTop + visible) continue;
            y -= p.scroll;

            for (Module m : modulesFor(p)) {
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
                y += 2;
                for (Setting<?> s : m.getSettings()) {
                    if (inside(mx, my, p.x, y, WIDTH, SROW)) {
                        clickSetting(s, button, p.x + 10, WIDTH - 18);
                        return true;
                    }
                    y += SROW;
                }
                y += 2;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void clickSetting(Setting<?> s, int button, int x, int w) {
        if (s instanceof BoolSetting b) b.toggle();
        else if (s instanceof ModeSetting mode) mode.cycle(button == 0);
        else if (s instanceof NumberSetting n && button == 0) {
            sliding = n;
            slideW = 44 - UI.width(n.display()) - 4;
            slideX = x + w - 44;
        } else if (s instanceof StringSetting str) {
            editing = str;
            editBuffer = str.get();
        }
        // Block lists are edited with .set <module> Blocks add <id>.
    }

    private void applySlider(double mx) {
        double pct = MathHelper.clamp((mx - slideX) / Math.max(1, slideW), 0, 1);
        sliding.set(sliding.getMin() + pct * (sliding.getMax() - sliding.getMin()));
    }

    private void commitEdit() {
        if (editing != null) {
            editing.parse(editBuffer);
            editing = null;
        }
    }

    @Override
    public boolean mouseScrolled(double rawX, double rawY, double horizontal, double vertical) {
        double mx = rawX / scale, my = rawY / scale;
        for (Panel p : PANELS) {
            if (p.open && inside(mx, my, p.x, p.y, WIDTH, vh)) {
                p.scroll -= (int) (vertical * 14);
                return true;
            }
        }
        return super.mouseScrolled(rawX, rawY, horizontal, vertical);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging != null) {
            dragging.x = (int) (click.x() / scale) - dragOffX;
            dragging.y = (int) (click.y() / scale) - dragOffY;
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
        if (editing != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) commitEdit();
            else if (keyCode == GLFW.GLFW_KEY_ESCAPE) editing = null;
            else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
            return true;
        }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) searchFocused = false;
            else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) query = query.substring(0, query.length() - 1);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) return false;
        if (editing != null) {
            editBuffer += input.asString();
            return true;
        }
        if (binding == null) {
            // Typing anywhere goes to search, like a command palette.
            searchFocused = true;
            searchPanel.open = true;
            query += input.asString();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        commitEdit();
        Config.save();
    }

    private static String keyName(int key) {
        String name = InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
        return name.length() > 6 ? name.substring(0, 6) : name;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
