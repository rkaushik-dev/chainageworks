package io.github.rkaushikdev.chainageworks.ingest;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.Result;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.io.Csv;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.util.Patterns;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Field readers shared by every importer.
 *
 * <p>They throw rather than returning a result, and the importer catches once per row. That is the
 * opposite of the rule everywhere else in this package, and it is right here: a row has ten fields,
 * any one of them can be wrong, and threading a result through ten of them turns a readable
 * constructor call into a staircase.
 *
 * <p>Every failure carries the line and the column, so the catch can turn it straight into an
 * import issue that names where to look.
 */
final class Fields {

    private static final Pattern SEPARATORS = Patterns.compile("[\\s-]+");
    private static final List<String> YES = List.of("y", "yes", "true", "1");
    private static final List<String> NO = List.of("n", "no", "false", "0");

    private Fields() {}

    private static BadInputException fail(Csv.RowReader reader, String column, String message) {
        return new BadInputException(message, Details.of("line", reader.line(), "column", column));
    }

    private static <T> T orFail(Result<T, CwException> parsed, Csv.RowReader reader, String column) {
        if (parsed.isErr()) {
            throw fail(reader, column, parsed.error().getMessage());
        }
        return parsed.value();
    }

    /** The value in a column, failing when it is missing or blank. */
    public static String need(Csv.RowReader reader, String column) {
        return Result.unwrap(reader.require(column));
    }

    /** The value in a column, if it has one. */
    public static Optional<String> optional(Csv.RowReader reader, String column) {
        return reader.get(column);
    }

    public static ItemCode needCode(Csv.RowReader reader, String column) {
        return orFail(ItemCode.tryParse(need(reader, column)), reader, column);
    }

    public static long needStation(Csv.RowReader reader, String column) {
        return orFail(Stations.tryParse(need(reader, column)), reader, column);
    }

    public static LocalDate needDate(Csv.RowReader reader, String column) {
        return orFail(SiteDates.tryParse(need(reader, column)), reader, column);
    }

    /** A date in a column that may be blank. */
    public static Optional<LocalDate> optionalDate(Csv.RowReader reader, String column) {
        Optional<String> text = reader.get(column);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(orFail(SiteDates.tryParse(text.get()), reader, column));
    }

    /** A number, thousands commas allowed. */
    public static double needNumber(Csv.RowReader reader, String column) {
        double value = Numbers.parse(need(reader, column).replace(",", ""));
        if (!Double.isFinite(value)) {
            throw fail(reader, column, "the " + column + " is not a number");
        }
        return value;
    }

    /**
     * A whole number: a count, a period number, a year or a month. A fraction is refused here, at
     * the row, rather than being carried into a register that counts in whole units.
     */
    public static int needWholeNumber(Csv.RowReader reader, String column) {
        double value = needNumber(reader, column);
        if (!Numbers.isInteger(value) || Math.abs(value) > Integer.MAX_VALUE) {
            throw fail(reader, column, "the " + column + " is not a whole number");
        }
        return (int) value;
    }

    /** An amount and a unit from two columns. */
    public static Quantity needQuantity(Csv.RowReader reader, String amountColumn, String unitColumn) {
        try {
            return Quantity.of(needNumber(reader, amountColumn), Unit.parse(need(reader, unitColumn)));
        } catch (BadInputException refused) {
            if (!refused.details().containsKey("line")) {
                throw fail(reader, unitColumn, refused.getMessage());
            }
            throw refused;
        }
    }

    public static Money needMoney(Csv.RowReader reader, String column, Currency currency) {
        return orFail(Money.tryParse(need(reader, column), currency), reader, column);
    }

    /** A chainage range from the {@code from} and {@code to} columns. */
    public static ChainageRange needExtent(Csv.RowReader reader) {
        return needExtent(reader, "from", "to");
    }

    /** A chainage range from two columns. */
    public static ChainageRange needExtent(Csv.RowReader reader, String fromColumn, String toColumn) {
        return ChainageRange.of(needStation(reader, fromColumn), needStation(reader, toColumn));
    }

    /** A yes or no column. Anything unrecognised is a failure, not a silent false. */
    public static boolean needFlag(Csv.RowReader reader, String column, boolean fallback) {
        Optional<String> text = reader.get(column);
        if (text.isEmpty()) {
            return fallback;
        }
        String cleaned = Text.lower(Text.trim(text.get()));
        if (YES.contains(cleaned)) {
            return true;
        }
        if (NO.contains(cleaned)) {
            return false;
        }
        throw fail(reader, column, text.get() + " is not a yes or a no");
    }

    /**
     * One of a fixed set of words, matched loosely on case and separators: {@code Passed With
     * Comment} reads as {@code passed_with_comment}.
     */
    public static String needOneOf(Csv.RowReader reader, String column, List<String> allowed) {
        String text = need(reader, column);
        String cleaned = SEPARATORS.matcher(Text.lower(Text.trim(text))).replaceAll("_");
        if (!allowed.contains(cleaned)) {
            throw fail(reader, column, text + " is not one of " + String.join(", ", allowed));
        }
        return cleaned;
    }
}
