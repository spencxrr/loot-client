package dev.loot.client.gui;

/**
 * Loot Client palette. Defaults to deep navy surfaces with a soft periwinkle accent; other
 * presets swap the accent (and optionally the surfaces) at runtime via {@link #apply}.
 */
public final class Theme {
    private Theme() {}

    public static final String[] PRESETS = {"Lavender", "Ocean", "Rose", "Mint", "Grape", "Sunset", "Mono"};
    public static final String[] SURFACES = {"Navy", "Black", "Plum"};

    public static int ACCENT = 0xFFA3AEFF;
    public static int ACCENT_SOFT = 0xFF8A94E6;
    public static int ACCENT_DIM = 0x55A3AEFF;
    public static int ACCENT_GLOW = 0x22A3AEFF;

    public static int SURFACE = 0xEE10121C;       // panel body
    public static int SURFACE_RAISED = 0xF5171A28; // header
    public static int ROW_HOVER = 0xFF1F2335;
    public static int ROW_ACTIVE = 0x1FA3AEFF;
    public static int SETTINGS_BG = 0xEE0C0E16;
    public static final int DIVIDER = 0x22FFFFFF;

    public static int TEXT = 0xFFE8EAF6;
    public static int TEXT_MUTED = 0xFF8C91AD;
    public static int TEXT_FAINT = 0xFF5D6180;

    public static int TOGGLE_OFF = 0xFF2A2E42;
    public static int KNOB_OFF = 0xFF8C91AD;
    public static final int KNOB_ON = 0xFFF4F5FF;

    public static final int GOOD = 0xFF7FE0B0;
    public static final int BAD = 0xFFF08CA0;

    public static void apply(String preset, String surface) {
        int accent = switch (preset) {
            case "Ocean" -> 0x6EC8FF;
            case "Rose" -> 0xFF93BE;
            case "Mint" -> 0x7FE6C3;
            case "Grape" -> 0xC395FF;
            case "Sunset" -> 0xFFB077;
            case "Mono" -> 0xDCDDE6;
            default -> 0xA3AEFF;
        };
        ACCENT = 0xFF000000 | accent;
        ACCENT_SOFT = lerp(ACCENT, 0xFF000000, 0.15f);
        ACCENT_DIM = 0x55000000 | accent;
        ACCENT_GLOW = 0x22000000 | accent;
        ROW_ACTIVE = 0x1F000000 | accent;

        int base = switch (surface) {
            case "Black" -> 0x0B0B0E;
            case "Plum" -> 0x16101E;
            default -> 0x10121C;
        };
        SURFACE = 0xEE000000 | base;
        SURFACE_RAISED = 0xF5000000 | (lerp(0xFF000000 | base, 0xFFFFFFFF, 0.04f) & 0xFFFFFF);
        SETTINGS_BG = 0xEE000000 | (lerp(0xFF000000 | base, 0xFF000000, 0.25f) & 0xFFFFFF);
        // hover rows and the off-toggle pick up a hint of the accent so every preset feels coherent
        ROW_HOVER = lerp(0xFF000000 | base, ACCENT, 0.08f) | 0xFF000000;
        ROW_HOVER = lerp(ROW_HOVER, 0xFFFFFFFF, 0.04f);
        TOGGLE_OFF = lerp(lerp(0xFF000000 | base, 0xFFFFFFFF, 0.1f), ACCENT, 0.08f);
        TEXT = lerp(0xFFF2F3F8, ACCENT, 0.06f);
        TEXT_MUTED = lerp(0xFF8E909E, ACCENT, 0.2f);
        TEXT_FAINT = lerp(0xFF5E606E, ACCENT, 0.2f);
        KNOB_OFF = TEXT_MUTED;
    }

    public static int lerp(int a, int b, float t) {
        int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16
            | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }
}
