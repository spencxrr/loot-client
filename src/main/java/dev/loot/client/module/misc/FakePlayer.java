package dev.loot.client.module.misc;

import com.mojang.authlib.GameProfile;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.StringSetting;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;

import java.util.UUID;

/** Spawns a client-side copy of you to test crystal/anchor damage and ESP on. Only you can see it. */
public class FakePlayer extends Module {
    private final StringSetting name = add(new StringSetting("Name", "Name shown above the fake player.", "LootDummy"));
    private final BoolSetting copyGear = add(new BoolSetting("CopyGear", "Give it your armor and held items.", true));

    private OtherClientPlayerEntity fake;

    public FakePlayer() {
        super("FakePlayer", "Spawns a client-side dummy player for testing.", Category.MISC);
    }

    @Override
    public void onEnable() {
        if (!inGame()) {
            setEnabled(false);
            return;
        }
        fake = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), name.get()));
        fake.copyPositionAndRotation(mc.player);
        fake.setHeadYaw(mc.player.getHeadYaw());
        if (copyGear.get()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                fake.equipStack(slot, mc.player.getEquippedStack(slot).copy());
            }
        }
        fake.setHealth(mc.player.getHealth());
        mc.world.addEntity(fake);
    }

    @Override
    public void onDisable() {
        if (fake != null && mc.world != null) mc.world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
        fake = null;
    }

    @Override
    public void onTick() {
        // Dies with the world on disconnect/dimension change.
        if (fake != null && fake.getEntityWorld() != mc.world) setEnabled(false);
    }
}
