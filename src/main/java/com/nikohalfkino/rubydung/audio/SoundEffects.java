package com.nikohalfkino.rubydung.audio;

import com.nikohalfkino.rubydung.util.GameSettings;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * fire-and-forget sound effects from /assets/rubydung/sfx/
 *
 * "door_open" is sfx/tiles/door_open, "ui/menu_open" is relative to sfx/
 *
 * each file is decoded once and kept as pcm, everything plays on one daemon thread
 */
public final class SoundEffects {

    private static final String PATH = "/assets/rubydung/sfx/";
    private static final String DEFAULT_FOLDER = "tiles/";

    // same sound closer together than this is dropped (breaking 30 blocks in one tick)
    private static final long MIN_GAP_MS = 45L;

    // how far playRandom shifts the pitch either way
    public static final float DEFAULT_PITCH_VARIATION = 0.12F;

    private static final Sample MISSING = new Sample(null, 0, 0);

    private static final Map<String, Sample> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_PLAYED = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SoundEffects");
        thread.setDaemon(true);
        return thread;
    });

    private SoundEffects() {}

    // the SFX slider in options, 1.0 = as recorded
    public static void setMasterVolume(float volume) {
        GameSettings.setSfxVolume(volume);
    }

    public static float getMasterVolume() {
        return GameSettings.getSfxVolume();
    }

    public static void play(String name) {
        play(name, 1.0F, 1.0F);
    }

    public static void play(String name, float volume) {
        play(name, volume, 1.0F);
    }

    /** pitch 1.0 = as recorded, higher = higher and shorter */
    public static void play(String name, float volume, float pitch) {
        enqueue(name, name, volume, pitch);
    }

    /** picks prefix_1 .. prefix_variants at random, with a slight pitch change */
    public static void playRandom(String prefix, int variants) {
        playRandom(prefix, variants, DEFAULT_PITCH_VARIATION);
    }

    public static void playRandom(String prefix, int variants, float pitchVariation) {
        float pitch = 1.0F + (RANDOM.nextFloat() * 2.0F - 1.0F) * pitchVariation;
        // The gap applies to the whole group, whichever variant was picked.
        enqueue(prefix, prefix + "_" + (1 + RANDOM.nextInt(variants)), 1.0F, pitch);
    }

    /** like playRandom, for a single file */
    public static void playVaried(String name) {
        float pitch = 1.0F + (RANDOM.nextFloat() * 2.0F - 1.0F) * DEFAULT_PITCH_VARIATION;
        enqueue(name, name, 1.0F, pitch);
    }

    /** decodes ahead of time so the first play has no delay */
    public static void preload(String... names) {
        EXECUTOR.execute(() -> {
            for (String name : names) {
                load(name);
            }
        });
    }

    private static void enqueue(String gapKey, String name, float volume, float pitch) {
        long now = System.currentTimeMillis();
        Long last = LAST_PLAYED.put(gapKey, now);
        if (last != null && now - last < MIN_GAP_MS) {
            return;
        }
        EXECUTOR.execute(() -> playNow(name, volume, pitch));
    }

    private static void playNow(String name, float volume, float pitch) {
        Sample sample = load(name);
        if (sample == MISSING) {
            return;
        }

        try {
            byte[] pcm = Math.abs(pitch - 1.0F) < 0.001F ? sample.pcm : resample(sample.pcm, sample.channels, pitch);
            AudioFormat format = new AudioFormat(sample.sampleRate, 16, sample.channels, true, false);
            Clip clip = AudioSystem.getClip();
            clip.open(format, pcm, 0, pcm.length);

            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float linear = Math.max(0.0001F, getMasterVolume() * volume);
                float decibels = (float) (20.0 * Math.log10(linear));
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
            }

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        } catch (Exception e) {
            System.err.println("SoundEffects: could not play " + name + ": " + e);
        }
    }

    // changes pitch by playing samples faster/slower, so higher = shorter
    private static byte[] resample(byte[] pcm, int channels, double pitch) {
        int frames = pcm.length / (2 * channels);
        int outFrames = Math.max(1, (int) (frames / pitch));
        byte[] out = new byte[outFrames * channels * 2];

        for (int i = 0; i < outFrames; i++) {
            double position = i * pitch;
            int first = Math.min(frames - 1, (int) position);
            int second = Math.min(frames - 1, first + 1);
            double fraction = position - first;

            for (int c = 0; c < channels; c++) {
                int a = sampleAt(pcm, first * channels + c);
                int b = sampleAt(pcm, second * channels + c);
                int value = (int) Math.round(a + (b - a) * fraction);
                int at = (i * channels + c) * 2;
                out[at] = (byte) value;
                out[at + 1] = (byte) (value >> 8);
            }
        }
        return out;
    }

    private static int sampleAt(byte[] pcm, int index) {
        return (short) ((pcm[index * 2] & 0xFF) | (pcm[index * 2 + 1] << 8));
    }

    private static Sample load(String name) {
        Sample cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }

        Sample sample = decode(name);
        CACHE.put(name, sample);
        return sample;
    }

    private static Sample decode(String name) {
        String resource = PATH + (name.indexOf('/') >= 0 ? "" : DEFAULT_FOLDER) + name + ".ogg";
        InputStream in = SoundEffects.class.getResourceAsStream(resource);
        if (in == null) {
            System.err.println("SoundEffects: missing resource " + resource);
            return MISSING;
        }

        MusicPlayer.Decoder decoder = null;
        try {
            decoder = new MusicPlayer.Decoder(in);
            int channels = decoder.channels;
            float[] chunk = new float[4096 * channels];
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            int frames;
            while ((frames = decoder.read(chunk, 4096)) > 0) {
                for (int i = 0; i < frames * channels; i++) {
                    int value = Math.max(-32768, Math.min(32767, (int) (chunk[i] * 32767.0F)));
                    bytes.write(value & 0xFF);
                    bytes.write((value >> 8) & 0xFF);
                }
            }
            return new Sample(bytes.toByteArray(), decoder.sampleRate, channels);
        } catch (Exception e) {
            System.err.println("SoundEffects: failed to decode " + resource + ": " + e);
            return MISSING;
        } finally {
            if (decoder != null) {
                decoder.close();
            }
        }
    }

    private static final class Sample {
        final byte[] pcm;
        final int sampleRate;
        final int channels;

        Sample(byte[] pcm, int sampleRate, int channels) {
            this.pcm = pcm;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }
}
