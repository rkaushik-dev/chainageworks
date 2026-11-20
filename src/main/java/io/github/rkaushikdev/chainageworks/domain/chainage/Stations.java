package io.github.rkaushikdev.chainageworks.domain.chainage;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.Result;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.util.Patterns;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stations: points on the alignment, held as whole millimetres from the origin in a {@code long}.
 *
 * <p>Millimetres because setting out is issued to the millimetre and because floating point
 * metres drift: adding 0.1 m two hundred times does not land on 20 m. Every length in this package
 * is an integer count of millimetres and the conversion to metres happens once, at the edge, when
 * something is printed.
 */
public final class Stations {

    public static final long MM_PER_METRE = 1000;
    public static final long MM_PER_KM = 1_000_000;

    /** {@code CH 12+340.500}, {@code 12+340.5}, {@code 12+340}, and the negative forms of each. */
    private static final Pattern STATION_TEXT =
            Patterns.compile("^(?:CH\\.?\\s*)?(-)?(\\d+)\\+(\\d+)(?:\\.(\\d{1,3}))?$", "i");

    private Stations() {}

    /** A station from millimetres held as a double, refusing anything not finite or not whole. */
    public static long of(double millimetres) {
        if (!Double.isFinite(millimetres)) {
            throw new BadInputException(
                    "chainage must be a finite number", Details.of("value", Numbers.format(millimetres)));
        }
        if (!Numbers.isInteger(millimetres)) {
            throw new BadInputException(
                    "chainage must be a whole number of millimetres", Details.of("value", millimetres));
        }
        return (long) millimetres;
    }

    /** Metres in, millimetres out, rounded half away from zero. */
    public static long fromMetres(double metres) {
        if (!Double.isFinite(metres)) {
            throw new BadInputException(
                    "chainage must be a finite number", Details.of("value", Numbers.format(metres)));
        }
        return Numbers.roundHalfAway(metres * MM_PER_METRE);
    }

    public static double toMetres(long station) {
        return (double) station / MM_PER_METRE;
    }

    /**
     * Read a chainage off a drawing or a site record. The metres part is the offset inside the
     * kilometre, so it has to stay under 1000: {@code 12+1340} is a slip for {@code 13+340} often
     * enough that accepting it would hide the mistake.
     */
    public static Result<Long, CwException> tryParse(String text) {
        String trimmed = Text.trim(text);
        if (trimmed.isEmpty()) {
            return Result.err(new BadInputException("chainage is blank"));
        }
        Matcher m = STATION_TEXT.matcher(trimmed);
        if (!m.find()) {
            return Result.err(
                    new BadInputException("chainage does not look like km+metres", Details.of("text", trimmed)));
        }
        String sign = m.group(1);
        double metres = Numbers.parse(m.group(3));
        if (metres >= 1000) {
            return Result.err(new BadInputException(
                    "the metres part of a chainage must be under 1000", Details.of("text", trimmed, "metres", metres)));
        }
        String fraction = Text.padEnd(m.group(4) == null ? "" : m.group(4), 3, '0');
        double magnitude = Numbers.parse(m.group(2)) * MM_PER_KM + metres * MM_PER_METRE + Numbers.parse(fraction);
        return Result.ok(of("-".equals(sign) ? -magnitude : magnitude));
    }

    /** {@link #tryParse} that throws its failure. */
    public static long parse(String text) {
        return Result.unwrap(tryParse(text));
    }

    /** {@code 12+340.500}: kilometres, a plus, metres to three digits, three decimals. */
    public static String format(long station) {
        return format(station, 3, false);
    }

    /**
     * The station written out with {@code decimals} (0 to 3) places of the metre, and {@code CH }
     * in front when {@code prefix} is set, the way a drawing label reads.
     */
    public static String format(long station, int decimals, boolean prefix) {
        boolean negative = station < 0;
        long magnitude = Math.abs(station);
        long km = magnitude / MM_PER_KM;
        long withinKm = magnitude - km * MM_PER_KM;
        long metres = withinKm / MM_PER_METRE;
        long millimetres = withinKm - metres * MM_PER_METRE;
        String metrePart = Text.padStart(Long.toString(metres), 3, '0');
        String tail = decimals == 0
                ? ""
                : "." + Text.padStart(Long.toString(millimetres), 3, '0').substring(0, decimals);
        return (prefix ? "CH " : "") + (negative ? "-" : "") + km + "+" + metrePart + tail;
    }

    public static int compare(long a, long b) {
        return Long.compare(a, b);
    }

    public static long plus(long station, long millimetres) {
        return station + millimetres;
    }

    public static long minus(long station, long millimetres) {
        return station - millimetres;
    }

    /** Unsigned distance between two points, in millimetres. */
    public static long span(long a, long b) {
        return Math.abs(a - b);
    }

    public static long min(long a, long b) {
        return Math.min(a, b);
    }

    public static long max(long a, long b) {
        return Math.max(a, b);
    }

    /** {@code station} held between {@code low} and {@code high}. */
    public static long clamp(long station, long low, long high) {
        if (low > high) {
            throw new BadInputException("clamp bounds are the wrong way round", Details.of("low", low, "high", high));
        }
        return min(max(station, low), high);
    }
}
