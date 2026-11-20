package io.github.rkaushikdev.chainageworks.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Number text and rounding, defined once so every report, file and message writes a number the
 * same way.
 *
 * <p>A figure is written as the shortest decimal that reads back to the same double: no trailing
 * {@code .0} on a whole number, plain digits from a millionth up to 10<sup>21</sup>, and exponent
 * form outside that ({@code 1e-7}, {@code 1e+21}). Fixed places round the exact binary value half
 * up, so {@code 1.005} to two places is {@code 1.00}, because the double is a shade under 1.005.
 */
public final class Numbers {

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");
    private static final Pattern HEX = Pattern.compile("0[xX][0-9a-fA-F]+");
    private static final Pattern OCTAL = Pattern.compile("0[oO][0-7]+");
    private static final Pattern BINARY = Pattern.compile("0[bB][01]+");

    private static final double PLAIN_LIMIT = 1e21;

    private Numbers() {}

    /** The shortest decimal text that reads back to {@code value}. */
    public static String format(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (value == 0) {
            return "0";
        }
        if (value < 0) {
            return "-" + format(-value);
        }
        BigDecimal exact = new BigDecimal(Double.toString(value)).stripTrailingZeros();
        String digits = exact.unscaledValue().toString();
        int k = digits.length();
        int n = k - exact.scale();
        if (k <= n && n <= 21) {
            return digits + "0".repeat(n - k);
        }
        if (0 < n && n <= 21) {
            return digits.substring(0, n) + "." + digits.substring(n);
        }
        if (-6 < n && n <= 0) {
            return "0." + "0".repeat(-n) + digits;
        }
        int exponent = n - 1;
        String sign = exponent < 0 ? "-" : "+";
        String mantissa = k == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        return mantissa + "e" + sign + Math.abs(exponent);
    }

    /** A whole number written without a decimal point. */
    public static String format(long value) {
        return Long.toString(value);
    }

    /**
     * {@code value} to exactly {@code places} decimal places, rounding the exact binary value half
     * up. A negative value keeps its sign even when it rounds to zero ({@code -0.001} is
     * {@code -0.00}).
     */
    public static String toFixed(double value, int places) {
        if (places < 0 || places > 100) {
            throw new IllegalArgumentException("decimal places must be 0 to 100: " + places);
        }
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Math.abs(value) >= PLAIN_LIMIT || Double.isInfinite(value)) {
            return format(value);
        }
        if (value < 0) {
            return "-" + toFixed(-value, places);
        }
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Read number text the way a form field is read: surrounding white space is ignored, blank
     * text is zero, decimal, exponent and {@code 0x}/{@code 0o}/{@code 0b} forms are accepted, and
     * anything else is {@code NaN}.
     */
    public static double parse(String text) {
        String trimmed = Text.trim(text);
        if (trimmed.isEmpty()) {
            return 0;
        }
        if (trimmed.equals("Infinity") || trimmed.equals("+Infinity")) {
            return Double.POSITIVE_INFINITY;
        }
        if (trimmed.equals("-Infinity")) {
            return Double.NEGATIVE_INFINITY;
        }
        if (DECIMAL.matcher(trimmed).matches()) {
            return Double.parseDouble(trimmed);
        }
        if (HEX.matcher(trimmed).matches()) {
            return new BigInteger(trimmed.substring(2), 16).doubleValue();
        }
        if (OCTAL.matcher(trimmed).matches()) {
            return new BigInteger(trimmed.substring(2), 8).doubleValue();
        }
        if (BINARY.matcher(trimmed).matches()) {
            return new BigInteger(trimmed.substring(2), 2).doubleValue();
        }
        return Double.NaN;
    }

    /** True for a finite value with no fractional part. */
    public static boolean isInteger(double value) {
        return Double.isFinite(value) && Math.floor(value) == value;
    }

    /** True for a value that is neither infinite nor {@code NaN}. */
    public static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    /**
     * The nearest whole number, a half going up ({@code 2.5} is 3, {@code -2.5} is -2).
     */
    public static double round(double value) {
        if (!Double.isFinite(value) || value == 0) {
            return value;
        }
        double floor = Math.floor(value);
        double result = value - floor >= 0.5 ? floor + 1 : floor;
        return result == 0 && value < 0 ? -0.0 : result;
    }

    /**
     * The nearest whole number, a half going away from zero ({@code 2.5} is 3, {@code -2.5} is
     * -3). This is how every scaled amount in a valuation is rounded.
     */
    public static long roundHalfAway(double value) {
        return value < 0 ? -(long) round(-value) : (long) round(value);
    }
}
