package dev.loot.client.module.basefinding;

import dev.loot.client.gui.Theme;
import dev.loot.client.gui.UI;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.render.Render3D;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.util.WorldUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists every block entity type in loaded chunks with counts, and optionally boxes them. Useful
 * for spotting what a server actually sent you (and stashes the other finders didn't flag).
 */
public class BlockEntityDebug extends Module {
    private final BoolSetting boxes = add(new BoolSetting("Boxes", "Draw a box on every block entity.", true));
    private final NumberSetting range = add(new NumberSetting("Range", "Max render distance.", 128, 8, 512, 8));
    private final NumberSetting rows = add(new NumberSetting("Rows", "Max types listed on the HUD.", 12, 3, 40, 1));

    private final List<BlockEntity> found = new ArrayList<>();
    private List<Map.Entry<String, Integer>> counts = List.of();
    private int timer;

    public BlockEntityDebug() {
        super("Block Entity Debug", "Counts and shows every block entity around you.", Category.BASEFINDING);
    }

    @Override
    public void onTick() {
        if (timer-- > 0) return;
        timer = 20;
        found.clear();
        Map<String, Integer> map = new HashMap<>();
        for (WorldChunk chunk : WorldUtil.loadedChunks()) {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                found.add(be);
                var id = Registries.BLOCK_ENTITY_TYPE.getId(be.getType());
                map.merge(id == null ? "?" : id.getPath(), 1, Integer::sum);
            }
        }
        counts = map.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).toList();
    }

    @Override
    public void onRender3D(Render3D r, float tickDelta) {
        if (!boxes.get()) return;
        double maxSq = range.get() * range.get();
        for (BlockEntity be : found) {
            if (be.getPos().getSquaredDistance(mc.player.getEntityPos()) > maxSq) continue;
            int rgb = be.getCachedState().getBlock().getDefaultMapColor().color;
            r.box(new Box(be.getPos()), 0xFF000000 | (rgb == 0 ? 0xA3AEFF : rgb), 30);
        }
    }

    @Override
    public void onRenderHud(DrawContext ctx, float tickDelta) {
        if (mc.options.hudHidden) return;
        int n = Math.min(rows.getInt(), counts.size());
        int w = 110, x = 4, y = 26;
        UI.roundRect(ctx, x, y, x + w, y + 16 + n * 10, 5, Theme.SURFACE);
        UI.drawBold(ctx, "Block entities", x + 6, y + 4, Theme.TEXT);
        UI.drawTextRight(ctx, String.valueOf(found.size()), x + w - 6, y + 4, Theme.ACCENT);
        for (int i = 0; i < n; i++) {
            var e = counts.get(i);
            UI.drawText(ctx, e.getKey(), x + 6, y + 15 + i * 10, Theme.TEXT_MUTED);
            UI.drawTextRight(ctx, String.valueOf(e.getValue()), x + w - 6, y + 15 + i * 10, Theme.TEXT);
        }
    }

    @Override
    public String getInfo() {
        return String.valueOf(found.size());
    }
}
