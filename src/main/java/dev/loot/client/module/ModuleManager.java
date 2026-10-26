package dev.loot.client.module;

import dev.loot.client.module.movement.Flight;
import dev.loot.client.module.movement.Sprint;
import dev.loot.client.module.movement.Step;
import dev.loot.client.module.player.AutoTotem;
import dev.loot.client.module.player.NoFall;
import dev.loot.client.module.player.Velocity;
import dev.loot.client.module.render.BlockESP;
import dev.loot.client.module.render.ClickGUI;
import dev.loot.client.module.render.ESP;
import dev.loot.client.module.render.Fullbright;
import dev.loot.client.module.render.Hud;
import dev.loot.client.module.render.NoRender;
import dev.loot.client.module.render.StorageESP;
import dev.loot.client.module.render.Tracers;
import dev.loot.client.module.world.BaseFinder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final Map<Class<? extends Module>, Module> byClass = new HashMap<>();
    private final Map<Integer, Boolean> keyStates = new HashMap<>();

    public ModuleManager() {
        // Render
        register(new ClickGUI());
        register(new Hud());
        register(new Fullbright());
        register(new NoRender());
        register(new ESP());
        register(new Tracers());
        register(new StorageESP());
        register(new BlockESP());
        // World
        register(new BaseFinder());
        // Player
        register(new AutoTotem());
        register(new NoFall());
        register(new Velocity());
        // Movement
        register(new Sprint());
        register(new Step());
        register(new Flight());
    }

    private void register(Module module) {
        modules.add(module);
        byClass.put(module.getClass(), module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModules(Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).toList();
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> clazz) {
        return (T) byClass.get(clazz);
    }

    public Module get(String name) {
        for (Module m : modules) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    public boolean isEnabled(Class<? extends Module> clazz) {
        Module m = byClass.get(clazz);
        return m != null && m.isEnabled();
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        pollKeybinds(mc);
        if (mc.player == null || mc.world == null) return;
        for (Module m : modules) {
            if (m.isEnabled()) m.onTick();
        }
    }

    /** Edge-triggered keybind polling, only while no screen is open. */
    private void pollKeybinds(MinecraftClient mc) {
        var window = mc.getWindow();
        boolean canToggle = mc.currentScreen == null;
        for (Module m : modules) {
            int key = m.getKey();
            if (key == GLFW.GLFW_KEY_UNKNOWN) continue;
            boolean down = InputUtil.isKeyPressed(window, key);
            boolean wasDown = keyStates.getOrDefault(key, false);
            if (down && !wasDown && canToggle) m.toggle();
        }
        keyStates.clear();
        for (Module m : modules) {
            int key = m.getKey();
            if (key != GLFW.GLFW_KEY_UNKNOWN) keyStates.put(key, InputUtil.isKeyPressed(window, key));
        }
    }
}
