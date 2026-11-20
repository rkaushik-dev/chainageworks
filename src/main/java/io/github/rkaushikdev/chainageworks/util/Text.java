package io.github.rkaushikdev.chainageworks.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * String helpers with one definition of white space.
 *
 * <p>White space is the ASCII tab, line feed, vertical tab, form feed, carriage return and space,
 * plus the Unicode space separators (no-break space U+00A0 included), the line and paragraph
 * separators and the byte order mark U+FEFF. Site exports pasted out of spreadsheets carry no-break
 * spaces as often as plain ones, so trimming has to remove both.
 */
public final class Text {

    /** The white space characters, as the body of a regular expression character class. */
    public static final String SPACE_CLASS_BODY =
            "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF";

    private Text() {}

    /** True for one of the white space characters described above. */
    public static boolean isSpace(char c) {
        return switch (c) {
            case '\t', '\n', '\u000B', '\f', '\r', ' ', ' ', ' ', ' ', ' ', ' ', ' ', '　', '﻿' -> true;
            default -> c >= ' ' && c <= ' ';
        };
    }

    /** {@code value} with white space taken off both ends. */
    public static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    /** {@code value} with white space taken off the start. */
    public static String trimStart(String value) {
        int start = 0;
        while (start < value.length() && isSpace(value.charAt(start))) {
            start++;
        }
        return value.substring(start);
    }

    /** {@code value} with white space taken off the end. */
    public static String trimEnd(String value) {
        int end = value.length();
        while (end > 0 && isSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    /** Lower case, the same on every machine whatever its locale. */
    public static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    /** Upper case, the same on every machine whatever its locale. */
    public static String upper(String value) {
        return value.toUpperCase(Locale.ROOT);
    }

    /** {@code value} padded on the left with {@code fill} to {@code width} characters. */
    public static String padStart(String value, int width, char fill) {
        if (value.length() >= width) {
            return value;
        }
        return String.valueOf(fill).repeat(width - value.length()) + value;
    }

    /** {@code value} padded on the left with spaces to {@code width} characters. */
    public static String padStart(String value, int width) {
        return padStart(value, width, ' ');
    }

    /** {@code value} padded on the right with {@code fill} to {@code width} characters. */
    public static String padEnd(String value, int width, char fill) {
        if (value.length() >= width) {
            return value;
        }
        return value + String.valueOf(fill).repeat(width - value.length());
    }

    /** {@code value} padded on the right with spaces to {@code width} characters. */
    public static String padEnd(String value, int width) {
        return padEnd(value, width, ' ');
    }

    /**
     * Split on a literal separator, keeping every empty part, so {@code "a,,b,"} is four parts and
     * an empty string is one empty part.
     */
    public static List<String> split(String value, String separator) {
        List<String> parts = new ArrayList<>();
        if (separator.isEmpty()) {
            for (int i = 0; i < value.length(); i++) {
                parts.add(String.valueOf(value.charAt(i)));
            }
            return parts;
        }
        int from = 0;
        while (true) {
            int at = value.indexOf(separator, from);
            if (at < 0) {
                parts.add(value.substring(from));
                return parts;
            }
            parts.add(value.substring(from, at));
            from = at + separator.length();
        }
    }
}
