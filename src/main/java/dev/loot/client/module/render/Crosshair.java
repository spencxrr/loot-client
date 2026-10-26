package dev.loot.client.module.render;

import dev.loot.client.gui.Theme;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;

/** Replaces the vanilla crosshair with a clean, configurable one in the client accent. */
public class Crosshair extends Module {
    private final NumberSetting length = add(new NumberSetting("Length", "Arm length.", 4, 1, 12, 0.5));
    private final NumberSetting gap = add(new NumberSetting("Gap", "Space between the arms and the center.", 2, 0, 10, 0.5));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", "Arm thickness.", 1, 0.5, 4, 0.5));
    private final BoolSetting dot = add(new BoolSetting("Dot", "Center dot.", false));
    private final BoolSetting outline = add(new BoolSetting("Outline", "Dark outline for contrast.", true));
    private final BoolSetting dynamic = add(new BoolSetting("Dynamic", "Arms spread while moving.", true));
    private final BoolSetting cooldown = add(new BoolSetting("Cooldown", "Show attack cooldown under the crosshair.", true));

    private float spread;

    public Crosshair() {
        super("Crosshair", "A clean custom crosshair.", Category.RENDER);
    }

    @Override
    public void onRenderHud(DrawContext ctx, float tickDelta) {
        if (mc.options.hudHidden || mc.options.getPerspective() != Perspective.FIRST_PERSON) return;
        float cx = ctx.getScaledWindowWidth() / 2f, cy = ctx.getScaledWindowHeight() / 2f;
        double speed = mc.player.getVelocity().horizontalLength();
        float target = dynamic.get() ? (float) Math.min(4, speed * 12) + (mc.player.isOnGround() ? 0 : 1.5f) : 0;
        spread += (target - spread) * 0.2f;

        float g = gap.getFloat() + spread, l = length.getFloat(), t = thickness.getFloat() / 2f;
        int color = Theme.ACCENT;
        if (outline.get()) {
            float o = 0.5f;
            arms(ctx, cx, cy, g - o, l + o * 2, t + o, 0xC0000000);
        }
        arms(ctx, cx, cy, g, l, t, color);
        if (dot.get()) {
            if (outline.get()) fill(ctx, cx - t - 0.5f, cy - t - 0.5f, cx + t + 0.5f, cy + t + 0.5f, 0xC0000000);
            fill(ctx, cx - t, cy - t, cx + t, cy + t, color);
        }
        if (cooldown.get()) {
            float progress = mc.player.getAttackCooldownProgress(0f);
            if (progress < 1f) {
                float w = 8;
                fill(ctx, cx - w, cy + g + l + 3, cx + w, cy + g + l + 4, Theme.ACCENT_DIM);
                fill(ctx, cx - w, cy + g + l + 3, cx - w + 2 * w * progress, cy + g + l + 4, color);
            }
        }
    }

    private static void arms(DrawContext ctx, float cx, float cy, float g, float l, float t, int color) {
        fill(ctx, cx - t, cy - g - l, cx + t, cy - g, color);
        fill(ctx, cx - t, cy + g, cx + t, cy + g + l, color);
        fill(ctx, cx - g - l, cy - t, cx - g, cy + t, color);
        fill(ctx, cx + g, cy - t, cx + g + l, cy + t, color);
    }

    /** Sub-pixel fill: draws at 4x resolution through the matrix stack. */
    private static void fill(DrawContext ctx, float x1, float y1, float x2, float y2, int color) {
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.scale(0.25f, 0.25f);
        ctx.fill(Math.round(x1 * 4), Math.round(y1 * 4), Math.round(x2 * 4), Math.round(y2 * 4), color);
        m.popMatrix();
    }
}
