package com.nikohalfkino.rubydung.audio.tts;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asynchronous Open JTalk wrapper.
 *
 * <p>Raw text is converted to katakana by {@link Katakana#toKana}, written to a temporary
 * file, and fed to the {@code open_jtalk} executable.  The WAV it writes is played back
 * through the Java audio system.  A single daemon thread services a bounded queue so the
 * game thread never blocks and speech is never silently dropped mid-queue.
 *
 * <h3>Sing mode</h3>
 * Sing mode is switched on <b>automatically for any line that contains a note</b>
 * ({@code bah<235,30>} or a valid pitch marker, see below); there is no toggle. Extreme
 * parameter presets are applied:
 * <ul>
 *   <li>Speed ≈ 0.45 – slow, sustained phonemes
 *   <li>Voiced threshold ≈ 0.10 – nearly every frame is voiced (a LOW threshold means more voiced),
 *       which is what lets the pitch shift be heard at all
 *   <li>GV weight for F0 = 0 – flat, steady pitch instead of a speech-like melody
 * </ul>
 * This produces the droning robotic timbre familiar from <em>Moonbase Alpha</em> text-to-speech.
 *
 * <h3>Inline pitch markers</h3>
 * The speaker parses {@code [N]} markers embedded in the text:
 * <ul>
 *   <li>{@code [+7]DO} — shift by +7 half-tones from base pitch
 *   <li>{@code [-3]word} — shift down 3 half-tones
 *   <li>{@code [G4]SOL} — use the note G4 as a semitone offset from C4
 * </ul>
 * Each marked segment is synthesised as a separate open_jtalk call and played on the same
 * audio line for minimal gaps, letting you compose simple melodies:
 * <pre>
 *   [0]DO [2]RE [4]MI [5]FA [7]SOL [9]LA [11]TI [12]DO
 * </pre>
 * Without markers the whole utterance uses {@link JTalkSettings#getSingNote()} as the offset.
 */
public final class JTalkSpeaker {

    /** How many pending utterances the queue holds before new ones are dropped silently. */
    private static final int QUEUE_CAPACITY = 6;

    /**
     * Matches inline pitch markers: {@code [+5]}, {@code [-3]}, {@code [0]},
     * {@code [C4]}, {@code [G#4]}, {@code [Bb3]}.
     */
    private static final Pattern PITCH_MARKER =
            Pattern.compile("\\[([+\\-]?\\d+(?:\\.\\d+)?|[A-Ga-g][#Bb]?\\d?)\\]");

    /**
     * A Moonbase Alpha singing note: {@code phoneme<duration-ms,pitch>}, e.g. {@code bah<235,30>}.
     * Notes are NOT individually bracketed: a whole phrase sits in one {@code [...]} group
     * ({@code [bah<235,30>bah<235,25>]}), and stray brackets/spaces between notes are ignored,
     * so the brackets are deliberately not part of the pattern.
     */
    private static final Pattern SINGING_NOTE =
            Pattern.compile("([^\\s\\[\\]<>,]+)<(\\d+),([^\\s\\[\\]<>,]+)>");

    /** Note letter → semitone offset from C within an octave. */
    private static final Map<Character, Integer> NOTE_BASE;
    static {
        Map<Character, Integer> m = new HashMap<>();
        m.put('C', 0); m.put('D', 2); m.put('E', 4); m.put('F', 5);
        m.put('G', 7); m.put('A', 9); m.put('B', 11);
        NOTE_BASE = Collections.unmodifiableMap(m);
    }

    // ── Internal segment type ─────────────────────────────────────────────────

    /** A run of kana text to be spoken at a specific pitch offset. */
    private static final class Segment {
        final String kana;
        /** Half-tones added on top of {@link JTalkSettings#getPitch()}. */
        final float pitchOffset;
        /** Whether the sing-mode presets (slow, fully voiced, flat pitch) apply to this segment. */
        final boolean sing;
        /** Requested playback duration in milliseconds, or -1 for an ordinary segment. */
        final int durationMs;

        Segment(String kana, float pitchOffset, boolean sing) {
            this(kana, pitchOffset, sing, -1);
        }

        Segment(String kana, float pitchOffset, boolean sing, int durationMs) {
            this.kana = kana;
            this.pitchOffset = pitchOffset;
            this.sing = sing;
            this.durationMs = durationMs;
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private final BlockingDeque<String> queue = new LinkedBlockingDeque<>(QUEUE_CAPACITY);
    private volatile boolean running;
    private boolean warnedNoVoice;
    private Thread thread;

    /** Bumped by {@link #clear()} so an utterance that is mid-playback knows to stop. */
    private final AtomicInteger generation = new AtomicInteger();

    /** The line currently playing, polled by {@link #getViseme()} (null when nothing is playing). */
    private volatile SourceDataLine activeLine;

    /** Mouth cues for the audio written to {@link #activeLine}, in that line's own timeline. */
    private final List<Visemes.Cue> cues = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Microseconds of audio written to the current line so far (worker thread only). */
    private long queuedMicros;

    /** Renders the notes of one utterance in parallel (each note is its own open_jtalk process). */
    private ExecutorService pool;

    /** Finished sing-mode renders keyed by voice + parameters + kana, so repeated notes cost nothing. */
    private static final int CACHE_ENTRIES = 96;
    private final Map<String, Pcm> cache = new LinkedHashMap<String, Pcm>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Pcm> eldest) {
            return size() > CACHE_ENTRIES;
        }
    };

    public synchronized void start() {
        if (this.thread != null) return;
        this.running = true;
        int workers = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
        this.pool = Executors.newFixedThreadPool(workers, r -> {
            Thread th = new Thread(r, "JTalk-render");
            th.setDaemon(true);
            return th;
        });
        this.thread = new Thread(this::loop, "JTalk");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public synchronized void stop() {
        this.running = false;
        if (this.pool != null) {
            this.pool.shutdownNow();
            this.pool = null;
        }
        if (this.thread != null) {
            this.thread.interrupt();
            this.thread = null;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Queues {@code text} to be spoken.  No-op if TTS is disabled, the text is blank,
     * or the queue is full.  Safe to call from any thread.
     */
    public void speak(String text) {
        if (!JTalkSettings.isEnabled() || text == null || text.isBlank()) return;
        this.queue.offer(text);   // non-blocking; drops if full
    }

    /** Discards all queued utterances and cuts off the one currently playing. */
    public void clear() {
        this.queue.clear();
        this.generation.incrementAndGet();
    }

    /**
     * The mouth shape for what is audible right now, or {@link Visemes.Viseme#CLOSED} when nothing
     * is playing. Reads the sound line's playback position, so it stays in sync with what you hear.
     * Safe to call from the game thread every frame.
     */
    public Visemes.Viseme getViseme() {
        SourceDataLine l = this.activeLine;
        if (l == null) return Visemes.Viseme.CLOSED;
        long pos = l.getMicrosecondPosition();
        for (Visemes.Cue c : this.cues) {
            if (pos >= c.startMicros && pos < c.endMicros) return c.viseme;
        }
        return Visemes.Viseme.CLOSED;
    }

    // ── Worker loop ───────────────────────────────────────────────────────────

    private void loop() {
        while (this.running) {
            try {
                String raw = this.queue.take();
                handleUtterance(raw);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("JTalkSpeaker: " + e);
            }
        }
    }

    private void handleUtterance(String raw) throws Exception {
        if (JTalkSettings.getVoicePath().isEmpty()) {
            if (!this.warnedNoVoice) {
                this.warnedNoVoice = true;
                System.err.println("JTalkSpeaker: no voicePath set in jtalk.txt (point it at a .htsvoice file).");
            }
            return;
        }
        List<Segment> segments = buildSegments(raw);
        if (segments.isEmpty()) return;

        final int gen = this.generation.get();
        final ExecutorService workers = this.pool;
        if (workers == null) return;

        // Start rendering EVERY note right away, in parallel. Playback below consumes them in
        // order, so by the time note 1 has finished playing, notes 2..n are already waiting.
        List<Future<Pcm>> jobs = new ArrayList<>();
        List<Segment> jobSegs = new ArrayList<>();   // jobSegs.get(i) is the segment of jobs.get(i)
        for (Segment seg : segments) {
            if (seg.kana.isBlank()) continue;
            jobSegs.add(seg);
            jobs.add(workers.submit((Callable<Pcm>) () -> render(seg)));
        }

        SourceDataLine line = null;
        boolean aborted = false;
        try {
            for (int j = 0; j < jobs.size(); j++) {
                Future<Pcm> job = jobs.get(j);
                if (!this.running || gen != this.generation.get()) { aborted = true; break; }

                Pcm pcm;
                try {
                    pcm = job.get();
                } catch (ExecutionException e) {
                    System.err.println("JTalkSpeaker: " + e.getCause());
                    continue;
                }
                if (pcm == null || pcm.data.length == 0) continue;

                AudioFormat fmt = pcm.format;
                if (line == null || !formatsMatch(line.getFormat(), fmt)) {
                    if (line != null) { line.drain(); line.close(); line = null; this.activeLine = null; }
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                    if (!AudioSystem.isLineSupported(info)) {
                        System.err.println("JTalkSpeaker: unsupported format: " + fmt);
                        continue;
                    }
                    line = (SourceDataLine) AudioSystem.getLine(info);
                    line.open(fmt);
                    line.start();
                    this.cues.clear();
                    this.queuedMicros = 0;
                    this.activeLine = line;
                }

                // Mouth cues for this segment, placed after everything already written to the line.
                this.cues.addAll(Visemes.build(jobSegs.get(j).kana, pcm.millis(), this.queuedMicros));
                this.queuedMicros += (long) (pcm.millis() * 1000);

                int frame = Math.max(1, fmt.getFrameSize());
                int offset = 0;
                while (offset < pcm.data.length) {
                    if (gen != this.generation.get()) { aborted = true; break; }
                    int count = Math.min(4096, pcm.data.length - offset);
                    count -= count % frame;
                    if (count <= 0) break;
                    line.write(pcm.data, offset, count);
                    offset += count;
                }
                if (aborted) break;
            }
        } finally {
            for (Future<Pcm> job : jobs) job.cancel(true);
            if (line != null) {
                if (aborted || !this.running) line.flush(); else line.drain();
                line.close();
            }
            this.activeLine = null;
            this.cues.clear();
        }
    }

    // ── Text segmentation ─────────────────────────────────────────────────────

    /**
     * Converts raw text into a list of kana segments, each with its own pitch offset.
     *
     * <p>Plain text without sing mode: one segment at offset 0.
     * <p>Sing mode (the text contains valid notes) but no markers: one segment at
     *    {@link JTalkSettings#getSingNote()}.
     * <p>Sing mode with markers: one segment per run of text between markers, each at the offset
     *    specified by the preceding marker.
     */
    private List<Segment> buildSegments(String raw) {
        // The duration/pitch note syntax always enables singing, regardless of the settings toggle.
        // Most importantly, only the phoneme (group 1) is passed through the kana converter;
        // the surrounding brackets and <duration,pitch> metadata are never spoken.
        Matcher notes = SINGING_NOTE.matcher(raw);
        List<Segment> noteSegments = new ArrayList<>();
        boolean foundNote = false;
        while (notes.find()) {
            foundNote = true;
            String phoneme = notes.group(1).trim();
            String kana = singingKana(phoneme);
            int durationMs;
            try {
                durationMs = Integer.parseInt(notes.group(2));
            } catch (NumberFormatException ex) {
                continue;
            }
            durationMs = Math.max(1, Math.min(30000, durationMs));
            float pitchOffset = parseSingingPitch(notes.group(3).trim());
            if (!kana.isBlank()) noteSegments.add(new Segment(kana, pitchOffset, true, durationMs));
        }
        if (foundNote) return noteSegments;

        boolean sing = containsSingingNotes(raw);
        if (!sing) {
            String kana = toKana(PITCH_MARKER.matcher(raw).replaceAll(" "));
            return kana.isBlank() ? Collections.emptyList()
                    : Collections.singletonList(new Segment(kana, 0.0f, false));
        }

        Matcher m = PITCH_MARKER.matcher(raw);
        if (!m.find()) {
            String kana = toKana(raw);
            return kana.isBlank() ? Collections.emptyList()
                    : Collections.singletonList(new Segment(kana, JTalkSettings.getSingNote(), true));
        }

        List<Segment> out = new ArrayList<>();
        int pos = 0;
        float currentOffset = JTalkSettings.getSingNote();
        m.reset();
        while (m.find()) {
            if (m.start() > pos) addSegment(raw, pos, m.start(), currentOffset, out);
            currentOffset = parseMarker(m.group(1));
            pos = m.end();
        }
        if (pos < raw.length()) addSegment(raw, pos, raw.length(), currentOffset, out);
        return out;
    }

    /**
     * Chat text as it is handed to open_jtalk: converted to katakana, or, when the "Transliterate
     * Voice" setting is off, passed through unchanged (for text that is already Japanese).
     */
    private static String toKana(String text) {
        return JTalkSettings.isTransliterate() ? Katakana.toKana(text) : text;
    }

    /** Same switch for the phoneme of a {@code [bah<235,30>]} note. */
    private static String singingKana(String phoneme) {
        return JTalkSettings.isTransliterate() ? Katakana.toSingingKana(phoneme) : phoneme;
    }

    /** Converts the numeric singing scale (25 = C4) or a note name into a C4-relative offset. */
    private static float parseSingingPitch(String value) {
        try {
            return Float.parseFloat(value) - 25.0f;
        } catch (NumberFormatException ignored) {
            return parseMarker(value);
        }
    }

    /**
     * True if the text has at least one marker that is a real note: a number within the pitch range
     * ({@code [0]}, {@code [+7]}, {@code [-3]}) or a note name with an octave ({@code [G4]},
     * {@code [Bb3]}). A bare letter like {@code [a]} is not enough to start singing.
     */
    private static boolean containsSingingNotes(String raw) {
        if (SINGING_NOTE.matcher(raw).find()) return true;
        Matcher m = PITCH_MARKER.matcher(raw);
        while (m.find()) {
            String g = m.group(1);
            char first = g.charAt(0);
            if (first == '+' || first == '-' || Character.isDigit(first)) {
                try {
                    if (Math.abs(Float.parseFloat(g)) <= JTalkSettings.MAX_PITCH) return true;
                } catch (NumberFormatException ignored) {
                }
            } else if (Character.isDigit(g.charAt(g.length() - 1))) {
                return true;   // letter, optional accidental, octave digit
            }
        }
        return false;
    }

    private static void addSegment(String raw, int from, int to, float offset, List<Segment> out) {
        String chunk = raw.substring(from, to).trim();
        if (chunk.isEmpty()) return;
        String kana = toKana(chunk);
        if (!kana.isBlank()) out.add(new Segment(kana, offset, true));
    }

    /**
     * Parses the inner text of a pitch marker.
     * <ul>
     *   <li>{@code "+5"} → 5.0
     *   <li>{@code "-3"} → -3.0
     *   <li>{@code "G4"} → semitones of G4 relative to C4 = 7
     *   <li>{@code "G#4"} → 8
     *   <li>{@code "Bb3"} → -2  (B♭ in octave 3)
     * </ul>
     */
    private static float parseMarker(String s) {
        // Numeric offset: "+5", "-3", "0", "7.5" …
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException ignored) {
        }

        // Note name: letter + optional accidental + optional octave digit.
        char letter = Character.toUpperCase(s.charAt(0));
        Integer base = NOTE_BASE.get(letter);
        if (base == null) return 0.0f;

        int idx = 1;
        int accidental = 0;
        if (idx < s.length()) {
            char a = s.charAt(idx);
            if (a == '#') { accidental =  1; idx++; }
            else if (a == 'b' || a == 'B') { accidental = -1; idx++; }
        }

        int octave = 4;
        if (idx < s.length() && Character.isDigit(s.charAt(idx))) {
            octave = s.charAt(idx) - '0';
        }

        // Semitone distance from C4.  C4 = 0, D4 = 2, G4 = 7, C5 = 12, G3 = -5 …
        return (octave - 4) * 12.0f + base + accidental;
    }

    // ── Synthesis ─────────────────────────────────────────────────────────────

    /** Decoded, silence-trimmed audio for one segment. */
    private static final class Pcm {
        final AudioFormat format;
        final byte[] data;

        Pcm(AudioFormat format, byte[] data) {
            this.format = format;
            this.data = data;
        }

        double millis() {
            int fs = Math.max(1, format.getFrameSize());
            return 1000.0 * (data.length / fs) / format.getFrameRate();
        }
    }

    /** Peak amplitude (of 32768) below which a frame counts as silence when trimming. */
    private static final int SILENCE_PEAK = 400;

    /**
     * Produces the audio for one segment. Plain segments are synthesised once. Notes with a
     * requested duration are lengthened by appending long-vowel marks (ー) in as few
     * open_jtalk runs as possible (usually one or two) and then fitted to the exact length.
     */
    private Pcm render(Segment seg) throws Exception {
        Pcm pcm = synthPcm(seg.kana, seg);
        if (pcm == null || seg.durationMs <= 0) return pcm;

        double target = seg.durationMs;
        // First guess for how long one mora lasts, refined after every pass.
        double perMora = Math.max(30.0, pcm.millis() / Math.max(1, seg.kana.length()));
        int extra = 0;

        for (int pass = 0; pass < 3 && pcm.millis() < target; pass++) {
            int add = (int) Math.ceil((target - pcm.millis()) * 1.1 / perMora);   // overshoot is free: it gets trimmed
            add = Math.max(1, Math.min(add, 60));
            extra += add;

            Pcm longer = synthPcm(seg.kana + "ー".repeat(extra), seg);
            if (longer == null || !formatsMatch(pcm.format, longer.format)) break;

            double grown = longer.millis() - pcm.millis();
            if (grown > 0) perMora = Math.max(30.0, grown / add);
            pcm = longer;
        }
        return fitToDuration(pcm, seg.durationMs);
    }

    /** Cuts (with a short fade-out) or pads with silence so the note is exactly {@code ms} long. */
    private static Pcm fitToDuration(Pcm in, int ms) {
        int fs = Math.max(1, in.format.getFrameSize());
        int frames = (int) Math.round(in.format.getFrameRate() * ms / 1000.0);
        byte[] out = new byte[frames * fs];
        System.arraycopy(in.data, 0, out, 0, Math.min(in.data.length, out.length));
        if (in.data.length > out.length) {
            fade(out, in.format, 0, (int) (in.format.getFrameRate() * 0.015));
        }
        return new Pcm(in.format, out);
    }

    /** Runs open_jtalk (or hits the cache) and returns trimmed PCM, or null on failure. */
    private Pcm synthPcm(String kana, Segment seg) throws IOException, InterruptedException,
            UnsupportedAudioFileException {
        String key = JTalkSettings.getVoicePath() + '|' + String.join(",", params(seg)) + '|' + kana;
        if (seg.sing) {
            synchronized (this.cache) {
                Pcm hit = this.cache.get(key);
                if (hit != null) return hit;
            }
        }

        File wav = Files.createTempFile("jtalk_", ".wav").toFile();
        try {
            if (!synthesise(kana, seg, wav)) return null;
            Pcm pcm;
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(wav)) {
                pcm = trimSilence(new Pcm(ais.getFormat(), ais.readAllBytes()));
            }
            if (seg.sing) {
                synchronized (this.cache) {
                    this.cache.put(key, pcm);
                }
            }
            return pcm;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            wav.delete();
        }
    }

    // ── Silence trimming / fades (16-bit little-endian PCM, which is what open_jtalk writes) ──

    private static boolean isPcm16(AudioFormat f) {
        return f.getEncoding() == AudioFormat.Encoding.PCM_SIGNED
                && f.getSampleSizeInBits() == 16 && !f.isBigEndian() && f.getFrameSize() > 0;
    }

    private static int framePeak(byte[] d, int frame, int frameSize) {
        int peak = 0;
        int base = frame * frameSize;
        for (int o = base; o + 1 < base + frameSize && o + 1 < d.length; o += 2) {
            int s = Math.abs((short) ((d[o + 1] << 8) | (d[o] & 0xFF)));
            if (s > peak) peak = s;
        }
        return peak;
    }

    /**
     * open_jtalk pads every utterance with leading/trailing silence, and sing mode's slow speed
     * stretches that padding about 2x. Played back-to-back, that padding IS the gap between notes.
     */
    private static Pcm trimSilence(Pcm in) {
        AudioFormat f = in.format;
        if (!isPcm16(f)) return in;
        int fs = f.getFrameSize();
        int frames = in.data.length / fs;

        int first = 0;
        while (first < frames && framePeak(in.data, first, fs) < SILENCE_PEAK) first++;
        if (first >= frames) return in;                       // all quiet: leave it alone
        int last = frames - 1;
        while (last > first && framePeak(in.data, last, fs) < SILENCE_PEAK) last--;

        int margin = (int) (f.getFrameRate() * 0.003);
        first = Math.max(0, first - margin);
        last = Math.min(frames - 1, last + margin);

        byte[] out = Arrays.copyOfRange(in.data, first * fs, (last + 1) * fs);
        fade(out, f, (int) (f.getFrameRate() * 0.004), (int) (f.getFrameRate() * 0.006));
        return new Pcm(f, out);
    }

    /** Linear fade-in / fade-out in place, so cuts do not click. */
    private static void fade(byte[] d, AudioFormat f, int inFrames, int outFrames) {
        if (!isPcm16(f)) return;
        int fs = f.getFrameSize();
        int ch = Math.max(1, f.getChannels());
        int frames = d.length / fs;
        for (int i = 0; i < frames; i++) {
            double g = 1.0;
            if (i < inFrames) g = Math.min(g, (double) i / inFrames);
            int fromEnd = frames - 1 - i;
            if (fromEnd < outFrames) g = Math.min(g, (double) fromEnd / outFrames);
            if (g >= 1.0) continue;
            for (int c = 0; c < ch; c++) {
                int o = i * fs + c * 2;
                short s = (short) ((d[o + 1] << 8) | (d[o] & 0xFF));
                s = (short) Math.round(s * g);
                d[o] = (byte) s;
                d[o + 1] = (byte) (s >> 8);
            }
        }
    }

    // ── Process ──

    /**
     * Runs open_jtalk on {@code kana} and writes the result to {@code out}.
     * Returns true if the process exited cleanly and wrote a non-empty file.
     */
    private boolean synthesise(String kana, Segment seg, File out) throws IOException, InterruptedException {
        File textFile = Files.createTempFile("jtalk_in_", ".txt").toFile();
        Process proc = null;
        try {
            try (PrintWriter w = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(textFile), StandardCharsets.UTF_8))) {
                w.println(kana);
            }

            ProcessBuilder pb = buildCommand(out, textFile, seg);
            pb.redirectErrorStream(true);

            proc = pb.start();
            try (InputStream drain = proc.getInputStream()) {
                drain.transferTo(OutputStream.nullOutputStream());
            }

            int exit = proc.waitFor();
            if (exit != 0) {
                System.err.println("JTalkSpeaker: open_jtalk exited " + exit + " for: " + kana);
                return false;
            }
            return out.length() > 0;
        } finally {
            if (proc != null && proc.isAlive()) proc.destroyForcibly();   // cancelled mid-synthesis
            //noinspection ResultOfMethodCallIgnored
            textFile.delete();
        }
    }

    /** speed, pitch, alpha, voiced, jf as the strings passed to open_jtalk. */
    private static String[] params(Segment seg) {
        float speed  = seg.sing ? JTalkSettings.SING_SPEED  : JTalkSettings.getSpeed();
        float voiced = seg.sing ? JTalkSettings.SING_VOICED : JTalkSettings.getVoiced();
        float jf     = seg.sing ? JTalkSettings.SING_JF     : JTalkSettings.getJf();
        float pitch  = JTalkSettings.getPitch() + seg.pitchOffset;
        float alpha  = JTalkSettings.getAlpha();
        return new String[]{fmt(speed), fmt(pitch), fmt(alpha), fmt(voiced), fmt(jf)};
    }

    private static ProcessBuilder buildCommand(File wavOut, File textIn, Segment seg) {
        String[] p = params(seg);
        ProcessBuilder pb = new ProcessBuilder(
                JTalkSettings.getExecPath(),
                "-x",  JTalkSettings.getDicPath(),
                "-m",  JTalkSettings.getVoicePath(),
                "-ow", wavOut.getAbsolutePath(),
                "-r",  p[0],
                "-fm", p[1],
                "-a",  p[2],
                "-u",  p[3],
                "-jf", p[4],
                textIn.getAbsolutePath()
        );
        pb.environment().put("LANG", "ja_JP.UTF-8");
        return pb;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Locale-safe float formatter (always uses '.', never ','). */
    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static boolean formatsMatch(AudioFormat a, AudioFormat b) {
        return a.getSampleRate()    == b.getSampleRate()
                && a.getSampleSizeInBits() == b.getSampleSizeInBits()
                && a.getChannels()      == b.getChannels()
                && a.isBigEndian()      == b.isBigEndian();
    }
}