package com.nikohalfkino.rubydung.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a kana string into timed mouth-shape cues for the talking portrait.
 * <p>
 * The enum order is the portrait frame order: {@code ordinal()} is the index of the sprite
 * (idle, a, i, u, e, o), so {@code CLOSED} is the idle frame.
 */
public final class Visemes {

    public enum Viseme { CLOSED, A, I, U, E, O }

    public static final class Cue {
        public final long startMicros, endMicros;
        public final Viseme viseme;

        Cue(long start, long end, Viseme viseme) {
            this.startMicros = start;
            this.endMicros = end;
            this.viseme = viseme;
        }
    }

    private static final String SMALL_VOWELS = "ァィゥェォ";   // a i u e o
    private static final String SMALL_YA = "ャュョ";            // a u o
    private static final String LIP_ROWS = "mbp";               // lips close before the vowel

    /** Vowel column of KanaData.ROWS (a i u e o) to viseme. */
    private static final Viseme[] BY_INDEX = {Viseme.A, Viseme.I, Viseme.U, Viseme.E, Viseme.O};

    /** One syllable: its mouth shape, whether it starts with a lip closure, and its relative length. */
    private static final class Mora {
        Viseme viseme;
        boolean lipClose;
        int weight = 1;
    }

    /**
     * Builds the cues for one segment of audio, spreading its moras evenly over
     * {@code durationMs}, starting {@code offsetMicros} into the line's timeline.
     */
    static List<Cue> build(String kana, double durationMs, long offsetMicros) {
        List<Mora> moras = split(kana);
        List<Cue> cues = new ArrayList<>();
        if (moras.isEmpty()) {
            return cues;
        }

        int total = 0;
        for (Mora m : moras) {
            total += m.weight;
        }
        double perWeight = durationMs * 1000.0 / total;

        double t = offsetMicros;
        for (Mora m : moras) {
            double len = perWeight * m.weight;
            if (m.lipClose && m.viseme != Viseme.CLOSED) {
                double close = Math.min(len * 0.3, 40_000);   // brief closure, 40 ms at most
                cues.add(new Cue((long) t, (long) (t + close), Viseme.CLOSED));
                t += close;
                len -= close;
            }
            cues.add(new Cue((long) t, (long) (t + len), m.viseme));
            t += len;
        }
        return cues;
    }

    private static List<Mora> split(String kana) {
        List<Mora> out = new ArrayList<>();
        for (int i = 0; i < kana.length(); i++) {
            char c = toKatakana(kana.charAt(i));

            if (c == 'ー') {                       // holds the previous vowel
                if (!out.isEmpty()) {
                    out.get(out.size() - 1).weight++;
                }
                continue;
            }

            int small = SMALL_VOWELS.indexOf(c);
            int ya = SMALL_YA.indexOf(c);
            if (small >= 0 || ya >= 0) {           // merges into the previous mora and changes its vowel
                if (!out.isEmpty()) {
                    Mora prev = out.get(out.size() - 1);
                    prev.viseme = small >= 0 ? BY_INDEX[small] : BY_INDEX[new int[]{0, 2, 4}[ya]];
                    prev.lipClose = false;
                }
                continue;
            }

            Mora m = new Mora();
            if (c == 'ッ' || c == 'ン' || c == '。' || c == '、' || c == ' ') {
                m.viseme = Viseme.CLOSED;
            } else {
                int idx = vowelIndex(c);
                if (idx < 0) {
                    continue;                       // not kana (kanji, symbols): no mouth data
                }
                m.viseme = BY_INDEX[idx];
                m.lipClose = isLipKana(c);
            }
            out.add(m);
        }
        return out;
    }

    private static char toKatakana(char c) {
        return (c >= 'ぁ' && c <= 'ゖ') ? (char) (c + 0x60) : c;
    }

    /** Column of the kana in KanaData.ROWS (a i u e o), or -1. */
    private static int vowelIndex(char c) {
        String s = String.valueOf(c);
        for (String[] row : KanaData.ROWS.values()) {
            for (int i = 0; i < row.length; i++) {
                if (row[i].equals(s)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isLipKana(char c) {
        String s = String.valueOf(c);
        for (int k = 0; k < LIP_ROWS.length(); k++) {
            String[] row = KanaData.ROWS.get(String.valueOf(LIP_ROWS.charAt(k)));
            if (row == null) {
                continue;
            }
            for (String kana : row) {
                if (kana.equals(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Visemes() {
    }
}