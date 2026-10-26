package dev.loot.client.module.client;

import dev.loot.client.LootClient;
import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.module.render.MusicHud;
import dev.loot.client.setting.ModeSetting;
import dev.loot.client.setting.NumberSetting;
import dev.loot.client.setting.StringSetting;
import dev.loot.client.util.ChatUtil;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internet radio in game. Plays MP3 Icecast/Shoutcast streams (SomaFM presets, or any URL in
 * Custom) and pushes the current song title to Music HUD.
 */
public class Radio extends Module {
    private static final Map<String, String> STATIONS = Map.of(
        "GrooveSalad", "https://ice1.somafm.com/groovesalad-128-mp3",
        "Lush", "https://ice1.somafm.com/lush-128-mp3",
        "DeepSpace", "https://ice1.somafm.com/deepspaceone-128-mp3",
        "DefCon", "https://ice1.somafm.com/defcon-128-mp3",
        "BeatBlender", "https://ice1.somafm.com/beatblender-128-mp3",
        "Fluid", "https://ice1.somafm.com/fluid-128-mp3");

    private final ModeSetting station = add(new ModeSetting("Station", "SomaFM station, or Custom for your own URL.", "GrooveSalad",
        "GrooveSalad", "Lush", "DeepSpace", "DefCon", "BeatBlender", "Fluid", "Custom").onChange(this::restart));
    private final StringSetting customUrl = add(new StringSetting("CustomUrl", "MP3 stream URL used by the Custom station.", ""));
    private final NumberSetting volume = add(new NumberSetting("Volume", "Radio volume in percent.", 40, 0, 100, 1));

    private volatile Thread thread;
    private volatile InputStream current;

    public Radio() {
        super("Radio", "Listen to internet radio while you play.", Category.CLIENT);
    }

    @Override
    public void onEnable() {
        start();
    }

    @Override
    public void onDisable() {
        stop();
    }

    private void restart() {
        if (!isEnabled()) return;
        stop();
        start();
    }

    private void start() {
        String url = station.is("Custom") ? customUrl.get().trim() : STATIONS.get(station.get());
        if (url == null || url.isEmpty()) {
            if (inGame()) ChatUtil.error("Set a stream first: .set Radio CustomUrl <url>");
            return;
        }
        String name = station.is("Custom") ? "Radio" : station.get();
        Thread t = new Thread(() -> play(url, name), "Loot Radio");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    private void stop() {
        Thread t = thread;
        thread = null;
        InputStream in = current;
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
        if (t != null) t.interrupt();
        MusicHud.external = null;
    }

    private void play(String url, String name) {
        Thread me = Thread.currentThread();
        SourceDataLine line = null;
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestProperty("Icy-MetaData", "1");
            conn.setRequestProperty("User-Agent", "LootClient/" + LootClient.VERSION);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            int metaInt = conn.getHeaderFieldInt("icy-metaint", 0);
            InputStream in = new BufferedInputStream(conn.getInputStream(), 64 * 1024);
            if (metaInt > 0) in = new IcyStream(in, metaInt, title -> MusicHud.external = name + ": " + title);
            current = in;
            MusicHud.external = name;

            Bitstream bits = new Bitstream(in);
            Decoder decoder = new Decoder();
            byte[] pcm = new byte[0];
            while (thread == me) {
                Header h = bits.readFrame();
                if (h == null) break;
                SampleBuffer out = (SampleBuffer) decoder.decodeFrame(h, bits);
                if (line == null) {
                    AudioFormat fmt = new AudioFormat(decoder.getOutputFrequency(), 16, decoder.getOutputChannels(), true, false);
                    line = AudioSystem.getSourceDataLine(fmt);
                    line.open(fmt, 32 * 1024);
                    line.start();
                }
                short[] samples = out.getBuffer();
                int n = out.getBufferLength();
                if (pcm.length < n * 2) pcm = new byte[n * 2];
                float gain = volume.getFloat() / 100f;
                gain *= gain; // perceptual curve
                for (int i = 0; i < n; i++) {
                    int s = (int) (samples[i] * gain);
                    pcm[i * 2] = (byte) s;
                    pcm[i * 2 + 1] = (byte) (s >> 8);
                }
                line.write(pcm, 0, n * 2);
                bits.closeFrame();
            }
        } catch (Exception e) {
            if (thread == me) {
                LootClient.LOGGER.warn("Radio stopped", e);
                net.minecraft.client.MinecraftClient.getInstance().execute(() -> ChatUtil.error("Radio stream failed: " + e.getMessage()));
            }
        } finally {
            if (line != null) {
                line.stop();
                line.close();
            }
            if (thread == me) MusicHud.external = null;
        }
    }

    /** Strips Icecast metadata blocks out of the audio and reports StreamTitle changes. */
    private static final class IcyStream extends FilterInputStream {
        private static final Pattern TITLE = Pattern.compile("StreamTitle='(.*?)';");
        private final int metaInt;
        private final java.util.function.Consumer<String> onTitle;
        private int untilMeta;

        IcyStream(InputStream in, int metaInt, java.util.function.Consumer<String> onTitle) {
            super(in);
            this.metaInt = metaInt;
            this.untilMeta = metaInt;
            this.onTitle = onTitle;
        }

        private void readMeta() throws IOException {
            int len = in.read() * 16;
            if (len < 0) throw new IOException("end of stream");
            if (len > 0) {
                byte[] meta = in.readNBytes(len);
                Matcher m = TITLE.matcher(new String(meta, StandardCharsets.UTF_8));
                if (m.find() && !m.group(1).isBlank()) onTitle.accept(m.group(1));
            }
            untilMeta = metaInt;
        }

        @Override
        public int read() throws IOException {
            if (untilMeta == 0) readMeta();
            int b = in.read();
            if (b >= 0) untilMeta--;
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (untilMeta == 0) readMeta();
            int n = in.read(buf, off, Math.min(len, untilMeta));
            if (n > 0) untilMeta -= n;
            return n;
        }
    }
}
