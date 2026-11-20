package io.github.rkaushikdev.chainageworks.domain.fluctuation;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A published cost index, month by month.
 *
 * @param id the series' key
 * @param name what it is called
 * @param readings the figures, oldest first
 */
public record IndexSeries(String id, String name, List<IndexReading> readings) {

    public IndexSeries {
        readings = List.copyOf(readings);
    }

    private static int monthKey(int year, int month) {
        return year * 12 + (month - 1);
    }

    /**
     * Build a series, refusing a month outside 1 to 12, a figure of nothing or less, and two
     * figures for one month.
     *
     * @param name the name, or null to use the id
     */
    public static IndexSeries build(String id, String name, List<IndexReading> readings) {
        Set<Integer> seen = new HashSet<>();
        for (IndexReading reading : readings) {
            if (reading.month() < 1 || reading.month() > 12) {
                throw new BadInputException(
                        "an index reading is filed against a month of the year",
                        Details.of("series", id, "month", reading.month()));
            }
            if (!Double.isFinite(reading.value()) || reading.value() <= 0) {
                throw new BadInputException(
                        "an index figure has to be more than nothing",
                        Details.of("series", id, "value", reading.value()));
            }
            if (!seen.add(monthKey(reading.year(), reading.month()))) {
                throw new BadInputException(
                        "the series has two figures for one month",
                        Details.of("series", id, "year", reading.year(), "month", reading.month()));
            }
        }
        List<IndexReading> sorted = new ArrayList<>(readings);
        sorted.sort(Comparator.comparingInt(r -> monthKey(r.year(), r.month())));
        return new IndexSeries(id, name == null ? id : name, sorted);
    }

    /**
     * The figure in force for a date.
     *
     * <p>The reading for the month itself is used when it exists, and the last one before it
     * otherwise. Indices are published in arrears, so the month a valuation falls in usually has no
     * figure yet, and carrying the last published one forward is what the contract says to do
     * rather than a convenience.
     */
    public Optional<IndexReading> readingAt(LocalDate date) {
        int wanted = monthKey(date.getYear(), date.getMonthValue());
        IndexReading found = null;
        for (IndexReading reading : readings) {
            if (monthKey(reading.year(), reading.month()) > wanted) {
                break;
            }
            found = reading;
        }
        return Optional.ofNullable(found);
    }

    /** The figure in force for a date, refusing a date the series does not reach back to. */
    public double valueAt(LocalDate date) {
        return readingAt(date)
                .orElseThrow(() -> new NotFoundException("index reading", id + " at " + SiteDates.format(date)))
                .value();
    }

    /** True when the figure that would be used has not been firmed up yet. */
    public boolean isProvisionalAt(LocalDate date) {
        return readingAt(date).map(IndexReading::provisional).orElse(false);
    }

    public Optional<IndexReading> latestReading() {
        return readings.isEmpty() ? Optional.empty() : Optional.of(readings.get(readings.size() - 1));
    }

    /** Months of the series that are still provisional, oldest first. */
    public List<IndexReading> provisionalMonths() {
        return readings.stream().filter(IndexReading::provisional).toList();
    }

    public static Optional<IndexSeries> find(List<IndexSeries> all, String id) {
        return all.stream().filter(series -> series.id().equals(id)).findFirst();
    }

    public static IndexSeries require(List<IndexSeries> all, String id) {
        return find(all, id).orElseThrow(() -> new NotFoundException("index series", id));
    }

    /**
     * How far the index has moved since a base date, as a fraction of one.
     *
     * <p>Returned as a fraction rather than a percentage because it is multiplied by a weighting
     * straight afterwards, and the two conventions do not survive being mixed. A tenth of a percent
     * lost here is thousands of pounds on a contract sum.
     */
    public double movementSince(LocalDate base, LocalDate asAt) {
        if (asAt.isBefore(base)) {
            throw new BadInputException(
                    "an index cannot be read before the base date",
                    Details.of("series", id, "base", SiteDates.format(base), "asAt", SiteDates.format(asAt)));
        }
        double from = valueAt(base);
        return (valueAt(asAt) - from) / from;
    }

    /** {@code IDX-LAB name, latest 105 for 2025-03, provisional}. */
    public String describe() {
        Optional<IndexReading> latest = latestReading();
        if (latest.isEmpty()) {
            return id + " " + name + ", no figures published";
        }
        IndexReading last = latest.get();
        String state = last.provisional() ? "provisional" : "firm";
        return id + " " + name + ", latest " + Numbers.format(last.value()) + " for " + last.year() + "-"
                + Text.padStart(Integer.toString(last.month()), 2, '0') + ", " + state;
    }
}
