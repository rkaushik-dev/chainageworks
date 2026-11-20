package io.github.rkaushikdev.chainageworks.util;

import java.util.Comparator;

/**
 * The order people expect a list of references in: the Unicode root collation, for the characters
 * references are written in.
 *
 * <p>Letters compare without regard to case first ({@code a} before {@code B}), digits come before
 * letters, and spaces and punctuation come before digits in the root order (space, {@code _},
 * {@code -}, {@code ,}, {@code ;}, {@code :}, {@code !}, {@code ?}, {@code .} and so on). Only when
 * two strings tie on that does case decide, lower case first; a last tie falls back to the plain
 * character order so the comparison is total. Characters outside ASCII sort after the letters, by
 * code point.
 *
 * <p>{@link String#compareTo} would put every capital before every small letter and {@code _}
 * between them, which is not how a register reads; {@link java.text.Collator} ignores punctuation,
 * so {@code CC-1} and {@code CC1} would tie.
 */
public final class Collation {

    /** The root order of the ASCII characters that are neither letters nor digits. */
    private static final String PUNCTUATION = "\t\n\u000B\f\r _-,;:!?.'\"()[]{}@*/\\&#%`^+<=>|~$";

    private static final int DIGITS = 1000;
    private static final int LETTERS = 2000;
    private static final int OTHER = 3000;

    /** {@link #compare} as a comparator. */
    public static final Comparator<String> ORDER = Collation::compare;

    private Collation() {}

    /** Negative, zero or positive as {@code a} sorts before, with or after {@code b}. */
    public static int compare(String a, String b) {
        int primary = compareBy(a, b, true);
        if (primary != 0) {
            return primary;
        }
        int tertiary = compareBy(a, b, false);
        if (tertiary != 0) {
            return tertiary;
        }
        return Integer.signum(a.compareTo(b));
    }

    private static int compareBy(String a, String b, boolean primary) {
        int length = Math.min(a.length(), b.length());
        for (int i = 0; i < length; i++) {
            int left = primary ? primaryWeight(a.charAt(i)) : tertiaryWeight(a.charAt(i));
            int right = primary ? primaryWeight(b.charAt(i)) : tertiaryWeight(b.charAt(i));
            if (left != right) {
                return left < right ? -1 : 1;
            }
        }
        return Integer.compare(a.length(), b.length());
    }

    private static int primaryWeight(char c) {
        int punctuation = PUNCTUATION.indexOf(c);
        if (punctuation >= 0) {
            return punctuation;
        }
        if (c >= '0' && c <= '9') {
            return DIGITS + (c - '0');
        }
        if (c >= 'a' && c <= 'z') {
            return LETTERS + (c - 'a');
        }
        if (c >= 'A' && c <= 'Z') {
            return LETTERS + (c - 'A');
        }
        return OTHER + c;
    }

    private static int tertiaryWeight(char c) {
        return c >= 'A' && c <= 'Z' ? 1 : 0;
    }
}
