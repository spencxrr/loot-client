package dev.loot.client.module.render;

import dev.loot.client.gui.Theme;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An expanding, fading ring on the ground wherever you (or others) jump. */
public class JumpCircles extends Module {
    private final NumberSetting radius = add(new NumberSetting("Radius", "Final ring size.", 1.4, 0.4, 4, 0.1));
    private final NumberSetting duration = add(new NumberSetting("Duration", "Ring lifetime in ms.", 900, 200, 3000, 50));
    private final BoolSetting others = add(new BoolSetting("Others", "Rings for other players too.", false));

    private record Circle(Vec3d pos, long start) {}

    private final List<Circle> circles = new ArrayList<>();
    private final Map<UUID, Boolean> wasOnGround = new HashMap<>();

    public JumpCircles() {
        super("Jump Circles", "Rings on the ground when you jump.", Category.RENDER);
    }

    @Override
    public void onTick() {
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && !others.get()) continue;
            boolean ground = p.isOnGround();
            Boolean before = wasOnGround.put(p.getUuid(), ground);
            if (before != null && before && !ground && p.getVelocity().y > 0.2) circles.add(new Circle(p.getEntityPos(), System.currentTimeMillis()));
        }
        if (wasOnGround.size() > 256) wasOnGround.clear();
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        long now = System.currentTimeMillis();
        double life = duration.get();
        Iterator<Circle> it = circles.iterator();
        while (it.hasNext()) {
            Circle c = it.next();
            double t = (now - c.start()) / life;
            if (t >= 1) {
                it.remove();
                continue;
            }
            double ease = 1 - Math.pow(1 - t, 3);
            double rad = radius.get() * ease;
            int alpha = (int) (255 * (1 - t));
            int color = (Theme.lerp(Theme.ACCENT, 0xFFFF9AD5, (float) t) & 0x00FFFFFF) | (alpha << 24);
            ring(r, c.pos().add(0, 0.02, 0), rad, color);
            ring(r, c.pos().add(0, 0.02, 0), rad * 0.7, (color & 0x00FFFFFF) | ((alpha / 2) << 24));
        }
    }

    private static void ring(Render3D r, Vec3d c, double rad, int color) {
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
            r.line(c.x + Math.cos(a1) * rad, c.y, c.z + Math.sin(a1) * rad, c.x + Math.cos(a2) * rad, c.y, c.z + Math.sin(a2) * rad, color);
        }
    }

    @Override
    public void onDisable() {
        circles.clear();
        wasOnGround.clear();
    }
}
