package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.sound.SoundEvents;

public class WeatherNotifier extends Module {
    private final BoolSetting sound = add(new BoolSetting("Sound", "Play a ping with the alert.", true));

    private Boolean wasRaining, wasThundering;

    public WeatherNotifier() {
        super("Weather Notifier", "Tells you when rain or thunder starts and stops.", Category.MISC);
    }

    @Override
    public void onEnable() {
        wasRaining = null;
        wasThundering = null;
    }

    @Override
    public void onTick() {
        boolean rain = mc.world.isRaining(), thunder = mc.world.isThundering();
        if (wasRaining != null) {
            if (thunder && !wasThundering) alert("Thunderstorm started. Mobs spawn in daylight, charged creepers possible.");
            else if (!thunder && wasThundering) alert("Thunderstorm ended.");
            else if (rain && !wasRaining) alert("It started raining.");
            else if (!rain && wasRaining) alert("Rain stopped.");
        }
        wasRaining = rain;
        wasThundering = thunder;
    }

    private void alert(String msg) {
        ChatUtil.info(msg);
        if (sound.get()) mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1f, 1.2f);
    }
}
