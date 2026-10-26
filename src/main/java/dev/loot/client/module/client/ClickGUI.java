package dev.loot.client.module.client;

import dev.loot.client.gui.ClickGuiScreen;
import dev.loot.client.gui.Theme;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.ModeSetting;
import dev.loot.client.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

/** Opens the menu, and holds the look settings: theme, font, text size and menu size. */
public class ClickGUI extends Module {
    public final ModeSetting theme = add(new ModeSetting("Theme", "Accent color.", "Lavender", Theme.PRESETS).onChange(this::applyTheme));
    public final ModeSetting surface = add(new ModeSetting("Background", "Panel color.", "Navy", Theme.SURFACES).onChange(this::applyTheme));
    public final ModeSetting font = add(new ModeSetting("Font", "Font used by the menu and HUD.", "Poppins",
        "Poppins", "Outfit", "Manrope", "FiraSans", "Ubuntu", "Minecraft"));
    public final NumberSetting fontSize = add(new NumberSetting("FontSize", "Text size in percent.", 100, 70, 130, 5));
    public final NumberSetting scale = add(new NumberSetting("MenuSize", "Menu size in percent. 100 fits every panel on screen at any resolution.", 100, 60, 150, 5));

    public ClickGUI() {
        super("Click GUI", "Opens the module menu. Right click it for theme, font and size.", Category.CLIENT);
        setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        applyTheme();
    }

    private void applyTheme() {
        Theme.apply(theme.get(), surface.get());
    }

    @Override
    public void onEnable() {
        mc.setScreen(new ClickGuiScreen());
        setEnabled(false);
    }
}
