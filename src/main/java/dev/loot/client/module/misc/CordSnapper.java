package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.util.ChatUtil;

/** Bind it to a key: copies your coordinates to the clipboard. */
public class CordSnapper extends Module {
    private final BoolSetting withDimension = add(new BoolSetting("Dimension", "Include the dimension name.", false));

    public CordSnapper() {
        super("Cord Snapper", "Copies your current coordinates to the clipboard.", Category.MISC);
    }

    @Override
    public void onEnable() {
        if (inGame()) {
            String coords = mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
            if (withDimension.get()) coords += " " + mc.world.getRegistryKey().getValue().getPath();
            mc.keyboard.setClipboard(coords);
            ChatUtil.info("Copied " + coords);
        }
        setEnabled(false);
    }
}
