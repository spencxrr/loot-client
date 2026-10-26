package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.Targets;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class AutoLog extends Module {
    private final NumberSetting health = add(new NumberSetting("Health", "Disconnect at or below this health (0 = off).", 6, 0, 19, 1));
    private final BoolSetting noTotems = add(new BoolSetting("NoTotems", "Disconnect when you run out of totems.", false));
    private final NumberSetting playerRange = add(new NumberSetting("PlayerRange", "Disconnect when a non-friend gets this close (0 = off).", 0, 0, 128, 4));
    private final BoolSetting toggleOff = add(new BoolSetting("ToggleOff", "Turn off after logging so you can rejoin.", true));

    public AutoLog() {
        super("Auto Log", "Disconnects you when things get dangerous.", Category.MISC);
    }

    @Override
    public void onTick() {
        String reason = null;
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (health.getInt() > 0 && hp <= health.getInt()) reason = "health was " + Math.round(hp);
        else if (noTotems.get() && mc.player.getInventory().count(Items.TOTEM_OF_UNDYING) == 0) reason = "out of totems";
        else if (playerRange.getInt() > 0) {
            var p = Targets.closestPlayer(playerRange.get());
            if (p != null) reason = p.getGameProfile().name() + " got within " + playerRange.getInt() + " blocks";
        }
        if (reason == null) return;
        if (toggleOff.get()) setEnabled(false);
        mc.getNetworkHandler().getConnection().disconnect(Text.literal("[Loot Client] Auto Log: " + reason));
    }
}
