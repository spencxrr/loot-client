package dev.loot.client.mixin;

import dev.loot.client.LootClient;
import dev.loot.client.module.render.FreeLook;
import dev.loot.client.module.render.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean thirdPerson;

    @Shadow protected abstract void setPos(double x, double y, double z);

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void loot$override(World area, Entity focused, boolean third, boolean inverse, float tickDelta, CallbackInfo ci) {
        if (LootClient.modules() == null || focused == null) return;
        Freecam freecam = LootClient.modules().get(Freecam.class);
        if (freecam.isEnabled()) {
            setRotation(freecam.yaw(tickDelta), freecam.pitch(tickDelta));
            var p = freecam.pos(tickDelta);
            setPos(p.x, p.y, p.z);
            thirdPerson = true; // so the player model renders and the hand doesn't
            return;
        }
        FreeLook freeLook = LootClient.modules().get(FreeLook.class);
        if (freeLook.isEnabled() && third) {
            setRotation(freeLook.yaw, freeLook.pitch);
            var eye = focused.getLerpedPos(tickDelta).add(0, focused.getStandingEyeHeight(), 0);
            Vec3d back = Vec3d.fromPolar(freeLook.pitch, freeLook.yaw).multiply(-4);
            HitResult hit = area.raycast(new RaycastContext(eye, eye.add(back), RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, focused));
            double dist = hit.getType() == HitResult.Type.MISS ? 4 : Math.max(0.2, hit.getPos().distanceTo(eye) - 0.2);
            Vec3d cam = eye.add(back.normalize().multiply(dist));
            setPos(cam.x, cam.y, cam.z);
        }
    }
}
