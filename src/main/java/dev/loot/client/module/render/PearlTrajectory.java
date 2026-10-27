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
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Predicts where the pearl (or other projectile) in your hand lands, tick for tick the way the
 * game moves it, and shows the path of pearls already flying. For pearls the landing block is
 * green when you'd end up standing on top of it and red when you'd hit a side or ceiling and fall.
 */
public class PearlTrajectory extends Module {
    private final BoolSetting others = add(new BoolSetting("InFlight", "Show the path of pearls already thrown.", true));
    private final BoolSetting allItems = add(new BoolSetting("AllThrowables", "Also bows, tridents, snowballs, potions.", true));
    private final BoolSetting landingBox = add(new BoolSetting("LandingBox", "Show where your body ends up after a pearl.", true));

    private static final int GREEN = 0xFF5CE68A, RED = 0xFFFF4D5E;

    /**
     * Motion constants per projectile type. Thrown items apply gravity, then drag, then move;
     * arrows and tridents move first, then drag, then gravity.
     */
    private record Motion(double speed, float pitchOffset, double gravity, double drag, double waterDrag, boolean moveFirst, boolean pearl) {}

    private static final Motion PEARL = new Motion(1.5, 0, 0.03, 0.99, 0.8, false, true);
    private static final Motion SNOWBALL = new Motion(1.5, 0, 0.03, 0.99, 0.8, false, false);
    private static final Motion POTION = new Motion(0.5, -20, 0.05, 0.99, 0.8, false, false);
    private static final Motion XP_BOTTLE = new Motion(0.7, -20, 0.07, 0.99, 0.8, false, false);
    private static final Motion WIND_CHARGE = new Motion(1.5, 0, 0, 1, 1, false, false);
    private static final Motion TRIDENT = new Motion(2.5, 0, 0.05, 0.99, 0.99, true, false);

    public PearlTrajectory() {
        super("Pearl Trajectory", "Shows exactly where pearls and projectiles will land.", Category.RENDER);
    }

    private Motion motionFor(ItemStack st) {
        if (st.isOf(Items.ENDER_PEARL)) return PEARL;
        if (!allItems.get()) return null;
        if (st.isOf(Items.SNOWBALL) || st.isOf(Items.EGG) || st.isOf(Items.BLUE_EGG) || st.isOf(Items.BROWN_EGG)) return SNOWBALL;
        if (st.isOf(Items.SPLASH_POTION) || st.isOf(Items.LINGERING_POTION)) return POTION;
        if (st.isOf(Items.EXPERIENCE_BOTTLE)) return XP_BOTTLE;
        if (st.isOf(Items.WIND_CHARGE)) return WIND_CHARGE;
        if (st.isOf(Items.TRIDENT)) return TRIDENT;
        if (st.isOf(Items.BOW)) {
            float pull = mc.player.isUsingItem() ? BowItem.getPullProgress(mc.player.getItemUseTime()) : 1f;
            return new Motion(3.0 * pull, 0, 0.05, 0.99, 0.6, true, false);
        }
        if (st.isOf(Items.CROSSBOW)) return new Motion(3.15, 0, 0.05, 0.99, 0.6, true, false);
        return null;
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        for (Hand hand : Hand.values()) {
            Motion m = motionFor(mc.player.getStackInHand(hand));
            if (m == null) continue;
            // spawned at eye height minus 0.1, aimed with the same formula the game uses
            Vec3d pos = mc.player.getLerpedPos(tickDelta).add(0, mc.player.getStandingEyeHeight() - 0.1, 0);
            double yaw = Math.toRadians(mc.player.getYaw(tickDelta));
            double pitch = Math.toRadians(mc.player.getPitch(tickDelta));
            double pitchRoll = Math.toRadians(mc.player.getPitch(tickDelta) + m.pitchOffset());
            Vec3d dir = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitchRoll), Math.cos(yaw) * Math.cos(pitch)).normalize().multiply(m.speed());
            Vec3d move = mc.player.getMovement();
            Vec3d vel = dir.add(move.x, mc.player.isOnGround() ? 0 : move.y, move.z);
            simulate(r, pos, vel, m, Theme.ACCENT, true);
            break;
        }

        if (!others.get()) return;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EnderPearlEntity pearl) simulate(r, pearl.getLerpedPos(tickDelta), pearl.getVelocity(), PEARL, 0xFFFF9AD5, false);
        }
    }

    private void simulate(Render3D r, Vec3d pos, Vec3d vel, Motion m, int color, boolean mine) {
        for (int tick = 0; tick < 400; tick++) {
            boolean inWater = mc.world.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER);
            double drag = inWater ? m.waterDrag() : m.drag();
            if (!m.moveFirst()) vel = vel.add(0, -m.gravity(), 0).multiply(drag);
            Vec3d next = pos.add(vel);

            HitResult hit = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            Vec3d end = hit.getType() == HitResult.Type.MISS ? next : hit.getPos();
            Entity entity = entityHit(pos, end);
            if (entity != null) {
                r.line(pos, entity.getBoundingBox().getCenter(), color);
                r.box(entity.getBoundingBox(), 0xFFFFB45C, 50);
                return;
            }
            // skip the first segment of your own throw so the line doesn't sit in your face
            if (!mine || tick > 0) r.line(pos, end, color);
            if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
                drawLanding(r, bh, m.pearl());
                return;
            }
            pos = next;
            if (m.moveFirst()) vel = vel.multiply(drag).add(0, -m.gravity(), 0);
            if (pos.y < mc.world.getBottomY() - 8 || vel.lengthSquared() < 1e-6) return;
        }
    }

    private Entity entityHit(Vec3d from, Vec3d to) {
        Box sweep = new Box(from, to).expand(1);
        Entity best = null;
        double bestSq = Double.MAX_VALUE;
        for (Entity e : mc.world.getOtherEntities(mc.player, sweep, en -> en.isAlive() && en.canHit() && !(en instanceof EnderPearlEntity))) {
            var hit = e.getBoundingBox().expand(0.3).raycast(from, to);
            if (hit.isPresent() && hit.get().squaredDistanceTo(from) < bestSq) {
                bestSq = hit.get().squaredDistanceTo(from);
                best = e;
            }
        }
        return best;
    }

    private void drawLanding(Render3D r, BlockHitResult bh, boolean pearl) {
        BlockPos block = bh.getBlockPos();
        if (!pearl) {
            r.box(new Box(block), Theme.ACCENT, 45);
            return;
        }
        Vec3d at = bh.getPos();
        boolean onTop = bh.getSide() == Direction.UP;
        Box body = new Box(at.x - 0.3, at.y + 0.01, at.z - 0.3, at.x + 0.3, at.y + 1.81, at.z + 0.3);
        boolean stands = onTop && mc.world.isSpaceEmpty(body);
        int color = stands ? GREEN : RED;
        r.box(new Box(block), color, 70);
        if (landingBox.get()) {
            // a side or ceiling hit drops you out of the block, roughly where the pearl touched
            Box ghost = stands ? body : new Box(at.x - 0.3, at.y - 0.9, at.z - 0.3, at.x + 0.3, at.y + 0.9, at.z + 0.3);
            r.boxOutline(ghost, color);
        }
    }
}
