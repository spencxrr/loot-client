package dev.loot.client;

import dev.loot.client.command.CommandManager;
import dev.loot.client.config.Config;
import dev.loot.client.module.Module;
import dev.loot.client.module.ModuleManager;
import dev.loot.client.module.render.BlockESP;
import dev.loot.client.module.world.BaseFinder;
import dev.loot.client.render.Render3D;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LootClient implements ClientModInitializer {
    public static final String MOD_ID = "lootclient";
    public static final Logger LOGGER = LoggerFactory.getLogger("Loot Client");
    public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
        .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");

    private static ModuleManager modules;

    public static ModuleManager modules() {
        return modules;
    }

    @Override
    public void onInitializeClient() {
        Render3D.init();
        modules = new ModuleManager();
        Config.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> modules.onTick());

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !CommandManager.handle(message));

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            modules.get(BlockESP.class).onChunkLoad(chunk);
            modules.get(BaseFinder.class).onChunkLoad(chunk);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> modules.get(BlockESP.class).onChunkUnload(chunk));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> modules.get(BaseFinder.class).clearHits());

        WorldRenderEvents.END_MAIN.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;
            Camera camera = mc.gameRenderer.getCamera();
            float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
            Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
            Render3D r = new Render3D(camera.getCameraPos(), context.matrices().peek().getPositionMatrix(), look);
            for (Module m : modules.getModules()) {
                if (!m.isEnabled()) continue;
                try {
                    m.onRender3D(r, tickDelta);
                } catch (Exception e) {
                    LOGGER.error("{} render failed", m.getName(), e);
                }
            }
            r.draw();
        });

        HudElementRegistry.addLast(Identifier.of(MOD_ID, "hud"), (ctx, tickCounter) -> {
            float tickDelta = tickCounter.getTickProgress(true);
            for (Module m : modules.getModules()) {
                if (m.isEnabled()) m.onRenderHud(ctx, tickDelta);
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Config.save());

        LOGGER.info("Loot Client {} loaded with {} modules", VERSION, modules.getModules().size());
    }
}
