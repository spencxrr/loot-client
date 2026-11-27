package dev.loot.client.gui;

import dev.loot.client.LootClient;
import dev.loot.client.config.Config;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.module.client.ClickGUI;
import dev.loot.client.setting.BlockListSetting;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.ModeSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.setting.Setting;
import dev.loot.client.setting.StringSetting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Category panels plus a search panel and a config panel. Left click toggles, right click opens
 * settings, middle click binds a key. Drag panels by the header (right click collapses), scroll
 * a panel with the wheel. Start typing anywhere to search.
 *
 * Layout and hit testing share one pass: every clickable thing drawn in a frame is recorded as a
 * {@link Hit}, and clicks are resolved against those, so what you click is always exactly what
 * you see (including while panels are scrolling, expanding or animating).
 */
public class ClickGuiScreen extends Screen {
    private static final int WIDTH = 128;
    private static final int HEADER = 22;
    private static final int ROW = 17;
    private static final int SROW = 15;
    private static final int BOX = 26;
    private static final int GAP = 8;
    private static final int PAD = 4;

    private static final long OPEN_MS = 260;
    private static final long STAGGER_MS = 28;
    private static final long CLOSE_MS = 150;

    private enum Kind { CATEGORY, SEARCH, CONFIG }

    private static final class Panel {
        final Kind kind;
        final Category category;
        int x, y;
        boolean open = true;
        final Anim openAnim = new Anim(1f);
        float scrollTarget;
        final Anim scroll = new Anim(0f);
        int maxScroll;
        int height = HEADER; // full on-screen height last frame, for hover and click tests
        final Anim enabledBar = new Anim(0f);
        final Anim headerHover = new Anim(0f);
        final Anim focus = new Anim(0f);
        // cached header counter, rebuilt only when it changes
        int lastOn = -1, lastTotal = -1;
        String counter = "";

        Panel(Kind kind, Category category, int x, int y) {
            this.kind = kind;
            this.category = category;
            this.x = x;
            this.y = y;
        }

        String title() {
            return switch (kind) {
                case CONFIG -> "Configs";
                case SEARCH -> "Search";
                case CATEGORY -> category.displayName;
            };
        }
    }

    /** Per-module animation state. */
    private static final class ModAnim {
        final Anim on = new Anim(0f), hover = new Anim(0f), expand = new Anim(0f);
    }

    private enum HitKind { HEADER, BOX, MODULE, BIND, SETTING, PROFILE }

    private static final class Hit {
        HitKind kind;
        Panel panel;
        Module module;
        Setting<?> setting;
        String name;
        int x, y, w, h;
    }

    private static final List<Panel> PANELS = new ArrayList<>();
    private static final Set<Module> EXPANDED = new HashSet<>();
    private static final Map<Module, ModAnim> MOD_ANIM = new IdentityHashMap<>();
    private static final Map<Object, Anim> ANIM = new IdentityHashMap<>();
    private static final Map<BoolSetting, Anim> TOGGLE_ANIM = new IdentityHashMap<>();
    private static final Map<String, Anim> PROFILE_ANIM = new HashMap<>();
    private static final String[] KEY_NAMES = new String[512];
    private static Panel searchPanel;
    private static String query = "";

    // search results, rebuilt only when the query changes
    private static String cachedQuery;
    private static List<Module> cachedResults = List.of();

    private final List<Hit> hits = new ArrayList<>();
    private int hitCount;
    private int clipTop, clipBottom;
    private Panel hovered;

    private String configName = "";
    private boolean configFocused;
    private List<String> profiles = List.of();
    private long profilesAt;

    private Panel dragging;
    private int dragOffX, dragOffY;
    private NumberSetting sliding;
    private int slideX, slideW;
    private Module binding;
    private StringSetting editing;
    private String editBuffer = "";
    private boolean searchFocused;

    private String tipTitle, tipHint;
    private String shownTipTitle, shownTipHint;
    private final Anim tipAnim = new Anim(0f);

    private final long openedAt;
    private long closingAt = -1;
    private long lastFrame;
    private float dt;

    private float scale = 1f;
    private int vw, vh;

    public ClickGuiScreen() {
        super(Text.literal("Loot Client"));
        if (PANELS.isEmpty()) {
            int x = GAP;
            for (Category c : Category.values()) {
                PANELS.add(new Panel(Kind.CATEGORY, c, x, GAP));
                x += WIDTH + GAP;
            }
            searchPanel = new Panel(Kind.SEARCH, null, x, GAP);
            PANELS.add(searchPanel);
            PANELS.add(new Panel(Kind.CONFIG, null, x + WIDTH + GAP, GAP));
        }
        openedAt = System.currentTimeMillis();
    }

    private static ClickGUI gui() {
        return LootClient.modules().get(ClickGUI.class);
    }

    /**
     * The menu lays itself out on a virtual screen 540 units tall (1080p at GUI scale 2) and is
     * scaled to fit the real window, so it looks the same whatever GUI scale or resolution you use.
     */
    private void updateScale() {
        var g = gui();
        // Changing the scale while dragging its own slider moves the slider under the mouse,
        // which made it jump around. Only apply the new size once the slider is let go.
        if (sliding == g.scale && vw > 0) return;
        scale = (float) MathHelper.clamp(super.height * g.scale.get() / (540.0 * 100.0), 0.1, 4.0);
        vw = (int) (super.width / scale);
        vh = (int) (super.height / scale);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Vanilla draws blur plus its own dark gradient. We draw a dim that fades with the open
        // animation instead, and only keep the blur when it's turned on (it costs a full-screen pass).
        if (gui().blur.get()) applyBlur(ctx);
    }

    private ModAnim modAnim(Module m) {
        return MOD_ANIM.computeIfAbsent(m, k -> new ModAnim());
    }

    private static Anim anim(Object key, float initial) {
        Anim a = ANIM.get(key);
        if (a == null) ANIM.put(key, a = new Anim(initial));
        return a;
    }

    private List<Module> modulesFor(Panel p) {
        if (p.kind == Kind.CATEGORY) return LootClient.modules().getModules(p.category);
        if (query.isBlank()) return List.of();
        if (!query.equals(cachedQuery)) {
            cachedQuery = query;
            String q = query.toLowerCase(Locale.ROOT).replace(" ", "");
            List<Module> starts = new ArrayList<>(), contains = new ArrayList<>();
            for (Module m : LootClient.modules().getModules()) {
                String name = m.getName().toLowerCase(Locale.ROOT).replace(" ", "");
                if (name.startsWith(q)) starts.add(m);
                else if (name.contains(q)) contains.add(m);
            }
            // names that start with the query rank first, so "fly" finds Flight before AntiFly-ish names
            starts.addAll(contains);
            cachedResults = starts;
        }
        return cachedResults;
    }

    private List<String> profiles() {
        if (System.currentTimeMillis() - profilesAt > 1000) {
            profiles = Config.profiles();
            profilesAt = System.currentTimeMillis();
        }
        return profiles;
    }

    // ---- render ---------------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int rawMouseX, int rawMouseY, float delta) {
        long now = System.currentTimeMillis();
        if (closingAt >= 0 && now - closingAt >= CLOSE_MS) {
            super.close();
            return;
        }
        dt = lastFrame == 0 ? 0f : MathHelper.clamp((now - lastFrame) / 1000f, 0f, 0.1f);
        lastFrame = now;

        updateScale();
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.scale(scale, scale);
        try {
            renderScaled(ctx, (int) (rawMouseX / scale), (int) (rawMouseY / scale), now);
        } finally {
            UI.alpha = 1f;
            m.popMatrix();
        }
    }

    /** 0..1 how far the whole menu is shown, including the close animation. */
    private float globalShow(long now) {
        if (closingAt >= 0) return 1f - UI.easeInCubic((now - closingAt) / (float) CLOSE_MS);
        return UI.easeOutCubic((now - openedAt) / (float) OPEN_MS);
    }

    private float panelShow(int index, long now) {
        if (closingAt >= 0) return globalShow(now);
        long t = now - openedAt - index * STAGGER_MS;
        return UI.easeOutCubic(t / (float) (OPEN_MS - 40));
    }

    private void renderScaled(DrawContext ctx, int mouseX, int mouseY, long now) {
        float show = globalShow(now);
        UI.alpha = 1f;
        UI.fill(ctx, 0, 0, vw, vh, (int) (0xA0 * show) << 24 | 0x05060B);

        if (sliding != null) applySlider(mouseX);
        hitCount = 0;
        tipTitle = null;
        tipHint = null;
        Panel top = topPanelAt(mouseX, mouseY);
        // no hover effects while closing, or on other panels while one is being dragged
        hovered = closingAt >= 0 || dragging != null && dragging != top ? null : top;

        for (int i = 0; i < PANELS.size(); i++) {
            Panel p = PANELS.get(i);
            clampToScreen(p);
            float t = panelShow(i, now);
            if (t <= 0f) continue;
            Matrix3x2fStack m = ctx.getMatrices();
            m.pushMatrix();
            // drop in from slightly above with a soft fade
            m.translate(0, (1f - t) * -10f);
            UI.alpha = t;
            renderPanel(ctx, p, mouseX, mouseY, now);
            m.popMatrix();
        }

        UI.alpha = show;
        renderTooltip(ctx);
        UI.drawText(ctx, "Loot Client " + LootClient.VERSION, 6, vh - 13, Theme.TEXT_FAINT);
        UI.drawTextRight(ctx, "Type to search  ·  Right click: settings  ·  Middle click: bind", vw - 6, vh - 13, Theme.TEXT_FAINT);
    }

    private void renderTooltip(DrawContext ctx) {
        if (tipTitle != null) {
            shownTipTitle = tipTitle;
            shownTipHint = tipHint;
        }
        float t = tipAnim.update(tipTitle != null, 16f, dt);
        if (t <= 0f || shownTipTitle == null || shownTipTitle.isEmpty()) return;
        float base = UI.alpha;
        UI.alpha = base * t;
        int maxW = Math.min(vw - 40, 360);
        String title = UI.ellipsize(shownTipTitle, maxW);
        String hint = shownTipHint == null ? null : UI.ellipsize(shownTipHint, maxW);
        int w = Math.max(UI.width(title), hint == null ? 0 : UI.width(hint)) + 16;
        int h = hint == null ? 17 : 28;
        int x = (vw - w) / 2;
        int y = vh - 26 - h + (int) ((1f - t) * 6f);
        UI.shadow(ctx, x, y, x + w, y + h, 7, 0x50);
        UI.roundRect(ctx, x, y, x + w, y + h, 7, Theme.SURFACE_RAISED);
        UI.drawText(ctx, title, x + 8, y + 4, Theme.TEXT);
        if (hint != null) UI.drawText(ctx, hint, x + 8, y + 15, Theme.TEXT_FAINT);
        UI.alpha = base;
    }

    private void tip(String title, String hint) {
        tipTitle = title;
        tipHint = hint;
    }

    private void clampToScreen(Panel p) {
        if (vw <= 0) return;
        p.x = MathHelper.clamp(p.x, 0, Math.max(0, vw - WIDTH));
        p.y = MathHelper.clamp(p.y, 0, Math.max(0, vh - HEADER));
    }

    private Panel topPanelAt(double mx, double my) {
        for (int i = PANELS.size() - 1; i >= 0; i--) {
            Panel p = PANELS.get(i);
            if (inside(mx, my, p.x, p.y, WIDTH, p.height)) return p;
        }
        return null;
    }

    private Hit hit(HitKind kind, Panel p, int x, int y, int w, int h) {
        // clip to the scrolling region so rows hidden under the edge can't be clicked
        int top = Math.max(y, clipTop), bottom = Math.min(y + h, clipBottom);
        if (bottom <= top) return null;
        Hit hit;
        if (hitCount < hits.size()) hit = hits.get(hitCount);
        else hits.add(hit = new Hit());
        hitCount++;
        hit.kind = kind;
        hit.panel = p;
        hit.module = null;
        hit.setting = null;
        hit.name = null;
        hit.x = x;
        hit.y = top;
        hit.w = w;
        hit.h = bottom - top;
        return hit;
    }

    private boolean hover(Panel p, int mx, int my, int x, int y, int w, int h) {
        return p == hovered && inside(mx, my, x, Math.max(y, clipTop), w, Math.min(y + h, clipBottom) - Math.max(y, clipTop));
    }

    private int settingsHeight(Module m) {
        return (m.getSettings().size() + 1) * SROW + 6;
    }

    /** Height of a panel's scrolling list with expand animations applied, also stepping those animations. */
    private int measure(Panel p, List<Module> mods) {
        if (p.kind == Kind.CONFIG) {
            int n = profiles().size();
            return n == 0 ? 22 : n * ROW;
        }
        if (p.kind == Kind.SEARCH && mods.isEmpty()) return query.isBlank() ? 0 : 22;
        float h = 0;
        for (Module m : mods) {
            ModAnim a = modAnim(m);
            float e = a.expand.update(EXPANDED.contains(m), 16f, dt);
            h += ROW + e * settingsHeight(m);
        }
        return (int) Math.ceil(h);
    }

    private void renderPanel(DrawContext ctx, Panel p, int mouseX, int mouseY, long now) {
        List<Module> mods = p.kind == Kind.CONFIG ? List.of() : modulesFor(p);
        float open = p.openAnim.update(p.open, 15f, dt);

        int boxH = p.kind == Kind.CATEGORY ? 0 : BOX;
        int listTop0 = p.y + HEADER + boxH;
        int content = measure(p, mods);
        int maxBody = Math.max(40, vh - listTop0 - 34);
        int visible = Math.min(content, maxBody);
        p.maxScroll = Math.max(0, content - visible);
        p.scrollTarget = MathHelper.clamp(p.scrollTarget, 0, p.maxScroll);
        float scroll = MathHelper.clamp(p.scroll.update(p.scrollTarget, 18f, dt), 0, p.maxScroll);

        int fullBody = boxH + visible + PAD;
        int bodyH = Math.round(fullBody * open);
        p.height = HEADER + bodyH;

        // shadow + surfaces
        UI.shadow(ctx, p.x, p.y, p.x + WIDTH, p.y + p.height, 6, 0x60);
        boolean flatBottom = bodyH > 2;
        UI.roundRect(ctx, p.x, p.y, p.x + WIDTH, p.y + HEADER, 6, Theme.SURFACE_RAISED, true, !flatBottom);
        if (flatBottom) UI.roundRect(ctx, p.x, p.y + HEADER, p.x + WIDTH, p.y + p.height, 6, Theme.SURFACE, false, true);

        renderHeader(ctx, p, mods, mouseX, mouseY);
        if (bodyH <= 0) return;

        int bodyTop = p.y + HEADER;
        int bodyBottom = p.y + p.height;
        ctx.enableScissor(p.x, bodyTop, p.x + WIDTH, bodyBottom);
        clipTop = bodyTop;
        clipBottom = bodyBottom;

        if (p.kind == Kind.SEARCH) {
            renderBox(ctx, p, bodyTop, query, "Type to search...", searchFocused, mouseX, mouseY);
        } else if (p.kind == Kind.CONFIG) {
            renderBox(ctx, p, bodyTop, configName, "Name, Enter to save", configFocused, mouseX, mouseY);
            if (hover(p, mouseX, mouseY, p.x, bodyTop, WIDTH, BOX)) tip("Save your current setup as a config.", "Click, type a name, press Enter. Right click clears.");
        }

        int listTop = listTop0;
        int listBottom = Math.min(listTop + visible, bodyBottom);
        if (listBottom > listTop) {
            ctx.enableScissor(p.x, listTop, p.x + WIDTH, listBottom);
            clipTop = listTop;
            clipBottom = listBottom;
            int y = listTop - Math.round(scroll);
            if (p.kind == Kind.CONFIG) renderProfiles(ctx, p, y, mouseX, mouseY);
            else if (mods.isEmpty() && p.kind == Kind.SEARCH && !query.isBlank()) UI.drawText(ctx, "No matches", p.x + 10, y + 6, Theme.TEXT_FAINT);
            else renderModules(ctx, p, mods, y, mouseX, mouseY);
            ctx.disableScissor();

            if (p.maxScroll > 0) {
                int barH = Math.max(12, visible * visible / Math.max(1, content));
                int barY = listTop + Math.round((visible - barH) * (scroll / p.maxScroll));
                boolean barHover = p == hovered;
                UI.roundRect(ctx, p.x + WIDTH - 4, barY, p.x + WIDTH - 2, barY + barH, 1, barHover ? Theme.ACCENT : Theme.ACCENT_DIM);
            }
        }
        ctx.disableScissor();
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
    }

    private void renderHeader(DrawContext ctx, Panel p, List<Module> mods, int mouseX, int mouseY) {
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
        hit(HitKind.HEADER, p, p.x, p.y, WIDTH, HEADER);
        boolean hov = hover(p, mouseX, mouseY, p.x, p.y, WIDTH, HEADER);
        float h = p.headerHover.update(hov, 14f, dt);
        if (h > 0) UI.roundRect(ctx, p.x, p.y, p.x + WIDTH, p.y + HEADER, 6, UI.withAlpha(Theme.ROW_HOVER, h * 0.6f), true, p.height <= HEADER + 2);

        UI.roundRect(ctx, p.x + 8, p.y + 8, p.x + 14, p.y + 14, 3, Theme.ACCENT);
        UI.drawBold(ctx, p.title(), p.x + 20, p.y + 7, Theme.TEXT);

        if (p.kind == Kind.CATEGORY) {
            int on = 0;
            for (Module m : mods) if (m.isEnabled()) on++;
            if (on != p.lastOn || mods.size() != p.lastTotal) {
                p.lastOn = on;
                p.lastTotal = mods.size();
                p.counter = on + "/" + mods.size();
            }
            int cw = UI.width(p.counter) + 8;
            int cx = p.x + WIDTH - 8 - cw;
            UI.roundRect(ctx, cx, p.y + 5, cx + cw, p.y + 17, 6, on > 0 ? Theme.ACCENT_GLOW : 0x18FFFFFF);
            UI.drawText(ctx, p.counter, cx + 4, p.y + 7, on > 0 ? Theme.ACCENT : Theme.TEXT_FAINT);

            // thin bar under the header showing how much of the category is on
            float frac = p.enabledBar.update(mods.isEmpty() ? 0f : on / (float) mods.size(), 10f, dt);
            int bw = Math.round((WIDTH - 16) * frac);
            if (p.open && bw > 0) UI.fill(ctx, p.x + 8, p.y + HEADER - 1, p.x + 8 + bw, p.y + HEADER, Theme.ACCENT_DIM);
        } else if (p.kind == Kind.SEARCH && !query.isBlank()) {
            String n = String.valueOf(mods.size());
            UI.drawTextRight(ctx, n, p.x + WIDTH - 10, p.y + 7, Theme.TEXT_FAINT);
        }
        if (hov && dragging == null) tip(p.title(), "Drag to move. Right click to collapse.");
    }

    private void renderBox(DrawContext ctx, Panel p, int y, String value, String placeholder, boolean focus, int mouseX, int mouseY) {
        hit(HitKind.BOX, p, p.x, y, WIDTH, BOX);
        boolean hov = hover(p, mouseX, mouseY, p.x, y, WIDTH, BOX);
        float f = p.focus.update(focus, 14f, dt);
        int bx1 = p.x + 6, by1 = y + 5, bx2 = p.x + WIDTH - 6, by2 = y + BOX - 5;
        UI.roundRect(ctx, bx1, by1, bx2, by2, 5, Theme.lerp(hov ? Theme.ROW_HOVER : Theme.SETTINGS_BG, Theme.ROW_HOVER, f));
        // accent underline grows from the middle while focused
        int half = Math.round((bx2 - bx1 - 8) / 2f * f);
        int mid = (bx1 + bx2) / 2;
        if (half > 0) UI.fill(ctx, mid - half, by2 - 1, mid + half, by2, Theme.ACCENT);
        int maxW = bx2 - bx1 - 12;
        if (value.isEmpty() && !focus) {
            UI.drawText(ctx, placeholder, bx1 + 6, by1 + 4, Theme.TEXT_FAINT);
        } else {
            String shown = UI.ellipsizeStart(value, maxW - 3);
            UI.drawText(ctx, shown, bx1 + 6, by1 + 4, Theme.TEXT);
            if (focus && (System.currentTimeMillis() / 530) % 2 == 0) {
                int cx = bx1 + 6 + UI.width(shown) + 1;
                UI.fill(ctx, cx, by1 + 3, cx + 1, by1 + 13, Theme.ACCENT);
            }
        }
    }

    private void renderProfiles(DrawContext ctx, Panel p, int y, int mouseX, int mouseY) {
        List<String> list = profiles();
        if (list.isEmpty()) {
            UI.drawText(ctx, "No saved configs yet", p.x + 10, y + 6, Theme.TEXT_FAINT);
            return;
        }
        for (String name : list) {
            if (y + ROW > clipTop && y < clipBottom) {
                Hit h = hit(HitKind.PROFILE, p, p.x, y, WIDTH, ROW);
                if (h != null) h.name = name;
                boolean hov = hover(p, mouseX, mouseY, p.x, y, WIDTH, ROW);
                float t = PROFILE_ANIM.computeIfAbsent(name, k -> new Anim(0f)).update(hov, 16f, dt);
                if (t > 0) UI.fill(ctx, p.x, y, p.x + WIDTH, y + ROW, UI.withAlpha(Theme.ROW_HOVER, t));
                UI.drawText(ctx, UI.ellipsize(name, WIDTH - 20), p.x + 9 + Math.round(t * 2), y + 4, Theme.lerp(Theme.TEXT, Theme.ACCENT, t));
                if (hov) tip("Config: " + name, "Left click to load, right click to delete.");
            }
            y += ROW;
        }
    }

    private void renderModules(DrawContext ctx, Panel p, List<Module> mods, int y, int mouseX, int mouseY) {
        for (Module m : mods) {
            ModAnim a = modAnim(m);
            float on = a.on.update(m.isEnabled(), 14f, dt);
            boolean visibleRow = y + ROW > clipTop && y < clipBottom;
            boolean hov = visibleRow && hover(p, mouseX, mouseY, p.x, y, WIDTH, ROW);
            float hv = a.hover.update(hov, 18f, dt);

            if (visibleRow) {
                Hit h = hit(HitKind.MODULE, p, p.x, y, WIDTH, ROW);
                if (h != null) h.module = m;
                if (on > 0) UI.fill(ctx, p.x, y, p.x + WIDTH, y + ROW, UI.withAlpha(Theme.ROW_ACTIVE, on));
                if (hv > 0) UI.fill(ctx, p.x, y, p.x + WIDTH, y + ROW, UI.withAlpha(Theme.ROW_HOVER, hv * 0.85f));
                if (on > 0) {
                    // accent bar grows out from the row's middle
                    int half = Math.max(1, Math.round((ROW - 6) / 2f * on));
                    int mid = y + ROW / 2;
                    UI.roundRect(ctx, p.x, mid - half, p.x + 2, mid + half, 1, Theme.ACCENT);
                }

                boolean isBinding = binding == m;
                String label = isBinding ? "Press a key..." : m.getName();
                int right = p.x + WIDTH - 8;
                int textX = p.x + 9;
                int textMax = right - 20 - textX;
                String key = !isBinding && m.getKey() != GLFW.GLFW_KEY_UNKNOWN ? keyName(m.getKey()) : null;
                if (key != null) textMax -= UI.width(key) + 6;

                Matrix3x2fStack mat = ctx.getMatrices();
                mat.pushMatrix();
                mat.translate(hv * 2f, 0);
                UI.drawText(ctx, UI.ellipsize(label, textMax), textX, y + 4,
                    isBinding ? Theme.ACCENT : Theme.lerp(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, 0.55f + 0.45f * hv), Theme.ACCENT, on));
                mat.popMatrix();

                if (key != null) {
                    int kw = UI.width(key) + 6;
                    int kx = right - 20 - kw;
                    UI.roundRect(ctx, kx, y + 3, kx + kw, y + ROW - 3, 3, 0x16FFFFFF);
                    UI.drawText(ctx, key, kx + 3, y + 4, Theme.TEXT_FAINT);
                }
                UI.toggle(ctx, right - 16, y + 4, on);
                if (hov) tip(m.getDescription(), "Left click: toggle  ·  Right click: settings  ·  Middle click: bind");
            }
            y += ROW;

            float e = a.expand.value;
            if (e <= 0f) continue;
            int full = settingsHeight(m);
            int shownH = Math.round(full * e);
            if (y + shownH > clipTop && y < clipBottom) {
                renderSettings(ctx, p, m, y, shownH, e, mouseX, mouseY);
            }
            y += shownH;
        }
    }

    private void renderSettings(DrawContext ctx, Panel p, Module m, int y, int shownH, float e, int mouseX, int mouseY) {
        int saveTop = clipTop, saveBottom = clipBottom;
        clipBottom = Math.min(clipBottom, y + shownH);
        clipTop = Math.max(clipTop, y);
        if (clipBottom <= clipTop) {
            clipTop = saveTop;
            clipBottom = saveBottom;
            return;
        }
        ctx.enableScissor(p.x, clipTop, p.x + WIDTH, clipBottom);
        float base = UI.alpha;
        UI.alpha = base * MathHelper.clamp(e * 1.4f - 0.2f, 0f, 1f);

        UI.fill(ctx, p.x, y, p.x + WIDTH, y + shownH, Theme.SETTINGS_BG);
        int top = y;
        y += 3;

        // keybind row
        {
            boolean hov = hover(p, mouseX, mouseY, p.x, y, WIDTH, SROW);
            Hit h = hit(HitKind.BIND, p, p.x, y, WIDTH, SROW);
            if (h != null) h.module = m;
            if (hov) UI.fill(ctx, p.x, y, p.x + WIDTH, y + SROW, UI.withAlpha(Theme.ROW_HOVER, 0.6f));
            UI.drawText(ctx, "Keybind", p.x + 12, y + 3, Theme.TEXT_MUTED);
            String bind = binding == m ? "press a key" : m.getKey() == GLFW.GLFW_KEY_UNKNOWN ? "none" : keyName(m.getKey());
            UI.drawTextRight(ctx, bind, p.x + WIDTH - 8, y + 3, binding == m ? Theme.ACCENT : Theme.TEXT);
            if (hov) tip("Keybind for " + m.getName(), "Click, then press a key. Right click or Esc clears it.");
            y += SROW;
        }
        for (Setting<?> s : m.getSettings()) {
            if (y + SROW > clipTop && y < clipBottom) {
                boolean hov = hover(p, mouseX, mouseY, p.x, y, WIDTH, SROW);
                Hit h = hit(HitKind.SETTING, p, p.x, y, WIDTH, SROW);
                if (h != null) {
                    h.module = m;
                    h.setting = s;
                }
                float hv = anim(s, 0f).update(hov, 18f, dt);
                if (hv > 0) UI.fill(ctx, p.x, y, p.x + WIDTH, y + SROW, UI.withAlpha(Theme.ROW_HOVER, hv * 0.6f));
                renderSetting(ctx, s, p.x + 12, y, WIDTH - 20, hv);
                if (hov) tip(s.getDescription(), settingHint(s));
            }
            y += SROW;
        }
        // guide line tying the settings to their module
        UI.fill(ctx, p.x + 6, top + 3, p.x + 7, top + shownH - 3, Theme.ACCENT_DIM);

        UI.alpha = base;
        ctx.disableScissor();
        clipTop = saveTop;
        clipBottom = saveBottom;
    }

    private static String settingHint(Setting<?> s) {
        if (s instanceof BoolSetting) return "Click to toggle.";
        if (s instanceof NumberSetting) return "Drag to change. Right click resets to default.";
        if (s instanceof ModeSetting) return "Left click: next  ·  Right click: previous.";
        if (s instanceof StringSetting) return "Click to edit, Enter to confirm, Esc to cancel.";
        if (s instanceof BlockListSetting) return "Click to pick blocks.";
        return null;
    }

    private void renderSetting(DrawContext ctx, Setting<?> s, int x, int y, int w, float hover) {
        int labelColor = Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, hover);
        if (s instanceof NumberSetting n) {
            String v = n.display();
            UI.drawText(ctx, UI.ellipsize(s.getName(), w - UI.width(v) - 6), x, y + 1, labelColor);
            UI.drawTextRight(ctx, v, x + w, y + 1, Theme.TEXT);
            double range = n.getMax() - n.getMin();
            float target = range <= 0 ? 0f : (float) ((n.get() - n.getMin()) / range);
            // the bar eases toward the value, so clicks and resets glide instead of snapping
            float pct = anim(sliderKey(n), target).update(target, sliding == n ? 40f : 16f, dt);
            int barY = y + 11;
            UI.roundRect(ctx, x, barY, x + w, barY + 2, 1, Theme.TOGGLE_OFF);
            int fillX = x + Math.round(w * pct);
            UI.roundRect(ctx, x, barY, Math.max(x + 2, fillX), barY + 2, 1, Theme.ACCENT);
            float knob = Math.max(hover, sliding == n ? 1f : 0f);
            if (knob > 0.05f) {
                int r = 2 + Math.round(knob);
                UI.roundRect(ctx, fillX - r, barY + 1 - r, fillX + r, barY + 1 + r, r, UI.withAlpha(Theme.KNOB_ON, knob));
            }
            return;
        }
        if (s instanceof BoolSetting b) {
            UI.drawText(ctx, UI.ellipsize(s.getName(), w - 22), x, y + 3, labelColor);
            UI.toggle(ctx, x + w - 16, y + 3, TOGGLE_ANIM.computeIfAbsent(b, k -> new Anim(k.get() ? 1f : 0f)).update(b.get(), 14f, dt));
            return;
        }
        String v;
        int color = Theme.ACCENT;
        if (s instanceof StringSetting str) {
            v = editing == str ? editBuffer + ((System.currentTimeMillis() / 530) % 2 == 0 ? "|" : " ") : str.display();
            color = editing == str ? Theme.ACCENT : Theme.TEXT;
        } else if (s instanceof BlockListSetting b) {
            v = b.get().size() + " blocks  >";
        } else {
            v = s.display();
        }
        int nameW = Math.min(UI.width(s.getName()), w / 2);
        v = editing == s ? UI.ellipsizeStart(v, w - nameW - 6) : UI.ellipsize(v, w - nameW - 6);
        UI.drawText(ctx, UI.ellipsize(s.getName(), w - UI.width(v) - 6), x, y + 3, labelColor);
        UI.drawTextRight(ctx, v, x + w, y + 3, color);
    }

    /** Separate key from the setting itself, which already keys its hover animation. */
    private static final Map<NumberSetting, Object> SLIDER_KEYS = new IdentityHashMap<>();

    private static Object sliderKey(NumberSetting n) {
        return SLIDER_KEYS.computeIfAbsent(n, k -> new Object());
    }

    // ---- input ----------------------------------------------------------------------------

    private Hit hitAt(double mx, double my) {
        Panel top = topPanelAt(mx, my);
        if (top == null) return null;
        for (int i = hitCount - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.panel == top && inside(mx, my, h.x, h.y, h.w, h.h)) return h;
        }
        return null;
    }

    private void bringToFront(Panel p) {
        if (PANELS.remove(p)) PANELS.add(p);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closingAt >= 0) return true;
        updateScale();
        double mx = click.x() / scale, my = click.y() / scale;
        int button = click.button();
        commitEdit();
        boolean wasBinding = binding != null;
        binding = null;
        searchFocused = false;
        configFocused = false;

        Hit h = hitAt(mx, my);
        if (h == null) {
            Panel p = topPanelAt(mx, my);
            if (p != null) {
                bringToFront(p);
                return true;
            }
            return wasBinding || super.mouseClicked(click, doubled);
        }
        Panel p = h.panel;
        bringToFront(p);
        switch (h.kind) {
            case HEADER -> {
                if (button == 0) {
                    dragging = p;
                    dragOffX = (int) mx - p.x;
                    dragOffY = (int) my - p.y;
                } else if (button == 1) {
                    p.open = !p.open;
                }
            }
            case BOX -> {
                if (p.kind == Kind.SEARCH) {
                    searchFocused = true;
                    if (button == 1) setQuery("");
                } else {
                    configFocused = true;
                    if (button == 1) configName = "";
                }
            }
            case PROFILE -> {
                if (button == 0) {
                    if (Config.loadProfile(h.name)) ChatUtil.info("Loaded config " + h.name);
                    else ChatUtil.error("Couldn't load config " + h.name);
                } else if (button == 1) {
                    if (Config.deleteProfile(h.name)) ChatUtil.info("Deleted config " + h.name);
                }
                profilesAt = 0;
            }
            case MODULE -> {
                Module m = h.module;
                switch (button) {
                    case 0 -> m.toggle();
                    case 1 -> {
                        if (!EXPANDED.remove(m)) EXPANDED.add(m);
                    }
                    case 2 -> binding = m;
                    default -> {}
                }
            }
            case BIND -> {
                if (button == 1) h.module.setKey(GLFW.GLFW_KEY_UNKNOWN);
                else binding = h.module;
            }
            case SETTING -> clickSetting(h.setting, button, p.x + 12, WIDTH - 20, mx);
        }
        return true;
    }

    private void clickSetting(Setting<?> s, int button, int x, int w, double mx) {
        if (s instanceof BoolSetting b) b.toggle();
        else if (s instanceof ModeSetting mode) mode.cycle(button == 0);
        else if (s instanceof NumberSetting n) {
            if (button == 1) {
                n.reset();
            } else if (button == 0) {
                sliding = n;
                slideX = x;
                slideW = w;
                applySlider(mx);
            }
        } else if (s instanceof StringSetting str) {
            editing = str;
            editBuffer = button == 1 ? "" : str.get();
        } else if (s instanceof BlockListSetting list) client.setScreen(new BlockPickerScreen(this, list));
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

    private void setQuery(String q) {
        query = q;
        searchPanel.scrollTarget = 0;
    }

    @Override
    public boolean mouseScrolled(double rawX, double rawY, double horizontal, double vertical) {
        if (closingAt >= 0) return true;
        double mx = rawX / scale, my = rawY / scale;
        Panel p = topPanelAt(mx, my);
        if (p != null && p.open) {
            p.scrollTarget = MathHelper.clamp(p.scrollTarget - (float) (vertical * ROW * 1.5), 0, p.maxScroll);
            return true;
        }
        return super.mouseScrolled(rawX, rawY, horizontal, vertical);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging != null) {
            dragging.x = (int) (click.x() / scale) - dragOffX;
            dragging.y = (int) (click.y() / scale) - dragOffY;
            clampToScreen(dragging);
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

    /** Shared editing for the three text fields. Returns the new text. */
    private String editText(String text, KeyInput input, int maxLen) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_BACKSPACE && !text.isEmpty()) {
            if (input.hasCtrlOrCmd()) {
                String t = text.stripTrailing();
                int cut = t.lastIndexOf(' ');
                return cut < 0 ? "" : t.substring(0, cut + 1);
            }
            return text.substring(0, text.length() - 1);
        }
        if (input.isPaste()) {
            String clip = client.keyboard.getClipboard().replaceAll("[\\r\\n\\t]", " ");
            String out = text + clip;
            return out.length() > maxLen ? out.substring(0, maxLen) : out;
        }
        return text;
    }

    private static boolean isEnter(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (closingAt >= 0) return true;
        int keyCode = input.key();
        if (binding != null) {
            boolean clear = keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE;
            binding.setKey(clear ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
            binding = null;
            return true;
        }
        if (editing != null) {
            if (isEnter(keyCode)) commitEdit();
            else if (keyCode == GLFW.GLFW_KEY_ESCAPE) editing = null;
            else editBuffer = editText(editBuffer, input, 256);
            return true;
        }
        if (configFocused) {
            if (isEnter(keyCode)) {
                if (!configName.isBlank()) {
                    try {
                        Config.saveProfile(configName);
                        ChatUtil.info("Saved config " + configName.trim());
                        configName = "";
                        profilesAt = 0;
                    } catch (IllegalArgumentException e) {
                        ChatUtil.error(e.getMessage());
                    }
                }
                configFocused = false;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) configFocused = false;
            else configName = editText(configName, input, 32);
            return true;
        }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || isEnter(keyCode)) searchFocused = false;
            else setQuery(editText(query, input, 64));
            return true;
        }
        // the menu's own key closes it again
        if (keyCode == gui().getKey() && keyCode != GLFW.GLFW_KEY_UNKNOWN) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (closingAt >= 0) return true;
        if (!input.isValidChar()) return false;
        if (editing != null) {
            if (editBuffer.length() < 256) editBuffer += input.asString();
            return true;
        }
        if (configFocused) {
            if (configName.length() < 32) configName += input.asString();
            return true;
        }
        if (binding == null) {
            // Typing anywhere goes to search, like a command palette.
            String s = input.asString();
            if (!searchFocused && s.isBlank()) return true;
            searchFocused = true;
            searchPanel.open = true;
            if (query.length() < 64) setQuery(query + s);
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        if (closingAt >= 0) return;
        commitEdit();
        binding = null;
        dragging = null;
        sliding = null;
        closingAt = System.currentTimeMillis();
    }

    @Override
    public void removed() {
        commitEdit();
        Config.save();
    }

    private static String keyName(int key) {
        if (key < 0 || key >= KEY_NAMES.length) return keyNameUncached(key);
        String s = KEY_NAMES[key];
        if (s == null) KEY_NAMES[key] = s = keyNameUncached(key);
        return s;
    }

    private static String keyNameUncached(int key) {
        String name = InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
        return name.length() > 7 ? name.substring(0, 7) : name;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
