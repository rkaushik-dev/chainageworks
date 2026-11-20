package io.github.rkaushikdev.chainageworks.domain.money;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.Result;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.util.Patterns;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An amount of money: a whole number of minor units.
 *
 * <p>Nothing in a valuation is ever priced in fractions of a penny: rates carry the extra places,
 * and the extension to money is where they are rounded away, once, on purpose.
 *
 * @param minor the amount in minor units, pence for sterling
 * @param currency what it is counted in
 */
public record Money(long minor, Currency currency) implements Comparable<Money> {

    private static final Pattern GROUPS = Pattern.compile("\\B(?=(\\d{3})+(?!\\d))");
    private static final Pattern MONEY_TEXT =
            Patterns.compile("^(-?)\\s*([£€$])?\\s*(-?[\\d,]+(?:\\.\\d+)?)\\s*([A-Za-z]{3})?$");

    /** The three currencies, in the order the contracts list them. */
    public static final List<Currency> ALL_CURRENCIES = List.of(Currency.values());

    /** Money from minor units. */
    public static Money of(long minor, Currency currency) {
        return new Money(minor, currency);
    }

    /**
     * Money from minor units held as a double, as a stored document carries them. Refuses
     * anything that is not finite or not whole.
     */
    public static Money of(double minor, Currency currency) {
        if (!Double.isFinite(minor)) {
            throw new BadInputException("money must be a finite number", Details.of("value", Numbers.format(minor)));
        }
        if (!Numbers.isInteger(minor)) {
            throw new BadInputException("money must be a whole number of minor units", Details.of("value", minor));
        }
        return new Money((long) minor, currency);
    }

    /** Money from major units, rounded to the penny half away from zero. */
    public static Money fromMajor(double major, Currency currency) {
        if (!Double.isFinite(major)) {
            throw new BadInputException("money must be a finite number", Details.of("value", Numbers.format(major)));
        }
        double scaled = major * currency.minorUnitsPerMajor();
        return of((double) Numbers.roundHalfAway(scaled), currency);
    }

    /** Nothing, in {@code currency}. */
    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    /** The amount in major units: pounds rather than pence. */
    public double major() {
        return (double) minor / currency.minorUnitsPerMajor();
    }

    public boolean isZero() {
        return minor == 0;
    }

    public boolean isNegative() {
        return minor < 0;
    }

    /** Refuses two different currencies. */
    public static void assertSameCurrency(Currency a, Currency b) {
        if (a != b) {
            throw new BadInputException(
                    "cannot combine money in different currencies", Details.of("left", a.code(), "right", b.code()));
        }
    }

    public Money plus(Money other) {
        assertSameCurrency(currency, other.currency);
        return new Money(minor + other.minor, currency);
    }

    public Money minus(Money other) {
        assertSameCurrency(currency, other.currency);
        return new Money(minor - other.minor, currency);
    }

    public Money negate() {
        return new Money(-minor, currency);
    }

    public Money abs() {
        return new Money(Math.abs(minor), currency);
    }

    /** Percentages and retention factors land here. Rounds half away from zero. */
    public Money scale(double factor) {
        if (!Double.isFinite(factor)) {
            throw new BadInputException(
                    "scale factor must be a finite number", Details.of("value", Numbers.format(factor)));
        }
        return new Money(Numbers.roundHalfAway(minor * factor), currency);
    }

    /** Negative, zero or positive as this is less than, equal to or more than {@code other}. */
    @Override
    public int compareTo(Money other) {
        assertSameCurrency(currency, other.currency);
        return Long.compare(minor, other.minor);
    }

    /** The smaller of two amounts, the first on a tie. */
    public static Money min(Money a, Money b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** The larger of two amounts, the first on a tie. */
    public static Money max(Money a, Money b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** The total of {@code parts}, which all have to be in {@code currency}. */
    public static Money sum(Iterable<Money> parts, Currency currency) {
        long minor = 0;
        for (Money part : parts) {
            assertSameCurrency(currency, part.currency);
            minor += part.minor;
        }
        return new Money(minor, currency);
    }

    /** {@code £1,234.56}: the symbol, the major units grouped in threes, two places. */
    public String format() {
        return format(true, true);
    }

    /**
     * The amount written out. {@code symbol} false writes the code after the amount instead of the
     * symbol in front ({@code 1,234.56 EUR}); {@code grouped} false drops the thousands commas.
     */
    public String format(boolean symbol, boolean grouped) {
        int digits = currency.minorDigits();
        boolean negative = minor < 0;
        long magnitude = Math.abs(minor);
        long per = currency.minorUnitsPerMajor();
        long major = magnitude / per;
        long rest = magnitude - major * per;
        String majorText = grouped ? GROUPS.matcher(Long.toString(major)).replaceAll(",") : Long.toString(major);
        String body = majorText + "." + Text.padStart(Long.toString(rest), digits, '0');
        if (!symbol) {
            return (negative ? "-" : "") + body + " " + currency.code();
        }
        return (negative ? "-" : "") + currency.symbol() + body;
    }

    /**
     * Read an amount with an optional currency: {@code 1,234.56}, {@code -£10.00}, {@code £-10.00},
     * {@code 10.00 usd}. Without a symbol or a code the amount is in {@code fallback}.
     */
    public static Result<Money, CwException> tryParse(String text, Currency fallback) {
        String trimmed = Text.trim(text);
        Matcher m = MONEY_TEXT.matcher(trimmed);
        if (!m.find()) {
            return Result.err(new BadInputException(
                    "money must be an amount with an optional currency", Details.of("text", trimmed)));
        }
        String leadingSign = m.group(1);
        String symbol = m.group(2);
        String digits = m.group(3);
        String code = m.group(4);
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
            return Result.err(new BadInputException("money amount is not a number", Details.of("text", trimmed)));
        }
        return Result.ok(fromMajor("-".equals(leadingSign) ? -value : value, currency));
    }

    /** {@link #tryParse} that throws its failure. */
    public static Money parse(String text, Currency fallback) {
        return Result.unwrap(tryParse(text, fallback));
    }

    /** {@link #parse(String, Currency)} in sterling unless the text says otherwise. */
    public static Money parse(String text) {
        return parse(text, Currency.GBP);
    }
}
