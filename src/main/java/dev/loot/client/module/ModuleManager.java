package dev.loot.client.module;

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
        // combat
        register(new dev.loot.client.module.combat.AimAssist());
        register(new dev.loot.client.module.combat.AnchorMacro());
        register(new dev.loot.client.module.combat.AnchorOptimizer());
        register(new dev.loot.client.module.combat.AutoCrystal());
        register(new dev.loot.client.module.combat.AutoDoubleHand());
        register(new dev.loot.client.module.combat.AutoHitCrystal());
        register(new dev.loot.client.module.combat.AutoInvTotem());
        register(new dev.loot.client.module.combat.AutoJumpReset());
        register(new dev.loot.client.module.combat.AutoPot());
        register(new dev.loot.client.module.combat.AutoTotem());
        register(new dev.loot.client.module.combat.CrystalOptimizer());
        register(new dev.loot.client.module.combat.DoubleAnchor());
        register(new dev.loot.client.module.combat.ElytraSwap());
        register(new dev.loot.client.module.combat.HitBox());
        register(new dev.loot.client.module.combat.HoverTotem());
        register(new dev.loot.client.module.combat.MaceBomber());
        register(new dev.loot.client.module.combat.MaceSwap());
        register(new dev.loot.client.module.combat.NoHitDelay());
        register(new dev.loot.client.module.combat.PotionRefill());
        register(new dev.loot.client.module.combat.ShieldBreaker());
        register(new dev.loot.client.module.combat.SpearSwap());
        register(new dev.loot.client.module.combat.StaticHitBoxes());
        register(new dev.loot.client.module.combat.TotemOffhand());
        register(new dev.loot.client.module.combat.TriggerBot());
        register(new dev.loot.client.module.combat.Velocity());
        // misc
        register(new dev.loot.client.module.misc.AutoClicker());
        register(new dev.loot.client.module.misc.AutoEat());
        register(new dev.loot.client.module.misc.AutoFirework());
        register(new dev.loot.client.module.misc.AutoLog());
        register(new dev.loot.client.module.misc.AutoLoot());
        register(new dev.loot.client.module.misc.AutoMine());
        register(new dev.loot.client.module.misc.AutoReconnect());
        register(new dev.loot.client.module.misc.AutoRespawn());
        register(new dev.loot.client.module.misc.AutoTool());
        register(new dev.loot.client.module.misc.AutoTpa());
        register(new dev.loot.client.module.misc.AutoWalk());
        register(new dev.loot.client.module.misc.CordSnapper());
        register(new dev.loot.client.module.misc.ElytraGlide());
        register(new dev.loot.client.module.misc.FakePlayer());
        register(new dev.loot.client.module.misc.FastPlace());
        register(new dev.loot.client.module.misc.Flight());
        register(new dev.loot.client.module.misc.KeyPearl());
        register(new dev.loot.client.module.misc.KeyWindCharge());
        register(new dev.loot.client.module.misc.NameProtect());
        register(new dev.loot.client.module.misc.NoFall());
        register(new dev.loot.client.module.misc.SkinProtect());
        register(new dev.loot.client.module.misc.Sprint());
        register(new dev.loot.client.module.misc.Step());
        register(new dev.loot.client.module.misc.WeatherNotifier());
        // basefinding
        register(new dev.loot.client.module.basefinding.BaseFinder());
        // render
        register(new dev.loot.client.module.render.BlockESP());
        register(new dev.loot.client.module.render.ESP());
        register(new dev.loot.client.module.render.Fullbright());
        register(new dev.loot.client.module.render.Hud());
        register(new dev.loot.client.module.render.NoRender());
        register(new dev.loot.client.module.render.StorageESP());
        register(new dev.loot.client.module.render.Tracers());
        // client
        register(new dev.loot.client.module.client.ClickGUI());
    }

    private void register(Module module) {
        modules.add(module);
        byClass.put(module.getClass(), module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModules(Category category) {
        return modules.stream().filter(m -> m.getCategory() == category)
            .sorted(java.util.Comparator.comparing(m -> m.getName().toLowerCase(java.util.Locale.ROOT))).toList();
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> clazz) {
        return (T) byClass.get(clazz);
    }

    /** Looks a module up by name, ignoring case and spaces ("autocrystal" finds "Auto Crystal"). */
    public Module get(String name) {
        String key = name.replace(" ", "").replace("_", "");
        for (Module m : modules) {
            if (m.getName().replace(" ", "").equalsIgnoreCase(key)) return m;
        }
        return null;
    }

    public boolean firePacketSend(net.minecraft.network.packet.Packet<?> packet) {
        boolean cancel = false;
        for (Module m : modules) {
            if (m.isEnabled() && safe(() -> m.onPacketSend(packet))) cancel = true;
        }
        return cancel;
    }

    public boolean firePacketReceive(net.minecraft.network.packet.Packet<?> packet) {
        boolean cancel = false;
        for (Module m : modules) {
            if (m.isEnabled() && safe(() -> m.onPacketReceive(packet))) cancel = true;
        }
        return cancel;
    }

    public boolean fireAttack(net.minecraft.entity.Entity target) {
        dev.loot.client.util.Targets.markTarget(target);
        boolean cancel = false;
        for (Module m : modules) {
            if (m.isEnabled() && safe(() -> m.onAttack(target))) cancel = true;
        }
        return cancel;
    }

    private static boolean safe(java.util.function.BooleanSupplier call) {
        try {
            return call.getAsBoolean();
        } catch (Exception e) {
            dev.loot.client.LootClient.LOGGER.error("Module hook failed", e);
            return false;
        }
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
            if (!m.isEnabled()) continue;
            try {
                m.onTick();
            } catch (Exception e) {
                dev.loot.client.LootClient.LOGGER.error("{} tick failed", m.getName(), e);
            }
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
