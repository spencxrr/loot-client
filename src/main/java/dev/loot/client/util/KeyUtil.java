package dev.loot.client.util;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class KeyUtil {
    private KeyUtil() {}

    /** Whether the key or mouse button bound to this binding is physically held right now. */
    public static boolean physicallyDown(KeyBinding binding) {
        MinecraftClient mc = MinecraftClient.getInstance();
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        if (key == null || key.getCode() == GLFW.GLFW_KEY_UNKNOWN) return false;
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS;
        }
        if (key.getCategory() != InputUtil.Type.KEYSYM) return false;
        return InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
    }

    /** Hands a binding back to the real keyboard, so turning a module off doesn't drop a key you're holding. */
    public static void release(KeyBinding binding) {
        binding.setPressed(MinecraftClient.getInstance().currentScreen == null && physicallyDown(binding));
    }
}
