package com.nikohalfkino.rubydung.audio;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything Katakana reads from disk: kana_tables.txt (required) and dictionary.txt (plus the
 * optional jtalk/dictionary.txt override). Loaded once, when the class is first used.
 */
final class KanaData {

    private static final String RESOURCE_DIR = "/assets/rubydung/tts/";
    private static final String TABLES_RESOURCE = RESOURCE_DIR + "kana_tables.txt";
    private static final String DICTIONARY_RESOURCE = RESOURCE_DIR + "dictionary.txt";

    /** Consonant keys the converter looks up; every one must be present in [rows]. */
    private static final String[] ROW_KEYS = {
            "-", "k", "g", "s", "z", "t", "d", "n", "h", "f", "b", "p", "m", "y", "r", "w", "v",
            "j", "sh", "ch", "q"
    };

    /** Entries the converter reads from [misc]; every one must be present. */
    private static final String[] MISC_KEYS = {
            "hundred", "point", "oh", "minus", "number", "hash", "dollar", "plural",
            "ordinal_suffix", "ordinal_suffix_ty", "ordinal_ty_ending"
    };

    // kana_tables.txt
    static final Map<String, String[]> ROWS = new HashMap<>();
    static final Set<String> VOICED_TH = new HashSet<>();
    static final String[] LETTERS = new String[26];
    static final String[] ONES = new String[20];
    static final String[] TENS = new String[10];
    static final String[] SCALES = new String[5];
    static final Map<String, String> ORDINALS = new HashMap<>();
    static final Map<Integer, String> SYMBOLS = new HashMap<>();
    static final Map<String, String> MISC = new HashMap<>();

    // dictionary.txt
    static final Map<String, String> DICT = new HashMap<>();
    static final Set<String> SPELLED = new HashSet<>();
    static final Set<String> SHORT_WORDS = new HashSet<>();

    static {
        loadTables();
        loadDictionary();
    }

    // ------------------------------------------------------------------ kana_tables.txt

    private static void loadTables() {
        try (InputStream in = KanaData.class.getResourceAsStream(TABLES_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("KanaData: required resource " + TABLES_RESOURCE + " not found");
            }
            readTables(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException("KanaData: could not read " + TABLES_RESOURCE, e);
        }
        validateTables();
    }

    private static void readTables(BufferedReader reader) throws IOException {
        String section = "";
        String line;
        int lineNo = 0;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            line = line.strip();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            if (line.charAt(0) == '[' && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
                continue;
            }
            switch (section) {
                case "rows": {
                    String[] kv = splitPair(line, lineNo);
                    String[] kana = kv[1].split("\\s+");
                    if (kana.length != 5) {
                        throw tableError(lineNo, "a row needs exactly 5 kana (a i u e o)");
                    }
                    ROWS.put(kv[0].equals("-") ? "" : kv[0], kana);
                    break;
                }
                case "voiced_th":
                    addWords(VOICED_TH, line);
                    break;
                case "letters": {
                    String[] kv = splitPair(line, lineNo);
                    char c = kv[0].length() == 1 ? Character.toLowerCase(kv[0].charAt(0)) : '?';
                    if (c < 'a' || c > 'z') {
                        throw tableError(lineNo, "letter name must be keyed by a single letter a-z");
                    }
                    LETTERS[c - 'a'] = kv[1];
                    break;
                }
                case "ones":
                    putIndexed(ONES, splitPair(line, lineNo), 0, lineNo);
                    break;
                case "tens":
                    putIndexed(TENS, splitPair(line, lineNo), 2, lineNo);
                    break;
                case "scales":
                    putIndexed(SCALES, splitPair(line, lineNo), 1, lineNo);
                    break;
                case "ordinals": {
                    String[] kv = splitPair(line, lineNo);
                    ORDINALS.put(kv[0], kv[1]);
                    break;
                }
                case "symbols": {
                    String[] kv = line.split("\\s+", 2);
                    if (kv.length != 2 || kv[0].codePointCount(0, kv[0].length()) != 1) {
                        throw tableError(lineNo, "expected: <single symbol> <reading>");
                    }
                    SYMBOLS.put(kv[0].codePointAt(0), kv[1].strip());
                    break;
                }
                case "misc": {
                    String[] kv = splitPair(line, lineNo);
                    MISC.put(kv[0].toLowerCase(Locale.ROOT), kv[1]);
                    break;
                }
                default:
                    System.err.println("KanaData: " + TABLES_RESOURCE + " line " + lineNo
                            + ": ignoring entry in unknown section [" + section + "]");
                    break;
            }
        }
    }

    private static String[] splitPair(String line, int lineNo) {
        int eq = line.indexOf('=');
        if (eq <= 0) {
            throw tableError(lineNo, "expected 'key = value'");
        }
        String key = line.substring(0, eq).strip();
        String value = line.substring(eq + 1).strip();
        if (key.isEmpty() || value.isEmpty()) {
            throw tableError(lineNo, "expected 'key = value'");
        }
        return new String[]{key, value};
    }

    private static void putIndexed(String[] target, String[] kv, int min, int lineNo) {
        int idx;
        try {
            idx = Integer.parseInt(kv[0]);
        } catch (NumberFormatException e) {
            throw tableError(lineNo, "key must be a number");
        }
        if (idx < min || idx >= target.length) {
            throw tableError(lineNo, "key must be between " + min + " and " + (target.length - 1));
        }
        target[idx] = kv[1];
    }

    private static IllegalStateException tableError(int lineNo, String message) {
        return new IllegalStateException("KanaData: " + TABLES_RESOURCE + " line " + lineNo + ": " + message);
    }

    private static void validateTables() {
        for (String key : ROW_KEYS) {
            if (!ROWS.containsKey(key.equals("-") ? "" : key)) {
                throw missing("[rows] entry '" + key + "'");
            }
        }
        for (int i = 0; i < LETTERS.length; i++) {
            if (LETTERS[i] == null) {
                throw missing("[letters] entry '" + (char) ('a' + i) + "'");
            }
        }
        for (int i = 0; i < ONES.length; i++) {
            if (ONES[i] == null) {
                throw missing("[ones] entry " + i);
            }
        }
        for (int i = 2; i < TENS.length; i++) {
            if (TENS[i] == null) {
                throw missing("[tens] entry " + i);
            }
        }
        for (int i = 1; i < SCALES.length; i++) {
            if (SCALES[i] == null) {
                throw missing("[scales] entry " + i);
            }
        }
        for (String key : MISC_KEYS) {
            if (!MISC.containsKey(key)) {
                throw missing("[misc] entry '" + key + "'");
            }
        }
        TENS[0] = TENS[1] = SCALES[0] = "";
    }

    private static IllegalStateException missing(String what) {
        return new IllegalStateException("KanaData: " + TABLES_RESOURCE + " is missing " + what);
    }

    static String misc(String key) {
        return MISC.get(key);
    }

    // ------------------------------------------------------------------ dictionary.txt

    private static File externalDictionary() {
        return new File(System.getProperty("jtalk.home", "jtalk"), "dictionary.txt");
    }

    private static void loadDictionary() {
        try (InputStream in = KanaData.class.getResourceAsStream(DICTIONARY_RESOURCE)) {
            if (in == null) {
                System.err.println("KanaData: " + DICTIONARY_RESOURCE + " not found; using letter rules only.");
            } else {
                readDictionary(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            System.err.println("KanaData: could not read " + DICTIONARY_RESOURCE + ": " + e);
        }

        File external = externalDictionary();
        if (external.isFile()) {
            try (BufferedReader reader = Files.newBufferedReader(external.toPath(), StandardCharsets.UTF_8)) {
                readDictionary(reader);
            } catch (IOException e) {
                System.err.println("KanaData: could not read " + external + ": " + e);
            }
        }
    }

    private static void readDictionary(BufferedReader reader) throws IOException {
        String section = "words";
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.strip();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            if (line.charAt(0) == '[' && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
                continue;
            }
            switch (section) {
                case "words": {
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        break;
                    }
                    String word = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
                    String kana = line.substring(eq + 1).strip();
                    if (!word.isEmpty() && !kana.isEmpty()) {
                        DICT.put(word, kana);
                    }
                    break;
                }
                case "spelled":
                    addWords(SPELLED, line);
                    break;
                case "short":
                    addWords(SHORT_WORDS, line);
                    break;
                default:
                    break;
            }
        }
    }

    private static void addWords(Set<String> target, String line) {
        for (String w : line.split("\\s+")) {
            if (!w.isEmpty()) {
                target.add(w.toLowerCase(Locale.ROOT));
            }
        }
    }

    private KanaData() {
    }
}