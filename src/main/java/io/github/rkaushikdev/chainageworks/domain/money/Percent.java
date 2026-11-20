package io.github.rkaushikdev.chainageworks.domain.money;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.Result;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.util.Patterns;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A percentage in basis points, so a hundredth of one percent is the finest step. Retention is 5
 * percent, a discount on materials on site is 12.5, and a contract price adjustment factor can be
 * 3.75. All three are exact here, which they would not be as a float.
 *
 * @param basisPoints hundredths of a percent
 */
public record Percent(long basisPoints) implements Comparable<Percent> {

    public static final long BASIS_POINTS_PER_PERCENT = 100;
    public static final long BASIS_POINTS_WHOLE = 10_000;

    public static final Percent ZERO = new Percent(0);
    public static final Percent FULL = new Percent(BASIS_POINTS_WHOLE);

    private static final Pattern PERCENT_TEXT = Patterns.compile("^(-?\\d+(?:\\.\\d+)?)\\s*%?$");

    /** A percentage from basis points. */
    public static Percent ofBasisPoints(long basisPoints) {
        return new Percent(basisPoints);
    }

    /**
     * A percentage from basis points held as a double, as a stored document carries them.
     * Refuses anything that is not finite or not whole.
     */
    public static Percent ofBasisPoints(double basisPoints) {
        if (!Double.isFinite(basisPoints)) {
            throw new BadInputException(
                    "percentage must be a finite number", Details.of("value", Numbers.format(basisPoints)));
        }
        if (!Numbers.isInteger(basisPoints)) {
            throw new BadInputException(
                    "percentage must be a whole number of basis points", Details.of("value", basisPoints));
        }
        return new Percent((long) basisPoints);
    }

    /** A percentage as written, {@code 12.5} for twelve and a half percent. */
    public static Percent of(double value) {
        if (!Double.isFinite(value)) {
            throw new BadInputException(
                    "percentage must be a finite number", Details.of("value", Numbers.format(value)));
        }
        return ofBasisPoints((double) Numbers.roundHalfAway(value * BASIS_POINTS_PER_PERCENT));
    }

    /** The percentage as written: {@code 12.5}. */
    public double value() {
        return (double) basisPoints / BASIS_POINTS_PER_PERCENT;
    }

    /** The percentage as a fraction of one: {@code 0.125}. */
    public double fraction() {
        return (double) basisPoints / BASIS_POINTS_WHOLE;
    }

    public boolean isZero() {
        return basisPoints == 0;
    }

    @Override
    public int compareTo(Percent other) {
        return Long.compare(basisPoints, other.basisPoints);
    }

    public Percent plus(Percent other) {
        return new Percent(basisPoints + other.basisPoints);
    }

    public Percent minus(Percent other) {
        return new Percent(basisPoints - other.basisPoints);
    }

    /** What is left when this much is taken off. Retention of 5 leaves 95. */
    public Percent complement() {
        return new Percent(BASIS_POINTS_WHOLE - basisPoints);
    }

    /** This percentage held between {@code low} and {@code high}. */
    public Percent clamp(Percent low, Percent high) {
        if (low.basisPoints > high.basisPoints) {
            throw new BadInputException(
                    "percentage bounds are the wrong way round", Details.of("low", low.value(), "high", high.value()));
        }
        if (basisPoints < low.basisPoints) {
            return low;
        }
        if (basisPoints > high.basisPoints) {
            return high;
        }
        return this;
    }

    /** Take this much of a money figure, rounded to the penny half away from zero. */
    public Money of(Money m) {
        double scaled = (double) (m.minor() * basisPoints) / BASIS_POINTS_WHOLE;
        return new Money(Numbers.roundHalfAway(scaled), m.currency());
    }

    /** Take this much of a quantity, rounded to the thousandth half away from zero. */
    public Quantity of(Quantity q) {
        double scaled = (double) (q.milli() * basisPoints) / BASIS_POINTS_WHOLE;
        return new Quantity(Numbers.roundHalfAway(scaled), q.unit());
    }

    /** The share one part is of a whole, to the nearest basis point. */
    public static Percent share(Money part, Money whole) {
        if (whole.minor() == 0) {
            throw new BadInputException("cannot take a percentage of nothing");
        }
        double scaled = (double) (part.minor() * BASIS_POINTS_WHOLE) / whole.minor();
        return new Percent(Numbers.roundHalfAway(scaled));
    }

    /** {@code 5%} for a whole percentage, {@code 3.75%} otherwise. */
    public String format() {
        return format(basisPoints % BASIS_POINTS_PER_PERCENT == 0 ? 0 : 2);
    }

    /** The percentage to {@code decimals} places with a percent sign. */
    public String format(int decimals) {
        return Numbers.toFixed(value(), decimals) + "%";
    }

    /** Read {@code 12.5}, {@code 12.5%} or {@code 5 %}. */
    public static Result<Percent, CwException> tryParse(String text) {
        String trimmed = Text.trim(text);
        Matcher m = PERCENT_TEXT.matcher(trimmed);
        if (!m.find()) {
            return Result.err(new BadInputException("percentage must be a number", Details.of("text", trimmed)));
        }
        return Result.ok(of(Numbers.parse(m.group(1))));
    }

    /** {@link #tryParse} that throws its failure. */
    public static Percent parse(String text) {
        return Result.unwrap(tryParse(text));
    }
}
