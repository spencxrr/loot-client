package dev.loot.client.module.render;

import dev.loot.client.gui.Theme;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Draws where the pearl (or other throwable/bow shot) in your hand will land, plus the path of
 * every ender pearl already in the air, so you can see where people are about to teleport.
 */
public class PearlTrajectory extends Module {
    private final BoolSetting others = add(new BoolSetting("InFlight", "Show the path of pearls already thrown.", true));
    private final BoolSetting allItems = add(new BoolSetting("AllThrowables", "Also bows, tridents, snowballs, potions.", true));

    private record Physics(double speed, double gravity, double drag) {}

    public PearlTrajectory() {
        super("Pearl Trajectory", "Shows where pearls and projectiles will land.", Category.RENDER);
    }

    private Physics physicsFor(ItemStack st) {
        if (st.isOf(Items.ENDER_PEARL)) return new Physics(1.5, 0.03, 0.99);
        if (!allItems.get()) return null;
        if (st.isOf(Items.SNOWBALL) || st.isOf(Items.EGG) || st.isOf(Items.BLUE_EGG) || st.isOf(Items.BROWN_EGG)) return new Physics(1.5, 0.03, 0.99);
        if (st.isOf(Items.SPLASH_POTION) || st.isOf(Items.LINGERING_POTION)) return new Physics(0.5, 0.05, 0.99);
        if (st.isOf(Items.EXPERIENCE_BOTTLE)) return new Physics(0.7, 0.07, 0.99);
        if (st.isOf(Items.WIND_CHARGE)) return new Physics(1.5, 0, 1.0);
        if (st.isOf(Items.TRIDENT)) return new Physics(2.5, 0.05, 0.99);
        if (st.isOf(Items.CROSSBOW)) return new Physics(3.15, 0.05, 0.99);
        if (st.isOf(Items.BOW)) {
            if (!mc.player.isUsingItem()) return null;
            float pull = BowItem.getPullProgress(mc.player.getItemUseTime());
            return pull < 0.1f ? null : new Physics(3.0 * pull, 0.05, 0.99);
        }
        return null;
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        for (Hand hand : Hand.values()) {
            Physics ph = physicsFor(mc.player.getStackInHand(hand));
            if (ph == null) continue;
            Vec3d start = mc.player.getLerpedPos(tickDelta).add(0, mc.player.getStandingEyeHeight() - 0.1, 0);
            float yaw = mc.player.getYaw(tickDelta), pitch = mc.player.getPitch(tickDelta);
            if (mc.player.getStackInHand(hand).isOf(Items.SPLASH_POTION) || mc.player.getStackInHand(hand).isOf(Items.LINGERING_POTION)
                || mc.player.getStackInHand(hand).isOf(Items.EXPERIENCE_BOTTLE)) pitch -= 20; // these are thrown with an upward tilt
            Vec3d dir = Vec3d.fromPolar(pitch, yaw).multiply(ph.speed());
            Vec3d pv = mc.player.getVelocity();
            dir = dir.add(pv.x, mc.player.isOnGround() ? 0 : pv.y, pv.z);
            // Draw the line from the hand rather than the eye so it doesn't sit in your face.
            Vec3d side = Vec3d.fromPolar(0, yaw + (hand == Hand.MAIN_HAND ? 90 : -90)).multiply(0.15);
            simulate(r, start, dir, ph, Theme.ACCENT, side);
            break;
        }

        if (!others.get()) return;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EnderPearlEntity pearl)) continue;
            simulate(r, pearl.getLerpedPos(tickDelta), pearl.getVelocity(), new Physics(0, 0.03, 0.99), 0xFFFF6B9A, Vec3d.ZERO);
        }
    }

    private void simulate(Render3D r, Vec3d pos, Vec3d vel, Physics ph, int color, Vec3d drawOffset) {
        Vec3d prev = pos;
        for (int i = 0; i < 300; i++) {
            Vec3d next = prev.add(vel);
            HitResult hit = mc.world.raycast(new RaycastContext(prev, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            Entity hitEntity = null;
            Box sweep = new Box(prev, next).expand(0.3);
            for (Entity e : mc.world.getOtherEntities(mc.player, sweep, en -> en.isAlive() && en.canHit())) {
                if (e.getBoundingBox().expand(0.3).raycast(prev, next).isPresent()) {
                    hitEntity = e;
                    break;
                }
            }
            if (hitEntity != null) {
                r.line(prev.add(drawOffset), hitEntity.getBoundingBox().getCenter(), color);
                r.box(hitEntity.getBoundingBox(), color, 50);
                return;
            }
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3d land = hit.getPos();
                r.line(prev.add(drawOffset), land, color);
                if (hit instanceof BlockHitResult bh) {
                    r.box(new Box(bh.getBlockPos()), color, 45);
                }
                return;
            }
            float t = MathHelper.clamp(i / 6f, 0, 1);
            r.line(prev.add(drawOffset.multiply(1 - t)), next.add(drawOffset.multiply(1 - Math.min(1, (i + 1) / 6f))), color);
            prev = next;
            vel = vel.multiply(ph.drag());
            vel = vel.add(0, -ph.gravity(), 0);
            if (prev.y < mc.world.getBottomY() - 16) return;
        }
    }
}
