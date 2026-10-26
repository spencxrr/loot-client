package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.StringSetting;

/** Replaces your username everywhere text is drawn, for screenshots and streams. */
public class NameProtect extends Module {
    private final StringSetting replacement = add(new StringSetting("Name", "What to show instead of your name.", "Me"));

    public NameProtect() {
        super("Name Protect", "Hides your username on screen.", Category.MISC);
    }

    public String apply(String text) {
        if (!isEnabled() || text == null) return text;
        String own = mc.getSession().getUsername();
        if (own == null || own.isEmpty() || !text.contains(own)) return text;
        return text.replace(own, replacement.get());
    }
}
