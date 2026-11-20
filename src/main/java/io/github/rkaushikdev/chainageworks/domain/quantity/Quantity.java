package io.github.rkaushikdev.chainageworks.domain.quantity;

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
 * An amount of something in a bill unit, held as whole thousandths for the same reason chainage
 * is: a quantity that has to add up is never a float.
 *
 * @param milli the amount times one thousand
 * @param unit what it is counted in
 */
public record Quantity(long milli, Unit unit) implements Comparable<Quantity> {

    /** Thousandths in one unit. */
    public static final long MILLI = 1000;

    private static final Pattern QUANTITY_TEXT =
            Patterns.compile("^(-?\\d+(?:\\.\\d+)?)\\s*([A-Za-z][A-Za-z0-9²³.\\s]*)$");

    /** A quantity from thousandths. */
    public static Quantity ofMilli(long milli, Unit unit) {
        return new Quantity(milli, unit);
    }

    /**
     * A quantity from thousandths held as a double, as a stored document carries them. Refuses
     * anything that is not finite or not whole.
     */
    public static Quantity ofMilli(double milli, Unit unit) {
        if (!Double.isFinite(milli)) {
            throw new BadInputException("quantity must be a finite number", Details.of("value", Numbers.format(milli)));
        }
        if (!Numbers.isInteger(milli)) {
            throw new BadInputException("quantity must be a whole number of thousandths", Details.of("value", milli));
        }
        return new Quantity((long) milli, unit);
    }

    /** A quantity from the amount, rounded to the thousandth half away from zero. */
    public static Quantity of(double amount, Unit unit) {
        if (!Double.isFinite(amount)) {
            throw new BadInputException(
                    "quantity must be a finite number", Details.of("value", Numbers.format(amount)));
        }
        return new Quantity(Numbers.roundHalfAway(amount * MILLI), unit);
    }

    /** None of {@code unit}. */
    public static Quantity zero(Unit unit) {
        return new Quantity(0, unit);
    }

    /** The amount: thousandths divided back out. */
    public double amount() {
        return (double) milli / MILLI;
    }

    public boolean isZero() {
        return milli == 0;
    }

    public boolean isNegative() {
        return milli < 0;
    }

    public Quantity plus(Quantity other) {
        Unit.assertSame(unit, other.unit);
        return new Quantity(milli + other.milli, unit);
    }

    public Quantity minus(Quantity other) {
        Unit.assertSame(unit, other.unit);
        return new Quantity(milli - other.milli, unit);
    }

    public Quantity negate() {
        return new Quantity(-milli, unit);
    }

    /** Take a fraction of a quantity. Used by rules of credit, so it rounds half away from zero. */
    public Quantity scale(double factor) {
        if (!Double.isFinite(factor)) {
            throw new BadInputException(
                    "scale factor must be a finite number", Details.of("value", Numbers.format(factor)));
        }
        return new Quantity(Numbers.roundHalfAway(milli * factor), unit);
    }

    @Override
    public int compareTo(Quantity other) {
        Unit.assertSame(unit, other.unit);
        return Long.compare(milli, other.milli);
    }

    /** The smaller of two quantities, the first on a tie. */
    public static Quantity min(Quantity a, Quantity b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** The larger of two quantities, the first on a tie. */
    public static Quantity max(Quantity a, Quantity b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /**
     * Sum a list. The unit has to be named because an empty list still has to know what it is a
     * zero of, and a running total of nothing in metres is not the same total as nothing in cubic
     * metres.
     */
    public static Quantity sum(Iterable<Quantity> parts, Unit unit) {
        long milli = 0;
        for (Quantity part : parts) {
            Unit.assertSame(unit, part.unit);
            milli += part.milli;
        }
        return new Quantity(milli, unit);
    }

    /**
     * Round to the number of places the bill is written to. A measured quantity is only billed at
     * its unit's precision, so this is what goes on the valuation even when the survey booked more
     * figures than that.
     */
    public Quantity roundedToBillPrecision() {
        long step = MILLI / (long) Math.pow(10, unit.decimals());
        if (step <= 1) {
            return this;
        }
        double halves = (double) milli / step;
        return new Quantity(Numbers.roundHalfAway(halves) * step, unit);
    }

    /** {@code 12.50 m3}: the amount to the unit's bill places, then the unit. */
    public String format() {
        return format(unit.decimals());
    }

    /** The amount to {@code places} decimal places, then the unit. */
    public String format(int places) {
        return Numbers.toFixed(amount(), places) + " " + unit.code();
    }

    /** Read a number and a unit: {@code 12.5 m3}, {@code 40 No.}. */
    public static Result<Quantity, CwException> tryParse(String text) {
        String trimmed = Text.trim(text);
        Matcher m = QUANTITY_TEXT.matcher(trimmed);
        if (!m.find()) {
            return Result.err(
                    new BadInputException("quantity must be a number and a unit", Details.of("text", trimmed)));
        }
        try {
            return Result.ok(of(Numbers.parse(m.group(1)), Unit.parse(m.group(2))));
        } catch (BadInputException refused) {
            return Result.err(refused);
        }
    }

    /** {@link #tryParse} that throws its failure. */
    public static Quantity parse(String text) {
        return Result.unwrap(tryParse(text));
    }

    /**
     * A lump sum is billed as exactly one of itself.
     *
     * <p>This is about the BILLED quantity, not the earned one. A lump item earns through its rule
     * of credit and can sit at any fraction part way through, but a bill that carries three sums
     * of the same lump item has been written wrong.
     */
    public void assertBillableLumpSum() {
        if (!unit.isMeasurable() && milli != MILLI) {
            throw new BadInputException(
                    "a lump sum item is billed as exactly one sum",
                    Details.of("unit", unit.code(), "amount", amount()));
        }
    }
}
