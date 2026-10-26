package dev.loot.client.module;

import dev.loot.client.render.Render3D;
import dev.loot.client.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    private int key = GLFW.GLFW_KEY_UNKNOWN;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    /** Lets helper objects register settings on a module they belong to. */
    public <S extends Setting<?>> S addSetting(S setting) {
        return add(setting);
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    protected boolean inGame() {
        return mc.player != null && mc.world != null;
    }

    public void onEnable() {}

    public void onDisable() {}

    public void onTick() {}

    public void onRender3D(Render3D r, float tickDelta) {}

    public void onRenderHud(DrawContext ctx, float tickDelta) {}

    /** Called for every outgoing packet. Return true to cancel it. */
    public boolean onPacketSend(Packet<?> packet) {
        return false;
    }

    /** Called on the network thread for every incoming packet. Return true to drop it. */
    public boolean onPacketReceive(Packet<?> packet) {
        return false;
    }

    /** Called right before the player attacks an entity. Return true to cancel the attack. */
    public boolean onAttack(Entity target) {
        return false;
    }

    /** Called for every chunk the client loads, enabled or not. */
    public void onChunkLoad(net.minecraft.world.chunk.WorldChunk chunk) {}

    /** Called for every chunk the client unloads, enabled or not. */
    public void onChunkUnload(net.minecraft.world.chunk.WorldChunk chunk) {}

    /** Extra info shown next to the name in the HUD module list. */
    public String getInfo() {
        return null;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public Setting<?> getSetting(String name) {
        for (Setting<?> s : settings) {
            if (s.getName().equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }
}
