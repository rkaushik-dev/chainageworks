package io.github.rkaushikdev.chainageworks.domain.time;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.util.Patterns;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A run of days, inclusive at both ends.
 *
 * <p>This is the opposite convention to a chainage range, and on purpose. A valuation period is
 * written "1 to 31 March" and means both of those days; writing it half open would put the last
 * day of the month outside the month. Lengths are counted in days, so there is no join to double
 * count.
 *
 * <p>The canonical constructor takes the ends as given; {@link #of} refuses a range that runs
 * backwards.
 *
 * @param from the first day
 * @param to the last day
 */
public record DateRange(LocalDate from, LocalDate to) implements Comparable<DateRange> {

    /** Start, then end. */
    public static final Comparator<DateRange> ORDER =
            Comparator.comparing(DateRange::from).thenComparing(DateRange::to);

    private static final Pattern RANGE_TEXT = Patterns.compile("^(.+?)\\s*(?:\\bto\\b|\\.\\.|--)\\s*(.+)$", "i");

    /** The range, refusing one that ends before it starts. */
    public static DateRange of(LocalDate from, LocalDate to) {
        if (SiteDates.compare(from, to) > 0) {
            throw new BadInputException(
                    "a date range ends on or after it starts",
                    Details.of("from", SiteDates.format(from), "to", SiteDates.format(to)));
        }
        return new DateRange(from, to);
    }

    public static DateRange singleDay(LocalDate date) {
        return new DateRange(date, date);
    }

    /** Whole calendar month, which is what an interim valuation period usually is. */
    public static DateRange month(int year, int month) {
        LocalDate first = SiteDates.of(year, month, 1);
        return new DateRange(first, SiteDates.endOfMonth(first));
    }

    /** The whole month {@code date} falls in. */
    public static DateRange monthOf(LocalDate date) {
        return new DateRange(SiteDates.startOfMonth(date), SiteDates.endOfMonth(date));
    }

    /** {@code 2025-03-01 to 2025-03-31}, or the one date for a single day. */
    public String format() {
        if (from.equals(to)) {
            return SiteDates.format(from);
        }
        return SiteDates.format(from) + " to " + SiteDates.format(to);
    }

    /** Read {@code a to b}, {@code a .. b}, {@code a -- b}, or one date for a single day. */
    public static DateRange parse(String text) {
        String trimmed = Text.trim(text);
        Matcher m = RANGE_TEXT.matcher(trimmed);
        if (!m.find()) {
            return singleDay(SiteDates.parse(trimmed));
        }
        LocalDate first = SiteDates.parse(m.group(1));
        LocalDate last = SiteDates.parse(m.group(2));
        return of(first, last);
    }

    /** Days in the range, counting both ends. A single day is one day. */
    public int days() {
        return SiteDates.diffDays(from, to) + 1;
    }

    public boolean contains(LocalDate date) {
        return SiteDates.compare(date, from) >= 0 && SiteDates.compare(date, to) <= 0;
    }

    /** True when the two ranges share at least one day. */
    public boolean overlaps(DateRange other) {
        return SiteDates.max(from, other.from).compareTo(SiteDates.min(to, other.to)) <= 0;
    }

    /** True when every day of {@code inner} is in this range. */
    public boolean covers(DateRange inner) {
        return SiteDates.compare(from, inner.from) <= 0 && SiteDates.compare(to, inner.to) >= 0;
    }

    /** The days both ranges share, if any. */
    public Optional<DateRange> intersect(DateRange other) {
        LocalDate start = SiteDates.max(from, other.from);
        LocalDate end = SiteDates.min(to, other.to);
        return SiteDates.compare(start, end) <= 0 ? Optional.of(new DateRange(start, end)) : Optional.empty();
    }

    @Override
    public int compareTo(DateRange other) {
        return ORDER.compare(this, other);
    }

    /** Sort and merge, treating consecutive days as continuous. The input is left alone. */
    public static List<DateRange> union(List<DateRange> ranges) {
        List<DateRange> sorted = new ArrayList<>(ranges);
        sorted.sort(ORDER);
        List<DateRange> out = new ArrayList<>();
        for (DateRange range : sorted) {
            DateRange last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && SiteDates.compare(range.from, last.to.plusDays(1)) <= 0) {
                out.set(out.size() - 1, new DateRange(last.from, SiteDates.max(last.to, range.to)));
            } else {
                out.add(range);
            }
        }
        return List.copyOf(out);
    }

    /** Every day of the range, first to last. */
    public Stream<LocalDate> eachDate() {
        return Stream.iterate(from, day -> !day.isAfter(to), day -> day.plusDays(1));
    }

    /** Every day of the range as a list. */
    public List<LocalDate> dates() {
        return eachDate().toList();
    }

    /**
     * Cut the range at every month boundary. A progress period that straddles a month has to be
     * reported against each one, because the bill revision in force can change on the first of a
     * month.
     */
    public List<DateRange> splitByMonth() {
        List<DateRange> out = new ArrayList<>();
        LocalDate cursor = from;
        while (SiteDates.compare(cursor, to) <= 0) {
            LocalDate monthEnd = SiteDates.endOfMonth(cursor);
            LocalDate end = SiteDates.min(monthEnd, to);
            out.add(new DateRange(cursor, end));
            cursor = end.plusDays(1);
        }
        return List.copyOf(out);
    }

    /** {@code 2025-03}: the month the range starts in, for a report heading. */
    public String describeMonth() {
        return from.getYear() + "-" + Text.padStart(Integer.toString(from.getMonthValue()), 2, '0');
    }
}
