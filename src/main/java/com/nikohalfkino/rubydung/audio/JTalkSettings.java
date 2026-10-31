package com.nikohalfkino.rubydung.audio;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Persistent settings for the Open JTalk TTS engine, saved to {@code jtalk.txt}.
 *
 * <p>Every float field maps 1-to-1 to an open_jtalk command-line flag:
 * <ul>
 *   <li>{@link #speed}  → {@code -r}  speech speed rate
 *   <li>{@link #pitch}  → {@code -fm} additional half-tones for F0
 *   <li>{@link #alpha}  → {@code -a}  mel-cepstrum all-pass constant (timbre)
 *   <li>{@link #voiced} → {@code -u}  voiced/unvoiced threshold: a frame is voiced only if its
 *       voicing probability is ABOVE this, so LOWER = more voiced, higher = more whispery/unvoiced
 *   <li>{@link #jf}     → {@code -jf} GV weight for F0 (higher = more dramatic pitch contour,
 *       0 = flat and steady)
 * </ul>
 *
 * <p><b>Sing mode</b> presets override speed/voiced/jf to produce the slow, droning,
 * fully-voiced, steady-pitched output that gives the Moonbase Alpha text-to-speech its character.
 * It is switched on by the toggle, or automatically for any chat line containing valid notes.
 * {@link #singNote} shifts the whole utterance up or down in half-tones on top of
 * the normal {@link #pitch}; inline {@code [N]} markers in the chat text can override
 * it word-by-word for melodies.
 */
public final class JTalkSettings {

    private static final String FILE_NAME = "jtalk.txt";

    // ── Bundled engine location ───────────────────────────────────────────────
    //
    // Layout under the JTalk home folder (packaged builds set -Djtalk.home=$APPDIR/jtalk):
    //   bin/open_jtalk[.exe]   (+ any DLLs it needs)
    //   open_jtalk_dic_utf_8/
    //   voice/**.htsvoice      (subfolders allowed: voice/mei/, voice/takumi/, ...)
    // Anything not found there falls back to PATH / empty, and jtalk.txt can override all three.

    private static final String HOME = System.getProperty("jtalk.home", "jtalk");
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private static final File VOICE_DIR = new File(HOME, "voice");

    private static final String DEFAULT_VOICE_NAME = "mei_normal";

    private static final String DEF_EXEC  = findExec();
    private static final String DEF_DIC   = findDic();
    private static final String DEF_VOICE = findVoice();

    private static String findExec() {
        File f = new File(HOME, "bin/open_jtalk" + (WINDOWS ? ".exe" : ""));
        return f.isFile() ? f.getAbsolutePath() : "open_jtalk";
    }

    private static String findDic() {
        File f = new File(HOME, "open_jtalk_dic_utf_8");
        return f.isDirectory() ? f.getAbsolutePath() : "open_jtalk_dic_utf_8";
    }

    /** Prefers mei_normal; otherwise the first voice found. Empty string if there are none. */
    private static String findVoice() {
        File[] voices = listVoices();
        for (File f : voices) {
            if (stem(f).equals(DEFAULT_VOICE_NAME)) return f.getAbsolutePath();
        }
        return voices.length > 0 ? voices[0].getAbsolutePath() : "";
    }

    // ── Voices ────────────────────────────────────────────────────────────────

    /** Folder that is searched for voices: {@code jtalk/voice} if it exists, else the jtalk home itself. */
    private static File voiceRoot() {
        return VOICE_DIR.isDirectory() ? VOICE_DIR : new File(HOME);
    }

    /** Every bundled {@code .htsvoice} (searching subfolders), sorted by path. */
    public static File[] listVoices() {
        File root = voiceRoot();
        if (!root.isDirectory()) return new File[0];
        try (Stream<Path> s = Files.walk(root.toPath())) {
            return s.map(Path::toFile)
                    .filter(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".htsvoice"))
                    .sorted()
                    .toArray(File[]::new);
        } catch (IOException | UncheckedIOException e) {
            return new File[0];
        }
    }

    /** File name without {@code .htsvoice}, lowercase: {@code Mei_Normal.htsvoice} -> {@code mei_normal}. */
    private static String stem(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".htsvoice") ? n.substring(0, n.length() - ".htsvoice".length()) : n;
    }

    /** Character part of a voice file: {@code mei_happy} -> {@code mei}. */
    private static String characterOf(File f) {
        String s = stem(f);
        int u = s.indexOf('_');
        return u > 0 ? s.substring(0, u) : s;
    }

    /** Mood part of a voice file: {@code mei_happy} -> {@code happy}; {@code "default"} if there is none. */
    private static String moodOf(File f) {
        String s = stem(f);
        int u = s.indexOf('_');
        return u > 0 && u < s.length() - 1 ? s.substring(u + 1) : "default";
    }

    /** Characters that have at least one bundled voice, sorted (e.g. mei, takumi). */
    public static List<String> listCharacters() {
        TreeSet<String> set = new TreeSet<>();
        for (File f : listVoices()) set.add(characterOf(f));
        return new ArrayList<>(set);
    }

    /** Moods available for a character, in file-name order (e.g. angry, bashful, happy, normal, sad). */
    public static List<String> listMoods(String character) {
        List<String> out = new ArrayList<>();
        for (File f : listVoices()) {
            if (characterOf(f).equals(character)) out.add(moodOf(f));
        }
        return out;
    }

    /** Character of the voice currently in use, or {@code "none"}. */
    public static String getVoiceCharacter() {
        return voicePath.isEmpty() ? "none" : characterOf(new File(voicePath));
    }

    /** Mood of the voice currently in use, or {@code "none"}. */
    public static String getVoiceMood() {
        return voicePath.isEmpty() ? "none" : moodOf(new File(voicePath));
    }

    /** Selects the voice for a character + mood, e.g. ("takumi", "sad"). Returns false if there is none. */
    public static boolean setVoice(String character, String mood) {
        for (File f : listVoices()) {
            if (characterOf(f).equals(character) && moodOf(f).equals(mood)) {
                voicePath = f.getAbsolutePath();
                return true;
            }
        }
        return false;
    }

    /** Switches to the next character, keeping the current mood if it has one, else "normal", else its first. */
    public static void nextCharacter() {
        List<String> chars = listCharacters();
        if (chars.isEmpty()) return;

        int i = chars.indexOf(getVoiceCharacter());
        String next = chars.get((i + 1) % chars.size());

        List<String> moods = listMoods(next);
        String mood = getVoiceMood();
        if (!moods.contains(mood)) mood = moods.contains("normal") ? "normal" : moods.get(0);
        setVoice(next, mood);
    }

    /**
     * Selects a bundled voice by name. Accepts the file name with or without {@code .htsvoice}
     * ({@code "takumi_normal"}). Returns false if no bundled voice matches.
     */
    public static boolean setVoiceByName(String name) {
        if (!notBlank(name)) return false;
        String want = name.strip().toLowerCase(Locale.ROOT);
        if (want.endsWith(".htsvoice")) want = want.substring(0, want.length() - ".htsvoice".length());

        for (File f : listVoices()) {
            if (stem(f).equals(want)) {
                voicePath = f.getAbsolutePath();
                return true;
            }
        }
        return false;
    }

    // ── Parameter bounds ──────────────────────────────────────────────────────

    public static final float MIN_SPEED  = 0.3f;
    public static final float MAX_SPEED  = 3.0f;
    public static final float DEF_SPEED  = 1.0f;

    public static final float MIN_PITCH  = -24.0f;
    public static final float MAX_PITCH  =  24.0f;
    public static final float DEF_PITCH  =   0.0f;

    public static final float MIN_ALPHA  = 0.0f;
    public static final float MAX_ALPHA  = 1.0f;
    public static final float DEF_ALPHA  = 0.55f;

    public static final float MIN_JF     = 0.0f;
    public static final float MAX_JF     = 2.0f;
    public static final float DEF_JF     = 1.0f;

    public static final float MIN_VOICED = 0.0f;
    public static final float MAX_VOICED = 1.0f;
    public static final float DEF_VOICED = 0.5f;

    /** Sing-mode presets – applied instead of speed/voiced/jf when sing mode is on. */
    public static final float SING_SPEED  = 0.45f;
    /** Low threshold = (almost) every frame is voiced, so the pitch shift can actually be heard. */
    public static final float SING_VOICED = 0.10f;
    /** No GV on F0 = a steady, flat pitch per note instead of a speech-like melody. */
    public static final float SING_JF     = 0.00f;

    // ── State ─────────────────────────────────────────────────────────────────

    private static volatile boolean enabled   = true;

    /** Whether chat text is converted to katakana ({@link Katakana}) before it is sent to the voice. */
    private static volatile boolean transliterate = true;

    /** Path to the {@code open_jtalk} binary; {@code "open_jtalk"} works if it is on PATH. */
    private static volatile String  execPath  = DEF_EXEC;

    /** Path to the UTF-8 MeCab dictionary directory bundled with Open JTalk. */
    private static volatile String  dicPath   = DEF_DIC;

    /** Path to the {@code .htsvoice} file (mei_normal by default). */
    private static volatile String  voicePath = DEF_VOICE;

    private static volatile float   speed     = DEF_SPEED;
    private static volatile float   pitch     = DEF_PITCH;
    private static volatile float   alpha     = DEF_ALPHA;
    private static volatile float   jf        = DEF_JF;
    private static volatile float   voiced    = DEF_VOICED;

    private static volatile boolean singMode  = false;

    /**
     * Base pitch offset (half-tones) used in sing mode when no inline marker overrides it.
     * Stacks on top of {@link #pitch}.
     */
    private static volatile float   singNote  = 0.0f;

    private JTalkSettings() {}

    // ── Accessors ─────────────────────────────────────────────────────────────

    public static boolean isEnabled()              { return enabled; }
    public static void    setEnabled(boolean v)    { enabled = v; }

    public static boolean isTransliterate()          { return transliterate; }
    public static void    setTransliterate(boolean v) { transliterate = v; }

    public static String  getExecPath()            { return execPath; }
    public static void    setExecPath(String v)    { if (notBlank(v)) execPath  = v.strip(); }

    public static String  getDicPath()             { return dicPath; }
    public static void    setDicPath(String v)     { if (notBlank(v)) dicPath   = v.strip(); }

    public static String  getVoicePath()           { return voicePath; }
    public static void    setVoicePath(String v)   { voicePath = notBlank(v) ? v.strip() : DEF_VOICE; }

    public static float   getSpeed()               { return speed; }
    public static void    setSpeed(float v)        { speed  = clamp(v, MIN_SPEED,  MAX_SPEED);  }

    public static float   getPitch()               { return pitch; }
    public static void    setPitch(float v)        { pitch  = clamp(v, MIN_PITCH,  MAX_PITCH);  }

    public static float   getAlpha()               { return alpha; }
    public static void    setAlpha(float v)        { alpha  = clamp(v, MIN_ALPHA,  MAX_ALPHA);  }

    public static float   getJf()                  { return jf; }
    public static void    setJf(float v)           { jf     = clamp(v, MIN_JF,     MAX_JF);     }

    public static float   getVoiced()              { return voiced; }
    public static void    setVoiced(float v)       { voiced = clamp(v, MIN_VOICED, MAX_VOICED); }

    public static boolean isSingMode()             { return singMode; }
    public static void    setSingMode(boolean v)   { singMode = v; }

    public static float   getSingNote()            { return singNote; }
    public static void    setSingNote(float v)     { singNote = clamp(v, MIN_PITCH, MAX_PITCH); }

    // ── Resets (used by the reset buttons next to each slider) ───────────────

    public static void resetSpeed()    { speed    = DEF_SPEED;  }
    public static void resetPitch()    { pitch    = DEF_PITCH;  }
    public static void resetAlpha()    { alpha    = DEF_ALPHA;  }
    public static void resetVoiced()   { voiced   = DEF_VOICED; }
    public static void resetJf()       { jf       = DEF_JF;     }
    public static void resetSingNote() { singNote = 0.0f;       }

    // ── Load / save ───────────────────────────────────────────────────────────

    public static void load() {
        File file = new File(FILE_NAME);
        if (!file.isFile()) {
            save();          // first run: write defaults so jtalk.txt exists to edit
            return;
        }

        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        enabled  = parseBoolean(p.getProperty("enabled"),  enabled);
        transliterate = parseBoolean(p.getProperty("transliterate"), transliterate);
        singMode = parseBoolean(p.getProperty("singMode"), singMode);

        setExecPath(p.getProperty("execPath",  execPath));
        setDicPath(p.getProperty("dicPath",    dicPath));

        // A bare file name ("takumi_normal.htsvoice") means "the bundled voice with that name";
        // anything with a path in it is used as-is. Unknown names keep the default voice.
        String v = p.getProperty("voicePath", "");
        if (notBlank(v) && !v.contains("/") && !v.contains("\\")) {
            voicePath = DEF_VOICE;
            setVoiceByName(v);
        } else {
            setVoicePath(v);
        }

        speed    = clamp(parseFloat(p.getProperty("speed"),    speed),    MIN_SPEED,  MAX_SPEED);
        pitch    = clamp(parseFloat(p.getProperty("pitch"),    pitch),    MIN_PITCH,  MAX_PITCH);
        alpha    = clamp(parseFloat(p.getProperty("alpha"),    alpha),    MIN_ALPHA,  MAX_ALPHA);
        jf       = clamp(parseFloat(p.getProperty("jf"),       jf),       MIN_JF,     MAX_JF);
        voiced   = clamp(parseFloat(p.getProperty("voiced"),   voiced),   MIN_VOICED, MAX_VOICED);
        singNote = clamp(parseFloat(p.getProperty("singNote"), singNote), MIN_PITCH,  MAX_PITCH);
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("enabled",   Boolean.toString(enabled));
        p.setProperty("transliterate", Boolean.toString(transliterate));
        // Blank = "use the bundled copy", so a moved/updated install never keeps a stale absolute path.
        p.setProperty("execPath",  execPath.equals(DEF_EXEC)   ? "" : execPath);
        p.setProperty("dicPath",   dicPath.equals(DEF_DIC)     ? "" : dicPath);
        p.setProperty("voicePath", voiceForSave());
        p.setProperty("speed",     Float.toString(speed));
        p.setProperty("pitch",     Float.toString(pitch));
        p.setProperty("alpha",     Float.toString(alpha));
        p.setProperty("jf",        Float.toString(jf));
        p.setProperty("voiced",    Float.toString(voiced));
        p.setProperty("singMode",  Boolean.toString(singMode));
        p.setProperty("singNote",  Float.toString(singNote));

        try (FileOutputStream out = new FileOutputStream(FILE_NAME)) {
            p.store(out, "Open JTalk TTS settings — see JTalkSettings.java");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Blank for the default voice, just the file name for a bundled one, the full path otherwise. */
    private static String voiceForSave() {
        if (voicePath.equals(DEF_VOICE)) return "";
        File f = new File(voicePath);
        boolean bundled = f.getAbsoluteFile().toPath().normalize()
                .startsWith(voiceRoot().getAbsoluteFile().toPath().normalize());
        return bundled ? f.getName() : voicePath;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static float parseFloat(String v, float fallback) {
        if (v == null) return fallback;
        try { return Float.parseFloat(v); } catch (NumberFormatException e) { return fallback; }
    }

    private static boolean parseBoolean(String v, boolean fallback) {
        if ("true".equalsIgnoreCase(v))  return true;
        if ("false".equalsIgnoreCase(v)) return false;
        return fallback;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}