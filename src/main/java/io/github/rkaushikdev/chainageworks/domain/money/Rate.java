package io.github.rkaushikdev.chainageworks.domain.money;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.Result;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.util.Patterns;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A bill rate: what one unit of an item is worth.
 *
 * <p>Rates are held to a thousandth of a minor unit because tendered rates are quoted to more
 * places than money is paid in. A rate of 0.0035 per kilogram is ordinary on a steel item, and
 * rounding it to the penny before the extension would lose most of the value of the item.
 *
 * @param milliMinor thousandths of a minor unit per unit
 * @param currency what it is priced in
 * @param unit what one of is priced
 */
public record Rate(long milliMinor, Currency currency, Unit unit) {

    /** A rate carries three more decimal places than the money it produces. */
    public static final long RATE_SCALE = 1000;

    private static final Pattern RATE_TEXT =
            Patterns.compile("^(.+?)\\s*(?:/|\\bper\\b)\\s*([A-Za-z][A-Za-z0-9²³.\\s]*)$", "i");
    private static final Pattern AMOUNT_TEXT =
            Patterns.compile("^(-?)\\s*([£€$])?\\s*(-?[\\d,]+(?:\\.\\d+)?)\\s*([A-Za-z]{3})?$");

    /** A rate from thousandths of a minor unit. */
    public static Rate ofMilliMinor(long milliMinor, Currency currency, Unit unit) {
        return new Rate(milliMinor, currency, unit);
    }

    /**
     * A rate from thousandths of a minor unit held as a double, as a stored document carries
     * them. Refuses anything that is not finite or not whole.
     */
    public static Rate ofMilliMinor(double milliMinor, Currency currency, Unit unit) {
        if (!Double.isFinite(milliMinor)) {
            throw new BadInputException(
                    "rate must be a finite number", Details.of("value", Numbers.format(milliMinor)));
        }
        if (!Numbers.isInteger(milliMinor)) {
            throw new BadInputException("rate must be a whole number of rate units", Details.of("value", milliMinor));
        }
        return new Rate((long) milliMinor, currency, unit);
    }

    /** Build from what the bill actually says, for example 12.34 per cubic metre. */
    public static Rate of(double major, Currency currency, Unit unit) {
        if (!Double.isFinite(major)) {
            throw new BadInputException("rate must be a finite number", Details.of("value", Numbers.format(major)));
        }
        double scaled = major * currency.minorUnitsPerMajor() * RATE_SCALE;
        return ofMilliMinor((double) Numbers.roundHalfAway(scaled), currency, unit);
    }

    /** A rate of nothing. */
    public static Rate zero(Currency currency, Unit unit) {
        return new Rate(0, currency, unit);
    }

    /** The rate in major units: pounds per unit. */
    public double major() {
        return (double) milliMinor / (currency.minorUnitsPerMajor() * RATE_SCALE);
    }

    public boolean isZero() {
        return milliMinor == 0;
    }

    public boolean isNegative() {
        return milliMinor < 0;
    }

    /** A rate for one whole unit, taken from a money figure. */
    public static Rate fromMoney(Money m, Unit unit) {
        return new Rate(m.minor() * RATE_SCALE, m.currency(), unit);
    }

    /**
     * The extension: rate times quantity, rounded to the penny once, at the end.
     *
     * <p>Both operands carry three extra places, so the product carries six and the divisor takes
     * them all off together. Rounding the rate first, or the quantity first, gives a different
     * answer on a long bill.
     */
    public Money extend(Quantity q) {
        Unit.assertSame(unit, q.unit());
        double product = (double) milliMinor * (double) q.milli();
        double scaled = product / (RATE_SCALE * Quantity.MILLI);
        return Money.of((double) Numbers.roundHalfAway(scaled), currency);
    }

    /** The rate times {@code factor}, rounded half away from zero. */
    public Rate scale(double factor) {
        if (!Double.isFinite(factor)) {
            throw new BadInputException(
                    "scale factor must be a finite number", Details.of("value", Numbers.format(factor)));
        }
        return new Rate(Numbers.roundHalfAway(milliMinor * factor), currency, unit);
    }

    /** Order two rates for the same unit in the same currency. */
    public static int compare(Rate a, Rate b) {
        if (a.currency != b.currency) {
            throw new BadInputException(
                    "cannot compare rates in different currencies",
                    Details.of("left", a.currency.code(), "right", b.currency.code()));
        }
        Unit.assertSame(a.unit, b.unit);
        return Long.compare(a.milliMinor, b.milliMinor);
    }

    /** {@code £12.34/m3}, with as many places as the rate needs, two at least and five at most. */
    public String format() {
        return format(significantDecimals(), true);
    }

    /**
     * The rate written to {@code decimals} places. {@code symbol} false writes the currency code
     * after the amount: {@code 12.34 GBP/m3}.
     */
    public String format(int decimals, boolean symbol) {
        String body = Numbers.toFixed(major(), decimals);
        String head = symbol ? currency.symbol() : "";
        String tail = symbol ? "" : " " + currency.code();
        return head + body + tail + "/" + unit.code();
    }

    /** {@link #format(int, boolean)} with as many places as the rate needs. */
    public String format(boolean symbol) {
        return format(significantDecimals(), symbol);
    }

    /** Two places unless the rate genuinely needs more, up to five. */
    int significantDecimals() {
        double perMajor = (double) currency.minorUnitsPerMajor() * RATE_SCALE;
        double remainder = Math.abs((double) milliMinor) % perMajor;
        int decimals = 0;
        double step = perMajor;
        while (remainder > 0 && decimals < 5) {
            step /= 10;
            decimals += 1;
            remainder %= step;
        }
        return Math.max(2, decimals);
    }

    /** Read {@code £12.34/m3}, {@code 0.0035 per kg} or {@code 10 EUR/m}. */
    public static Result<Rate, CwException> tryParse(String text, Currency fallback) {
        String trimmed = Text.trim(text);
        Matcher parts = RATE_TEXT.matcher(trimmed);
        if (!parts.find()) {
            return Result.err(new BadInputException("a rate is an amount per unit", Details.of("text", trimmed)));
        }
        Unit unit;
        try {
            unit = Unit.parse(parts.group(2));
        } catch (BadInputException refused) {
            return Result.err(refused);
        }
        Matcher amount = AMOUNT_TEXT.matcher(Text.trim(parts.group(1)));
        if (!amount.find()) {
            return Result.err(new BadInputException("a rate is an amount per unit", Details.of("text", trimmed)));
        }
        String sign = amount.group(1);
        String symbol = amount.group(2);
        String digits = amount.group(3);
        String code = amount.group(4);
        Currency currency = fallback;
        if (code != null) {
            String upper = Text.upper(code);
            if (!Currency.isCode(upper)) {
                return Result.err(new BadInputException("unknown currency", Details.of("text", code)));
            }
            currency = Currency.valueOf(upper);
        } else if (symbol != null) {
            currency = Currency.fromSymbol(symbol).orElse(fallback);
        }
        double value = Numbers.parse(digits.replace(",", ""));
        if (!Double.isFinite(value)) {
            return Result.err(new BadInputException("rate amount is not a number", Details.of("text", trimmed)));
        }
        return Result.ok(of("-".equals(sign) ? -value : value, currency, unit));
    }

    /** {@link #tryParse} that throws its failure. */
    public static Rate parse(String text, Currency fallback) {
        return Result.unwrap(tryParse(text, fallback));
    }

    /** {@link #parse(String, Currency)} in sterling unless the text says otherwise. */
    public static Rate parse(String text) {
        return parse(text, Currency.GBP);
    }

    /** The rate as plain money per unit, rounded to the penny, for reports that want one. */
    public Money asMoneyPerUnit() {
        double scaled = (double) milliMinor / RATE_SCALE;
        return Money.of((double) Numbers.roundHalfAway(scaled), currency);
    }

    /** {@code 12.3400 GBP per m3}: four places, for a log line or an audit note. */
    public String describe() {
        return Numbers.toFixed(major(), 4) + " " + currency.code() + " per " + unit.code();
    }
}
