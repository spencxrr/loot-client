package dev.loot.client;

import dev.loot.client.command.CommandManager;
import dev.loot.client.config.Config;
import dev.loot.client.config.FriendManager;
import dev.loot.client.module.Module;
import dev.loot.client.module.ModuleManager;
import dev.loot.client.module.misc.AutoReconnect;
import dev.loot.client.module.render.BlockESP;
import dev.loot.client.module.basefinding.SusChunkFinder;
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
    private static FriendManager friends;

    public static ModuleManager modules() {
        return modules;
    }

    public static FriendManager friends() {
        return friends;
    }

    @Override
    public void onInitializeClient() {
        Render3D.init();
        friends = new FriendManager();
        modules = new ModuleManager();
        Config.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            modules.onTick();
            modules.get(AutoReconnect.class).clientTick();
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !CommandManager.handle(message));

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> modules.fireChunkLoad(chunk));
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> modules.fireChunkUnload(chunk));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> modules.get(SusChunkFinder.class).clearHits());

        WorldRenderEvents.END_MAIN.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;
            Camera camera = mc.gameRenderer.getCamera();
            float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
            Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
            dev.loot.client.render.Projection.update(camera, tickDelta);
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

        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) ->
            !modules.get(dev.loot.client.module.render.BlockOutline.class).isEnabled());

        HudElementRegistry.addLast(Identifier.of(MOD_ID, "hud"), (ctx, tickCounter) -> {
            float tickDelta = tickCounter.getTickProgress(true);
            for (Module m : modules.getModules()) {
                if (!m.isEnabled()) continue;
                try {
                    m.onRenderHud(ctx, tickDelta);
                } catch (Exception e) {
                    LOGGER.error("{} hud failed", m.getName(), e);
                }
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Config.save());

        LOGGER.info("Loot Client {} loaded with {} modules", VERSION, modules.getModules().size());
    }
}
