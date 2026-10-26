package dev.loot.client.gui;

/** Loot Client palette: deep navy surfaces with a soft periwinkle (blue-lavender) accent. */
public final class Theme {
    private Theme() {}

    public static final int ACCENT = 0xFFA3AEFF;        // periwinkle
    public static final int ACCENT_SOFT = 0xFF8A94E6;
    public static final int ACCENT_DIM = 0x55A3AEFF;
    public static final int ACCENT_GLOW = 0x22A3AEFF;

    public static final int SURFACE = 0xEE10121C;       // panel body
    public static final int SURFACE_RAISED = 0xF5171A28; // header
    public static final int ROW_HOVER = 0xFF1F2335;
    public static final int ROW_ACTIVE = 0x1FA3AEFF;
    public static final int SETTINGS_BG = 0xEE0C0E16;
    public static final int DIVIDER = 0x22FFFFFF;

    public static final int TEXT = 0xFFE8EAF6;
    public static final int TEXT_MUTED = 0xFF8C91AD;
    public static final int TEXT_FAINT = 0xFF5D6180;

    public static final int TOGGLE_OFF = 0xFF2A2E42;
    public static final int KNOB_OFF = 0xFF8C91AD;
    public static final int KNOB_ON = 0xFFF4F5FF;

    public static final int GOOD = 0xFF7FE0B0;
    public static final int BAD = 0xFFF08CA0;

    public static int lerp(int a, int b, float t) {
        int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16
            | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }
}
