package dev.loot.client.module.render;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.WorldUtil;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

public class StorageESP extends Module {
    private final BoolSetting chests = add(new BoolSetting("Chests", "Chests and trapped chests.", true));
    private final BoolSetting barrels = add(new BoolSetting("Barrels", "Barrels.", true));
    private final BoolSetting shulkers = add(new BoolSetting("Shulkers", "Placed shulker boxes.", true));
    private final BoolSetting echests = add(new BoolSetting("EnderChests", "Ender chests.", true));
    private final BoolSetting hoppers = add(new BoolSetting("Hoppers", "Hoppers.", false));
    private final BoolSetting furnaces = add(new BoolSetting("Furnaces", "Furnaces, smokers, blast furnaces.", false));
    private final BoolSetting dispensers = add(new BoolSetting("Dispensers", "Dispensers and droppers.", false));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", "Also draw lines to each container.", false));
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Box fill opacity.", 50, 0, 255, 5));

    private record Target(BlockPos pos, int color) {}

    private List<Target> targets = List.of();
    private int timer;

    public StorageESP() {
        super("Storage ESP", "Highlights containers in loaded chunks.", Category.RENDER);
    }

    @Override
    public void onTick() {
        // Block entity lists change rarely, so refresh a few times a second instead of every frame.
        if (timer-- > 0) return;
        timer = 5;
        List<Target> found = new ArrayList<>();
        for (WorldChunk chunk : WorldUtil.loadedChunks()) {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                int color = colorFor(be);
                if (color != 0) found.add(new Target(be.getPos(), color));
            }
        }
        targets = found;
    }

    private int colorFor(BlockEntity be) {
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.get() ? 0xFFE04CE0 : 0;
        if (be instanceof EnderChestBlockEntity) return echests.get() ? 0xFF7A3DFF : 0;
        if (be instanceof ChestBlockEntity) return chests.get() ? 0xFFFFB13B : 0;
        if (be instanceof BarrelBlockEntity) return barrels.get() ? 0xFFB5793A : 0;
        if (be instanceof HopperBlockEntity) return hoppers.get() ? 0xFF8A8A8A : 0;
        if (be instanceof AbstractFurnaceBlockEntity) return furnaces.get() ? 0xFF5E5E5E : 0;
        if (be instanceof DispenserBlockEntity) return dispensers.get() ? 0xFFA0A0A0 : 0;
        return 0;
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        for (Target t : targets) {
            Box box = new Box(t.pos).contract(0.06);
            r.box(box, t.color, fill.getInt());
            if (tracers.get()) r.line(r.crosshair(), box.getCenter(), t.color);
        }
    }

    @Override
    public String getInfo() {
        return String.valueOf(targets.size());
    }

    @Override
    public void onDisable() {
        targets = List.of();
    }
}
