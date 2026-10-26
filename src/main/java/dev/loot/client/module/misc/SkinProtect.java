package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;

/** Shows default skins so screenshots don't give away who you (or others) are. Applied in PlayerListEntryMixin. */
public class SkinProtect extends Module {
    public final BoolSetting self = add(new BoolSetting("Self", "Hide your own skin.", true));
    public final BoolSetting others = add(new BoolSetting("Others", "Hide other players' skins too.", false));

    public SkinProtect() {
        super("Skin Protect", "Replaces skins with default ones on your screen.", Category.MISC);
    }
}
