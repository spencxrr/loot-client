package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.entity.player.PlayerAbilities;

/**
 * Creative-style flight. Works on servers with allow-flight=true (common on anarchy);
 * vanilla's "flying is not enabled" kick still applies otherwise.
 */
public class Flight extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", "Flight speed multiplier.", 1, 0.1, 10, 0.1));

    public Flight() {
        super("Flight", "Fly like in creative.", Category.MISC);
    }

    @Override
    public void onTick() {
        PlayerAbilities abilities = mc.player.getAbilities();
        abilities.allowFlying = true;
        abilities.flying = true;
        abilities.setFlySpeed(0.05f * speed.getFloat());
    }

    @Override
    public void onDisable() {
        if (!inGame()) return;
        PlayerAbilities abilities = mc.player.getAbilities();
        abilities.setFlySpeed(0.05f);
        if (!abilities.creativeMode && !mc.player.isSpectator()) {
            abilities.flying = false;
            abilities.allowFlying = false;
        }
    }

    @Override
    public String getInfo() {
        return speed.display();
    }
}
