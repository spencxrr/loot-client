package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.client.gui.screen.DeathScreen;

public class AutoRespawn extends Module {
    private final BoolSetting sayCoords = add(new BoolSetting("DeathCoords", "Print where you died in chat (only you see it).", true));

    public AutoRespawn() {
        super("Auto Respawn", "Respawns instantly when you die.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (!(mc.currentScreen instanceof DeathScreen)) return;
        if (sayCoords.get()) {
            ChatUtil.info(String.format("You died at %d, %d, %d in %s", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(),
                mc.world.getRegistryKey().getValue().getPath()));
        }
        mc.player.requestRespawn();
        mc.setScreen(null);
    }
}
