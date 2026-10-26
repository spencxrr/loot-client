package dev.loot.client.module.client;

import com.google.gson.JsonObject;
import dev.loot.client.LootClient;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.BoolSetting;
import dev.loot.client.setting.StringSetting;
import dev.loot.client.util.ChatUtil;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Discord rich presence over Discord's local IPC pipe, no library needed. Discord shows the
 * name of whatever application ID you give it, so make an app called "Loot Client" at
 * discord.com/developers/applications and paste its Application ID into ClientId.
 */
public class DiscordPresence extends Module {
    private final StringSetting clientId = add(new StringSetting("ClientId", "Application ID from discord.com/developers.", ""));
    private final BoolSetting showServer = add(new BoolSetting("ShowServer", "Show the server address.", false));
    private final StringSetting image = add(new StringSetting("Image", "Art asset key uploaded to your Discord app (optional).", ""));

    private volatile boolean running;
    private Thread thread;
    private final long started = System.currentTimeMillis() / 1000;

    public DiscordPresence() {
        super("Discord Presence", "Shows Loot Client as your Discord status.", Category.CLIENT);
    }

    @Override
    public void onEnable() {
        if (clientId.get().isBlank()) {
            if (inGame()) ChatUtil.error("Set your Discord app ID first: .set DiscordPresence ClientId <id>");
            setEnabled(false);
            return;
        }
        running = true;
        thread = new Thread(this::loop, "Loot Discord RPC");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void onDisable() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        while (running) {
            try (Pipe pipe = Pipe.open()) {
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", clientId.get().trim());
                pipe.write(0, hello.toString());
                pipe.read();
                while (running) {
                    pipe.write(1, activity().toString());
                    pipe.read();
                    Thread.sleep(15_000);
                }
                clear(pipe);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                // Discord not running, or closed: try again in a bit.
                try {
                    Thread.sleep(20_000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private void clear(Pipe pipe) {
        try {
            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            JsonObject cmd = new JsonObject();
            cmd.addProperty("cmd", "SET_ACTIVITY");
            cmd.add("args", args);
            cmd.addProperty("nonce", UUID.randomUUID().toString());
            pipe.write(1, cmd.toString());
        } catch (IOException ignored) {
        }
    }

    private JsonObject activity() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        String details;
        if (mc.world == null) details = "In the menus";
        else if (mc.isInSingleplayer()) details = "Singleplayer";
        else if (showServer.get() && mc.getCurrentServerEntry() != null) details = "Playing " + mc.getCurrentServerEntry().address;
        else details = "Multiplayer";
        long on = LootClient.modules().getModules().stream().filter(Module::isEnabled).count();

        JsonObject act = new JsonObject();
        act.addProperty("details", details);
        act.addProperty("state", on + " modules on");
        JsonObject ts = new JsonObject();
        ts.addProperty("start", started);
        act.add("timestamps", ts);
        JsonObject assets = new JsonObject();
        if (!image.get().isBlank()) assets.addProperty("large_image", image.get().trim());
        assets.addProperty("large_text", "Loot Client " + LootClient.VERSION);
        act.add("assets", assets);

        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", act);
        JsonObject cmd = new JsonObject();
        cmd.addProperty("cmd", "SET_ACTIVITY");
        cmd.add("args", args);
        cmd.addProperty("nonce", UUID.randomUUID().toString());
        return cmd;
    }

    /** Discord's IPC transport: a named pipe on Windows, a unix socket elsewhere. */
    private interface Pipe extends Closeable {
        void writeRaw(ByteBuffer buf) throws IOException;

        void readRaw(ByteBuffer buf) throws IOException;

        default void write(int op, String json) throws IOException {
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(op).putInt(data.length).put(data).flip();
            writeRaw(buf);
        }

        default void read() throws IOException {
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            readRaw(header);
            header.flip();
            header.getInt();
            int len = header.getInt();
            if (len < 0 || len > 1 << 20) throw new IOException("bad frame");
            readRaw(ByteBuffer.allocate(len));
        }

        static Pipe open() throws IOException {
            boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
            for (int i = 0; i < 10; i++) {
                try {
                    if (windows) return new WindowsPipe(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"));
                    for (Path dir : unixDirs()) {
                        Path sock = dir.resolve("discord-ipc-" + i);
                        if (!Files.exists(sock)) continue;
                        SocketChannel ch = SocketChannel.open(UnixDomainSocketAddress.of(sock));
                        return new UnixPipe(ch);
                    }
                } catch (IOException ignored) {
                }
            }
            throw new IOException("Discord isn't running");
        }

        private static Path[] unixDirs() {
            String base = System.getenv("XDG_RUNTIME_DIR");
            if (base == null) base = System.getenv("TMPDIR");
            if (base == null) base = "/tmp";
            Path b = Path.of(base);
            return new Path[]{b, b.resolve("app/com.discordapp.Discord"), b.resolve("snap.discord"), Path.of("/tmp")};
        }
    }

    private record WindowsPipe(RandomAccessFile file) implements Pipe {
        public void writeRaw(ByteBuffer buf) throws IOException {
            file.write(buf.array(), buf.position(), buf.remaining());
        }

        public void readRaw(ByteBuffer buf) throws IOException {
            file.readFully(buf.array(), 0, buf.capacity());
            buf.position(buf.capacity());
        }

        public void close() throws IOException {
            file.close();
        }
    }

    private record UnixPipe(SocketChannel channel) implements Pipe {
        public void writeRaw(ByteBuffer buf) throws IOException {
            while (buf.hasRemaining()) channel.write(buf);
        }

        public void readRaw(ByteBuffer buf) throws IOException {
            while (buf.hasRemaining()) if (channel.read(buf) < 0) throw new IOException("closed");
        }

        public void close() throws IOException {
            channel.close();
        }
    }
}
