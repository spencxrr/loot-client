package dev.loot.client.module.misc;

import dev.loot.client.LootClient;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.StringSetting;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Accepts teleport requests (EssentialsX style "X has requested to teleport to you"). */
public class AutoTpa extends Module {
    private static final Pattern REQUEST = Pattern.compile("(\\w{3,16}) has requested (?:to teleport to you|that you teleport to them)", Pattern.CASE_INSENSITIVE);

    private final BoolSetting friendsOnly = add(new BoolSetting("FriendsOnly", "Only accept requests from friends.", true));
    private final StringSetting command = add(new StringSetting("Command", "Command sent to accept, without the slash.", "tpaccept"));

    public AutoTpa() {
        super("Auto Tpa", "Automatically accepts teleport requests.", Category.MISC);
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (!(packet instanceof GameMessageS2CPacket msg) || msg.overlay()) return false;
        Matcher m = REQUEST.matcher(msg.content().getString());
        if (!m.find()) return false;
        String name = m.group(1);
        if (friendsOnly.get() && !LootClient.friends().isFriend(name)) return false;
        String cmd = command.get().replaceFirst("^/", "").trim().toLowerCase(Locale.ROOT);
        mc.execute(() -> {
            if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand(cmd + " " + name);
        });
        return false;
    }
}
