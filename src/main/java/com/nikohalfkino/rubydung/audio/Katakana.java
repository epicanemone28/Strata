package com.nikohalfkino.rubydung.audio;

import static com.nikohalfkino.rubydung.audio.KanaData.DICT;
import static com.nikohalfkino.rubydung.audio.KanaData.LETTERS;
import static com.nikohalfkino.rubydung.audio.KanaData.ROWS;
import static com.nikohalfkino.rubydung.audio.KanaData.SHORT_WORDS;
import static com.nikohalfkino.rubydung.audio.KanaData.SPELLED;
import static com.nikohalfkino.rubydung.audio.KanaData.SYMBOLS;
import static com.nikohalfkino.rubydung.audio.KanaData.VOICED_TH;
import static com.nikohalfkino.rubydung.audio.KanaData.misc;
import static com.nikohalfkino.rubydung.audio.NumberWords.digitValue;
import static com.nikohalfkino.rubydung.audio.NumberWords.readNumber;

import java.text.Normalizer;
import java.util.Locale;

public final class Katakana {

    private static final String VOWELS = "aiueo";

    private Katakana() {
    }

    public static String toSingingKana(String phoneme) {
        String kana = convert(phoneme, false);
        StringBuilder out = new StringBuilder(kana.length());
        for (int i = 0; i < kana.length(); i++) {
            char c = kana.charAt(i);
            if (i > 0 && isKanaVowel(c) && out.length() > 0
                    && out.charAt(out.length() - 1) == c) {
                out.append('ー');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isKanaVowel(char c) {
        return "アイウエオ".indexOf(c) >= 0;
    }

    public static String toKana(String text) {
        return convert(text, true);
    }

    private static String convert(String text, boolean smart) {
        int[] cps = text.codePoints().toArray();
        int n = cps.length;
        StringBuilder out = new StringBuilder();
        StringBuilder word = new StringBuilder();
        boolean dotted = false;
        boolean dollar = false;

        for (int i = 0; i < n; i++) {
            int cp = cps[i];

            if (cp == '\'' || cp == 0x2019) {
                continue;
            }

            if (isLatin(cp)) {
                word.append(baseLetters(cp));
                continue;
            }

            if (smart && cp == '.' && word.length() >= 1 && (dotted || word.length() == 1)
                    && i + 1 < n && isLatin(cps[i + 1]) && (i + 2 >= n || !isLatin(cps[i + 2]))) {
                dotted = true;
                continue;
            }

            flush(word, out, smart, dotted);
            dotted = false;

            if (smart && digitValue(cp) >= 0) {
                i = readNumber(cps, i, out, dollar);
                dollar = false;
            } else if (isJapanese(cp) || Character.isDigit(cp)) {
                out.appendCodePoint(cp);
            } else if (".!?。！？".indexOf(cp) >= 0) {
                out.append('。');
            } else if (",;:、，；：".indexOf(cp) >= 0) {
                out.append('、');
            } else if (Character.isWhitespace(cp)) {
                out.append(' ');
            } else if (smart) {
                boolean nextDigit = i + 1 < n && digitValue(cps[i + 1]) >= 0;
                if (cp == '$') {
                    if (nextDigit) {
                        dollar = true;
                    } else {
                        out.append(misc("dollar"));
                    }
                } else if (cp == '-') {
                    if (nextDigit && (i == 0 || !Character.isLetterOrDigit(cps[i - 1]))) {
                        out.append(misc("minus"));
                    }
                } else if (cp == '#') {
                    out.append(misc(nextDigit ? "number" : "hash"));
                } else {
                    String symbol = symbolWord(cp);
                    if (symbol != null) {
                        out.append(symbol);
                    }
                }
            }
        }

        flush(word, out, smart, dotted);
        return out.toString().trim();
    }

    private static void flush(StringBuilder word, StringBuilder out, boolean smart, boolean spellOut) {
        if (word.length() == 0) {
            return;
        }
        String raw = word.toString();
        word.setLength(0);
        out.append(smart ? smartWord(raw, spellOut) : latinWord(raw.toLowerCase(Locale.ROOT)));
    }

    private static String symbolWord(int cp) {
        return SYMBOLS.get(cp);
    }

    static boolean isLatin(int cp) {
        return Character.isLetter(cp) && Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN;
    }

    static boolean isJapanese(int cp) {
        if (cp == 0x30FC || cp == 0x3005) {
            return true;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HAN;
    }

    private static String baseLetters(int cp) {
        switch (cp) {
            case 0xDF:
                return "ss";
            case 0xE6:
                return "ae";
            case 0xC6:
                return "AE";
            case 0x153:
                return "oe";
            case 0x152:
                return "OE";
            case 0xF8:
            case 0xF0:
            case 0x111:
                return cp == 0xF8 ? "o" : "d";
            case 0xD8:
            case 0x110:
            case 0xD0:
                return cp == 0xD8 ? "O" : "D";
            case 0x142:
                return "l";
            case 0x141:
                return "L";
            case 0xFE:
                return "th";
            case 0xDE:
                return "TH";
            case 0x131:
                return "i";
            default:
                break;
        }
        String decomposed = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD);
        StringBuilder letters = new StringBuilder();
        for (char c : decomposed.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                letters.append(c);
            }
        }
        return letters.toString();
    }

    private static boolean isAllUpper(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 'A' || c > 'Z') {
                return false;
            }
        }
        return !s.isEmpty();
    }

    private static boolean hasVowel(String lower) {
        for (int i = 0; i < lower.length(); i++) {
            if (isVowelAt(lower, i)) {
                return true;
            }
        }
        return false;
    }

    private static String spell(String letters) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < letters.length(); i++) {
            char c = Character.toLowerCase(letters.charAt(i));
            if (c >= 'a' && c <= 'z') {
                sb.append(LETTERS[c - 'a']);
            }
        }
        return sb.toString();
    }

    private static String smartWord(String raw, boolean forceSpell) {
        String lower = raw.toLowerCase(Locale.ROOT);

        if (forceSpell) {
            return spell(lower);
        }

        if (lower.length() == 1) {
            return lower.equals("a") ? "ア" : spell(lower);
        }

        String known = DICT.get(lower);
        if (known != null) {
            return known;
        }
        if (SPELLED.contains(lower)) {
            return spell(lower);
        }

        if (raw.length() >= 3 && raw.endsWith("s") && isAllUpper(raw.substring(0, raw.length() - 1))) {
            String base = raw.substring(0, raw.length() - 1);
            if (!hasVowel(base.toLowerCase(Locale.ROOT)) || base.length() <= 3
                    || SPELLED.contains(base.toLowerCase(Locale.ROOT))) {
                return spell(base) + misc("plural");
            }
        }

        if (isAllUpper(raw)) {
            if (!hasVowel(lower)) {
                return spell(lower);
            }
            if (lower.length() <= 3 && !SHORT_WORDS.contains(lower)) {
                return spell(lower);
            }
            return latinWord(lower);
        }

        if (lower.length() <= 4 && !hasVowel(lower)) {
            return spell(lower);
        }

        return latinWord(lower);
    }

    private static boolean isVowelLetter(char c) {
        return VOWELS.indexOf(c) >= 0;
    }

    private static boolean isVowelAt(String w, int j) {
        if (j < 0 || j >= w.length()) {
            return false;
        }
        char c = w.charAt(j);
        if (isVowelLetter(c)) {
            return true;
        }
        return c == 'y' && j > 0 && (j + 1 >= w.length() || !isVowelLetter(w.charAt(j + 1)));
    }

    private static String latinWord(String w) {
        boolean voicedTh = VOICED_TH.contains(w);

        int n = w.length();
        if (n > 2 && w.charAt(n - 1) == 'e' && !isVowelAt(w, n - 2) && hasVowelBefore(w, n - 2)) {
            w = w.substring(0, n - 1);
            n--;
        }

        StringBuilder out = new StringBuilder();
        int i = 0;

        while (i < n) {
            if (isVowelAt(w, i)) {
                i += vowelGroup("", w, i, out);
                continue;
            }

            char c = w.charAt(i);
            char d = i + 1 < n ? w.charAt(i + 1) : '\0';

            if (i == 0 && ((c == 'k' && d == 'n') || (c == 'w' && d == 'r'))) {
                i++;
                continue;
            }

            if (d == c) {
                if ("ksptcgdbzf".indexOf(c) >= 0) {
                    out.append("ッ");
                }
                i++;
                continue;
            }

            if (c == 'g' && d == 'h' && i > 0) {
                i += 2;
                continue;
            }

            if (c == 'n' && d == 'g') {
                if (isVowelAt(w, i + 2)) {
                    out.append("ン");
                    i++;
                } else {
                    out.append("ング");
                    i += 2;
                }
                continue;
            }

            String key;
            int len = 1;

            if (c == 's' && d == 'h') {
                key = "sh";
                len = 2;
            } else if (c == 'c' && d == 'h') {
                key = "ch";
                len = 2;
            } else if (c == 't' && d == 'h') {
                key = voicedTh ? "z" : "s";
                len = 2;
            } else if (c == 'p' && d == 'h') {
                key = "f";
                len = 2;
            } else if (c == 'w' && d == 'h') {
                key = "w";
                len = 2;
            } else if (c == 'c' && d == 'k') {
                key = "k";
                len = 2;
            } else if (c == 'q') {
                key = "q";
                len = d == 'u' ? 2 : 1;
            } else if (c == 'x') {
                if (i == 0) {
                    key = "z";
                } else {
                    out.append("ク");
                    key = "s";
                }
            } else if (c == 'c') {
                key = (d == 'e' || d == 'i' || d == 'y') ? "s" : "k";
            } else if (c == 'l') {
                key = "r";
            } else {
                key = String.valueOf(c);
            }

            int next = i + len;
            if (isVowelAt(w, next)) {
                i = next + vowelGroup(key, w, next, out);
            } else {
                finalConsonant(key, c, w, i, len, out);
                i = next;
            }
        }

        return out.toString();
    }

    private static boolean hasVowelBefore(String w, int end) {
        for (int i = 0; i < end; i++) {
            if (isVowelAt(w, i)) {
                return true;
            }
        }
        return false;
    }

    private static int vowelGroup(String key, String w, int j, StringBuilder out) {
        int n = w.length();
        char a = w.charAt(j);
        char b = j + 1 < n ? w.charAt(j + 1) : '\0';
        boolean glide = (b == 'y' || b == 'w') && (j + 2 >= n || !isVowelLetter(w.charAt(j + 2)));
        String pair = "" + a + b;

        int idx;
        String tail = "";
        int used = 2;

        if (pair.equals("ee") || pair.equals("ea") || pair.equals("ie") || pair.equals("ei")) {
            idx = 1;
            tail = "ー";
        } else if (pair.equals("oo")) {
            idx = 2;
            tail = "ー";
        } else if (pair.equals("oa")) {
            idx = 4;
            tail = "ー";
        } else if (pair.equals("ai") || (pair.equals("ay") && glide)) {
            idx = 3;
            tail = "イ";
        } else if (pair.equals("ou") || (pair.equals("ow") && glide)) {
            idx = 0;
            tail = "ウ";
        } else if (pair.equals("au") || (pair.equals("aw") && glide)) {
            idx = 4;
            tail = "ー";
        } else if (pair.equals("oi") || (pair.equals("oy") && glide)) {
            idx = 4;
            tail = "イ";
        } else {
            idx = VOWELS.indexOf(a == 'y' ? 'i' : a);
            used = 1;
        }

        out.append(ROWS.get(key)[idx]).append(tail);
        return used;
    }

    private static void finalConsonant(String key, char letter, String w, int i, int len, StringBuilder out) {
        int n = w.length();

        boolean atEnd = i + len >= n;
        if (atEnd && (key.equals("t") || key.equals("k") || key.equals("p"))
                && i > 0 && isVowelLetter(w.charAt(i - 1))
                && !(i > 1 && isVowelLetter(w.charAt(i - 2)))) {
            out.append("ッ");
        }

        String kana;
        switch (key) {
            case "t":
                kana = "ト";
                break;
            case "d":
                kana = "ド";
                break;
            case "sh":
                kana = "シュ";
                break;
            case "ch":
                kana = "チ";
                break;
            case "j":
                kana = "ジ";
                break;
            case "n":
                kana = "ン";
                break;
            case "h":
                kana = "";
                break;
            case "w":
                kana = "ウ";
                break;
            case "m":
                kana = "ム";
                break;
            case "r":
                if (letter != 'l' && i > 0 && isVowelLetter(w.charAt(i - 1))
                        && !(out.length() > 0 && out.charAt(out.length() - 1) == 'ー')) {
                    kana = "ー";
                } else if (out.length() > 0 && out.charAt(out.length() - 1) == 'ー') {
                    kana = "";
                } else {
                    kana = "ル";
                }
                break;
            default:
                kana = ROWS.get(key)[2];
                break;
        }
        out.append(kana);
    }
}