package io.github.rkaushikdev.chainageworks.http;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.Result;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The numbers and dates a route reads off its path and its query, refused in the job's own terms.
 *
 * <p>A query parameter given more than once is not one value, so a count named twice is refused, and
 * a date named twice is treated as not given at all.
 */
final class Params {

    private static final String FROM_ONE = " has to be a whole number from one";
    private static final String FROM_ZERO = " has to be a whole number from zero";

    private Params() {}

    /**
     * A path value that counts from one, such as a valuation number.
     *
     * @throws BadInputException unless it is a whole number from one
     */
    static int fromOne(String raw, String what) {
        return whole(raw, what, 1, FROM_ONE);
    }

    /**
     * A query count that counts from one, {@code fallback} when it is not given.
     *
     * @throws BadInputException unless it is a single whole number from one
     */
    static int count(HttpServletRequest request, String name, int fallback) {
        String[] values = request.getParameterValues(name);
        if (values == null) {
            return fallback;
        }
        return whole(single(values, name, FROM_ONE), name, 1, FROM_ONE);
    }

    /**
     * A query count that counts from zero, so nothing at all is allowed; zero when it is not given.
     *
     * @throws BadInputException unless it is a single whole number from zero
     */
    static int fromZero(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null) {
            return 0;
        }
        return whole(single(values, name, FROM_ZERO), name, 0, FROM_ZERO);
    }

    /**
     * A query date written {@code 2025-03-25}, {@code fallback} when it is not given or empty.
     *
     * @throws CwException the date's own refusal when it cannot be read
     */
    static LocalDate date(HttpServletRequest request, String name, LocalDate fallback) {
        Optional<String> raw = text(request, name).filter(value -> !value.isEmpty());
        if (raw.isEmpty()) {
            return fallback;
        }
        Result<LocalDate, CwException> parsed = SiteDates.tryParse(raw.get());
        if (!parsed.isOk()) {
            throw parsed.error();
        }
        return parsed.value();
    }

    /** A query value given exactly once. */
    static Optional<String> text(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values != null && values.length == 1 ? Optional.of(values[0]) : Optional.empty();
    }

    /** True only for a query flag given once as exactly {@code true}. */
    static boolean flag(HttpServletRequest request, String name) {
        return text(request, name).filter("true"::equals).isPresent();
    }

    private static String single(String[] values, String what, String rule) {
        if (values.length != 1) {
            throw new BadInputException(what + rule, Details.of("what", what));
        }
        return values[0];
    }

    private static int whole(String raw, String what, int least, String rule) {
        double value = Numbers.parse(raw);
        if (!Numbers.isInteger(value) || value < least || value > Integer.MAX_VALUE) {
            throw new BadInputException(what + rule, Details.of("value", raw));
        }
        return (int) value;
    }
}
