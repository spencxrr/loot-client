package dev.loot.client.module.combat;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;

/** Grows other entities' hitboxes for your crosshair only. Applied in EntityMixin#getTargetingMargin. */
public class HitBox extends Module {
    public final NumberSetting expand = add(new NumberSetting("Expand", "Extra blocks added on each side.", 0.15, 0, 1, 0.05));
    public final BoolSetting playersOnly = add(new BoolSetting("PlayersOnly", "Only expand player hitboxes.", true));

    public HitBox() {
        super("HitBox", "Makes targets easier to hit.", Category.COMBAT);
    }

    @Override
    public String getInfo() {
        return expand.display();
    }
}
