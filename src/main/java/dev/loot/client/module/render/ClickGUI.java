package dev.loot.client.module.render;

import dev.loot.client.gui.ClickGuiScreen;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import org.lwjgl.glfw.GLFW;

public class ClickGUI extends Module {
    public ClickGUI() {
        super("ClickGUI", "Opens the module menu.", Category.CLIENT);
        setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onEnable() {
        mc.setScreen(new ClickGuiScreen());
        setEnabled(false);
    }
}
