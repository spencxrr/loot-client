package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

public class AutoReconnect extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Seconds to wait before reconnecting.", 5, 1, 60, 1));

    /** Set by ConnectScreenMixin whenever you join a server. */
    public static ServerAddress lastAddress;
    public static ServerInfo lastInfo;

    private long disconnectedAt = -1;

    public AutoReconnect() {
        super("Auto Reconnect", "Rejoins the last server after you get kicked.", Category.MISC);
    }

    /** Runs every client tick even out of game, since the disconnect screen has no world. */
    public void clientTick() {
        if (!isEnabled() || !(mc.currentScreen instanceof DisconnectedScreen) || lastAddress == null) {
            disconnectedAt = -1;
            return;
        }
        if (disconnectedAt == -1) disconnectedAt = System.currentTimeMillis();
        if (System.currentTimeMillis() - disconnectedAt < delay.get() * 1000) return;
        disconnectedAt = -1;
        ConnectScreen.connect(new TitleScreen(), mc, lastAddress, lastInfo, false, null);
    }

    public int secondsLeft() {
        if (disconnectedAt == -1) return -1;
        return (int) Math.ceil(delay.get() - (System.currentTimeMillis() - disconnectedAt) / 1000.0);
    }
}
