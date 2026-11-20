package io.github.rkaushikdev.chainageworks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The details an error carries: a flat, ordered map of names to strings, numbers and booleans.
 *
 * <p>Numbers are held in one form so that two details maps compare equal when they say the same
 * thing: a whole number is a {@link Long} however it was handed in, and anything with a fraction
 * is a {@link Double}. A {@code null} value means the detail is absent and is left out.
 */
public final class Details {

    private Details() {}

    /** No details at all. */
    public static Map<String, Object> none() {
        return Map.of();
    }

    /** Details from alternating names and values. */
    public static Map<String, Object> of(Object... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("details come in name and value pairs");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            String name = (String) namesAndValues[i];
            Object value = normalise(namesAndValues[i + 1]);
            if (value != null) {
                details.put(name, value);
            }
        }
        return Collections.unmodifiableMap(details);
    }

    /** A copy of {@code details} with every value in its canonical form. */
    public static Map<String, Object> copyOf(Map<String, ?> details) {
        Map<String, Object> copy = new LinkedHashMap<>();
        details.forEach((name, value) -> {
            Object normal = normalise(value);
            if (normal != null) {
                copy.put(name, normal);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    /** The canonical form of one detail value. */
    public static Object normalise(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (Double.isFinite(d) && Math.floor(d) == d && Math.abs(d) < 0x1p63) {
                return (long) d;
            }
            return d;
        }
        if (value instanceof Enum<?> || value instanceof CharSequence) {
            return value.toString();
        }
        throw new IllegalArgumentException("a detail is a string, a number or a boolean: "
                + value.getClass().getName());
    }
}
