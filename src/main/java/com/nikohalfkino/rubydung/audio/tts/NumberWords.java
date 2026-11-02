package com.nikohalfkino.rubydung.audio.tts;

import static com.nikohalfkino.rubydung.audio.tts.KanaData.ONES;
import static com.nikohalfkino.rubydung.audio.tts.KanaData.ORDINALS;
import static com.nikohalfkino.rubydung.audio.tts.KanaData.SCALES;
import static com.nikohalfkino.rubydung.audio.tts.KanaData.TENS;
import static com.nikohalfkino.rubydung.audio.tts.KanaData.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads digits as English number words (cardinals, ordinals, years, decimals, dollars) in kana. */
final class NumberWords {

    static int digitValue(int cp) {
        if (cp >= '0' && cp <= '9') {
            return cp - '0';
        }
        if (cp >= 0xFF10 && cp <= 0xFF19) {
            return cp - 0xFF10;
        }
        return -1;
    }

    static int readNumber(int[] cps, int start, StringBuilder out, boolean dollar) {
        int n = cps.length;
        StringBuilder ip = new StringBuilder();
        StringBuilder fp = new StringBuilder();
        boolean grouped = false;
        boolean hasFrac = false;
        int j = start;

        while (j < n) {
            int d = digitValue(cps[j]);
            if (d >= 0) {
                (hasFrac ? fp : ip).append((char) ('0' + d));
                j++;
            } else if (!hasFrac && cps[j] == ',' && j + 3 < n
                    && digitValue(cps[j + 1]) >= 0 && digitValue(cps[j + 2]) >= 0
                    && digitValue(cps[j + 3]) >= 0
                    && (j + 4 >= n || digitValue(cps[j + 4]) < 0)) {
                grouped = true;
                j++;
            } else if (!hasFrac && cps[j] == '.' && j + 1 < n && digitValue(cps[j + 1]) >= 0) {
                hasFrac = true;
                j++;
            } else {
                break;
            }
        }

        boolean ordinal = false;
        if (!hasFrac && j + 1 < n && cps[j] < 128 && cps[j + 1] < 128) {
            String suffix = ("" + (char) cps[j] + (char) cps[j + 1]).toLowerCase(Locale.ROOT);
            if ((suffix.equals("st") || suffix.equals("nd") || suffix.equals("rd") || suffix.equals("th"))
                    && (j + 2 >= n || !Katakana.isLatin(cps[j + 2]))) {
                ordinal = true;
                j += 2;
            }
        }

        int prev = start > 0 ? cps[start - 1] : -1;
        int next = j < n ? cps[j] : -1;
        if (!ordinal && ((prev >= 0 && Katakana.isJapanese(prev)) || (next >= 0 && Katakana.isJapanese(next)))) {
            for (int k = start; k < j; k++) {
                out.appendCodePoint(cps[k]);
            }
            return j - 1;
        }

        out.append(numberToKana(ip.toString(), fp.toString(), hasFrac, grouped, ordinal));
        if (dollar) {
            out.append(misc("dollar"));
        }
        return j - 1;
    }

    private static String numberToKana(String ip, String fp, boolean hasFrac, boolean grouped,
                                       boolean ordinal) {
        List<String> t = new ArrayList<>();

        boolean digitByDigit = ip.length() > 15 || (ip.length() > 1 && ip.charAt(0) == '0' && !grouped);
        if (digitByDigit) {
            for (int i = 0; i < ip.length(); i++) {
                t.add(ONES[ip.charAt(i) - '0']);
            }
        } else {
            long value = Long.parseLong(ip);
            if (!hasFrac && !grouped && !ordinal && ip.length() == 4 && isYear((int) value)) {
                yearWords((int) value, t);
            } else {
                cardinal(value, t);
            }
        }

        if (ordinal && !t.isEmpty()) {
            int last = t.size() - 1;
            t.set(last, ordinalOf(t.get(last)));
        }

        if (hasFrac) {
            t.add(misc("point"));
            for (int i = 0; i < fp.length(); i++) {
                t.add(ONES[fp.charAt(i) - '0']);
            }
        }
        return String.join("", t);
    }

    private static boolean isYear(int v) {
        return (v >= 1100 && v <= 1999) || (v >= 2010 && v <= 2099);
    }

    private static void yearWords(int year, List<String> t) {
        int hi = year / 100;
        int lo = year % 100;
        under100(hi, t);
        if (lo == 0) {
            t.add(misc("hundred"));
        } else {
            if (lo < 10) {
                t.add(misc("oh"));
            }
            under100(lo, t);
        }
    }

    private static void under100(int v, List<String> t) {
        if (v >= 20) {
            t.add(TENS[v / 10]);
            if (v % 10 > 0) {
                t.add(ONES[v % 10]);
            }
        } else if (v > 0) {
            t.add(ONES[v]);
        }
    }

    private static void under1000(int v, List<String> t) {
        if (v >= 100) {
            t.add(ONES[v / 100]);
            t.add(misc("hundred"));
            v %= 100;
        }
        under100(v, t);
    }

    private static void cardinal(long value, List<String> t) {
        if (value == 0) {
            t.add(ONES[0]);
            return;
        }
        int[] groups = new int[SCALES.length];
        int count = 0;
        long v = value;
        while (v > 0 && count < groups.length) {
            groups[count++] = (int) (v % 1000);
            v /= 1000;
        }
        for (int g = count - 1; g >= 0; g--) {
            if (groups[g] == 0) {
                continue;
            }
            under1000(groups[g], t);
            if (g > 0) {
                t.add(SCALES[g]);
            }
        }
    }

    private static String ordinalOf(String last) {
        String special = ORDINALS.get(last);
        if (special != null) {
            return special;
        }
        return last.endsWith(misc("ordinal_ty_ending"))
                ? last + misc("ordinal_suffix_ty")
                : last + misc("ordinal_suffix");
    }

    private NumberWords() {
    }
}