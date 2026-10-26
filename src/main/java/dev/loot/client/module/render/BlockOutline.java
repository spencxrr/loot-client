package dev.loot.client.module.render;

import dev.loot.client.gui.Theme;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Swaps the thin black block outline for a tinted box in the client accent. */
public class BlockOutline extends Module {
    private final NumberSetting fill = add(new NumberSetting("FillAlpha", "Fill opacity.", 35, 0, 255, 5));
    private final NumberSetting expand = add(new NumberSetting("Expand", "Grow the box a little so it doesn't z-fight.", 0.002, 0, 0.05, 0.001));

    public BlockOutline() {
        super("Block Outline", "Custom accent-colored block selection box.", Category.RENDER);
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;
        var shape = state.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return;
        double e = expand.get();
        for (Box b : shape.getBoundingBoxes()) r.box(b.offset(pos).expand(e), Theme.ACCENT, fill.getInt());
    }
}
