package io.github.rkaushikdev.chainageworks.domain.time;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.Result;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Days on site.
 *
 * <p>A site date is a {@link LocalDate}, deliberately not a timestamp. A site diary entry, a
 * valuation cut-off and an inspection sign-off are all dated, never timed, and giving them an
 * instant would drag a timezone into every comparison. The one place a clock time matters is the
 * audit trail on a record, and that is a separate field.
 *
 * <p>A date is made only for the years 1900 to 2200, which is the range any contract here can run
 * in; arithmetic on one that exists is not limited.
 */
public final class SiteDates {

    /** The year a stored day count starts from: day 0 is 1970-01-01. */
    public static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);

    private static final Pattern DATE_TEXT = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");

    private SiteDates() {}

    /** The year, month and day of a date. */
    public record DateParts(int year, int month, int day) {}

    static int daysInMonth(int year, int month) {
        if (month == 2) {
            boolean leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
            return leap ? 29 : 28;
        }
        return month == 4 || month == 6 || month == 9 || month == 11 ? 30 : 31;
    }

    /** The date, refusing a year outside 1900 to 2200 and a day the month does not have. */
    public static LocalDate of(int year, int month, int day) {
        if (year < 1900 || year > 2200) {
            throw new BadInputException("year is outside the range this contract can run in", Details.of("year", year));
        }
        if (month < 1 || month > 12) {
            throw new BadInputException("month must be 1 to 12", Details.of("month", month));
        }
        int limit = daysInMonth(year, month);
        if (day < 1 || day > limit) {
            throw new BadInputException(
                    "day is not in that month", Details.of("year", year, "month", month, "day", day, "limit", limit));
        }
        return LocalDate.of(year, month, day);
    }

    /** The date {@code days} after 1970-01-01, which is how a date is counted. */
    public static LocalDate ofEpochDay(long days) {
        return LocalDate.ofEpochDay(days);
    }

    /** Days since 1970-01-01. */
    public static long epochDay(LocalDate date) {
        return date.toEpochDay();
    }

    public static DateParts parts(LocalDate date) {
        return new DateParts(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    /** Read {@code yyyy-mm-dd}, surrounding white space ignored. */
    public static Result<LocalDate, CwException> tryParse(String text) {
        String trimmed = Text.trim(text);
        Matcher m = DATE_TEXT.matcher(trimmed);
        if (!m.matches()) {
            return Result.err(new BadInputException("a site date is written yyyy-mm-dd", Details.of("text", trimmed)));
        }
        try {
            return Result.ok(
                    of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
        } catch (BadInputException refused) {
            return Result.err(refused);
        }
    }

    /** {@link #tryParse} that throws its failure. */
    public static LocalDate parse(String text) {
        return Result.unwrap(tryParse(text));
    }

    /** {@code yyyy-mm-dd}. */
    public static String format(LocalDate date) {
        return Text.padStart(Integer.toString(date.getYear()), 4, '0')
                + "-"
                + Text.padStart(Integer.toString(date.getMonthValue()), 2, '0')
                + "-"
                + Text.padStart(Integer.toString(date.getDayOfMonth()), 2, '0');
    }

    public static LocalDate addDays(LocalDate date, int days) {
        return date.plusDays(days);
    }

    /** Days from the first to the second. Negative when they are the other way. */
    public static int diffDays(LocalDate from, LocalDate to) {
        return Math.toIntExact(to.toEpochDay() - from.toEpochDay());
    }

    /** Negative, zero or positive as {@code a} is before, on or after {@code b}. */
    public static int compare(LocalDate a, LocalDate b) {
        return Long.compare(a.toEpochDay(), b.toEpochDay());
    }

    /** The earlier of two dates, the first on a tie. */
    public static LocalDate min(LocalDate a, LocalDate b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** The later of two dates, the first on a tie. */
    public static LocalDate max(LocalDate a, LocalDate b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** ISO weekday, so Monday is 1 and Sunday is 7. */
    public static int dayOfWeek(LocalDate date) {
        return date.getDayOfWeek().getValue();
    }

    public static boolean isWeekend(LocalDate date) {
        return dayOfWeek(date) >= 6;
    }

    public static LocalDate startOfMonth(LocalDate date) {
        return of(date.getYear(), date.getMonthValue(), 1);
    }

    public static LocalDate endOfMonth(LocalDate date) {
        return of(date.getYear(), date.getMonthValue(), daysInMonth(date.getYear(), date.getMonthValue()));
    }

    /** Step whole months, clamping onto the last day when the target is short. */
    public static LocalDate addMonths(LocalDate date, int months) {
        int zeroBased = date.getYear() * 12 + (date.getMonthValue() - 1) + months;
        int targetYear = Math.floorDiv(zeroBased, 12);
        int targetMonth = zeroBased - targetYear * 12 + 1;
        int limit = daysInMonth(targetYear, targetMonth);
        return of(targetYear, targetMonth, Math.min(date.getDayOfMonth(), limit));
    }

    public static boolean isLastDayOfMonth(LocalDate date) {
        return date.equals(endOfMonth(date));
    }
}
