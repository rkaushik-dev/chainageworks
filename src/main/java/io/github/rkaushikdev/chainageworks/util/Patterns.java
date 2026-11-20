package io.github.rkaushikdev.chainageworks.util;

import java.util.regex.Pattern;

/**
 * Regular expressions with the white space and end of text rules the rest of the package uses.
 *
 * <p>Patterns are written in the ordinary syntax and three things are adjusted when they are
 * compiled: {@code \s} and {@code \S} mean the white space of {@link Text}, not only ASCII; a
 * {@code $} outside a character class means the very end of the text, so a trailing line break
 * never sneaks through an anchored pattern; and the {@code i} flag folds case for every letter,
 * not only ASCII ones.
 */
public final class Patterns {

    private static final String SPACE = "[" + Text.SPACE_CLASS_BODY + "]";
    private static final String NOT_SPACE = "[^" + Text.SPACE_CLASS_BODY + "]";

    private Patterns() {}

    /** Compile {@code source} with no flags. */
    public static Pattern compile(String source) {
        return compile(source, "");
    }

    /**
     * Compile {@code source}. {@code flags} may hold {@code i} (ignore case) and {@code g}, which
     * is accepted and ignored: a Java pattern is applied once or to every match by the caller.
     */
    public static Pattern compile(String source, String flags) {
        int javaFlags = 0;
        for (char flag : flags.toCharArray()) {
            if (flag == 'i') {
                javaFlags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            } else if (flag != 'g') {
                throw new IllegalArgumentException("unsupported pattern flag: " + flag);
            }
        }
        return Pattern.compile(translate(source), javaFlags);
    }

    static String translate(String source) {
        StringBuilder out = new StringBuilder(source.length() + 16);
        boolean inClass = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\' && i + 1 < source.length()) {
                char next = source.charAt(i + 1);
                i++;
                if (next == 's') {
                    out.append(inClass ? Text.SPACE_CLASS_BODY : SPACE);
                } else if (next == 'S' && !inClass) {
                    out.append(NOT_SPACE);
                } else {
                    out.append('\\').append(next);
                }
                continue;
            }
            if (inClass) {
                if (c == ']') {
                    inClass = false;
                    out.append(c);
                } else if (c == '[' || c == '&') {
                    out.append('\\').append(c);
                } else {
                    out.append(c);
                }
                continue;
            }
            if (c == '[') {
                inClass = true;
                out.append(c);
                if (i + 1 < source.length() && source.charAt(i + 1) == '^') {
                    out.append('^');
                    i++;
                }
                if (i + 1 < source.length() && source.charAt(i + 1) == ']') {
                    out.append("\\]");
                    i++;
                }
                continue;
            }
            if (c == '$') {
                out.append("\\z");
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
