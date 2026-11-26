package dev.loot.client.module.combat;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.ExplosionImpl;

final class CrystalUtil {
    private CrystalUtil() {}

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    static boolean canPlaceOn(BlockPos base) {
        var world = mc().world;
        BlockState state = world.getBlockState(base);
        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) return false;
        BlockPos up = base.up();
        if (!world.getBlockState(up).isAir()) return false;
        Box space = new Box(up.getX(), up.getY(), up.getZ(), up.getX() + 1, up.getY() + 2, up.getZ() + 1);
        return world.getOtherEntities(null, space).isEmpty();
    }

    static void place(BlockPos base) {
        Vec3d hit = Vec3d.ofCenter(base).add(0, 0.5, 0);
        // place from whichever hand actually holds the crystal (offhand crystals used to do nothing)
        Hand hand = mc().player.getMainHandStack().isOf(net.minecraft.item.Items.END_CRYSTAL) || !mc().player.getOffHandStack().isOf(net.minecraft.item.Items.END_CRYSTAL)
            ? Hand.MAIN_HAND : Hand.OFF_HAND;
        mc().interactionManager.interactBlock(mc().player, hand, new BlockHitResult(hit, Direction.UP, base, false));
        mc().player.swingHand(hand);
    }

    static void hit(EndCrystalEntity crystal) {
        mc().interactionManager.attackEntity(mc().player, crystal);
        mc().player.swingHand(Hand.MAIN_HAND);
    }

    /**
     * Rough vanilla explosion damage (power 6) before armor, from a crystal at {@code pos}.
     * Good enough to rank spots and keep self-damage in check.
     */
    static float damage(Vec3d pos, Entity target) {
        double dist = Math.sqrt(target.squaredDistanceTo(pos)) / 12.0;
        if (dist > 1) return 0;
        double exposure = ExplosionImpl.calculateReceivedDamage(pos, target);
        double impact = (1.0 - dist) * exposure;
        float dmg = (float) ((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
        if (target instanceof LivingEntity l) {
            // vanilla armor formula with toughness, then Resistance; enchantments are left out
            float armor = l.getArmor();
            float toughness = (float) l.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ARMOR_TOUGHNESS);
            float reduced = net.minecraft.util.math.MathHelper.clamp(armor - dmg / (2f + toughness / 4f), armor * 0.2f, 20f);
            dmg *= 1f - reduced / 25f;
            var resistance = l.getStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE);
            if (resistance != null) dmg *= Math.max(0f, 1f - (resistance.getAmplifier() + 1) * 0.2f);
        }
        return Math.max(0f, dmg);
    }

    static Vec3d crystalPos(BlockPos base) {
        return new Vec3d(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
    }
}
