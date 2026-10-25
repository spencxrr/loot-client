package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;

/** FPS and visibility tweaks. The actual cancelling happens in the mixins that read these settings. */
public class NoRender extends Module {
    public final BoolSetting particles = add(new BoolSetting("Particles", "Skip all particles (big FPS win in lag machines and explosions).", true));
    public final BoolSetting weather = add(new BoolSetting("Weather", "No rain, snow or thunder darkening.", true));
    public final BoolSetting hurtCam = add(new BoolSetting("HurtCam", "No screen shake when taking damage.", true));

    public NoRender() {
        super("NoRender", "Skips expensive or annoying rendering.", Category.RENDER);
    }
}
