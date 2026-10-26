package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.NumberSetting;

/** Scales knockback the server applies to you. Applied in ClientPlayNetworkHandlerMixin. */
public class Velocity extends Module {
    public final NumberSetting horizontal = add(new NumberSetting("Horizontal", "Percent of horizontal knockback kept.", 0, 0, 100, 5));
    public final NumberSetting vertical = add(new NumberSetting("Vertical", "Percent of vertical knockback kept.", 0, 0, 100, 5));

    public Velocity() {
        super("Velocity", "Reduces knockback from hits.", Category.COMBAT);
    }

    @Override
    public String getInfo() {
        return horizontal.getInt() + "%/" + vertical.getInt() + "%";
    }
}
