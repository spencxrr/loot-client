package dev.loot.client.util;

import net.minecraft.util.math.MathHelper;

public final class ColorUtil {
    private ColorUtil() {}

    public static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (alpha & 0xFF) << 24;
    }

    /** Smooth hue cycle, offset lets list entries ripple. */
    public static int rainbow(long offsetMs, float saturation, float brightness) {
        float hue = ((System.currentTimeMillis() + offsetMs) % 4000L) / 4000f;
        return 0xFF000000 | MathHelper.hsvToRgb(hue, saturation, brightness);
    }
}
