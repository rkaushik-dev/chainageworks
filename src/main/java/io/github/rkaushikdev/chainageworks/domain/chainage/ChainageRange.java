package io.github.rkaushikdev.chainageworks.domain.chainage;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.Result;
import io.github.rkaushikdev.chainageworks.util.Patterns;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stretch of the alignment, held half open as {@code [from, to)} in millimetres.
 *
 * <p>Half open is the only choice that makes the bill add up. Two abutting pipe runs written
 * {@code 12+000 to 12+100} and {@code 12+100 to 12+200} are 200 m of pipe, not 200 m plus one
 * shared metre, and a closed interval would have to special case the join every time two claims are
 * summed.
 *
 * <p>The canonical constructor takes the ends as given; {@link #of} refuses a range that runs
 * backwards.
 *
 * @param from the first station, inside the range
 * @param to the last station, just outside it
 */
public record ChainageRange(long from, long to) implements Comparable<ChainageRange> {

    /** Start, then end. */
    public static final Comparator<ChainageRange> ORDER =
            Comparator.comparingLong(ChainageRange::from).thenComparingLong(ChainageRange::to);

    private static final Pattern RANGE_TEXT = Patterns.compile("^(.+?)\\s*(?:\\bto\\b|--|–|→)\\s*(.+)$", "i");

    /** The range, refusing one that runs backwards. */
    public static ChainageRange of(long from, long to) {
        if (from > to) {
            throw backwards(from, to);
        }
        return new ChainageRange(from, to);
    }

    /** A point: a range that starts and ends on one station. */
    public static ChainageRange point(long station) {
        return new ChainageRange(station, station);
    }

    private static BadInputException backwards(long from, long to) {
        return new BadInputException(
                "a chainage range runs forwards", Details.of("from", Stations.format(from), "to", Stations.format(to)));
    }

    /** {@code 12+000 to 12+340}, {@code 12+000 -- 12+340}. A single station gives a point. */
    public static Result<ChainageRange, CwException> tryParse(String text) {
        String trimmed = Text.trim(text);
        Matcher m = RANGE_TEXT.matcher(trimmed);
        if (!m.find()) {
            return Stations.tryParse(trimmed).map(ChainageRange::point);
        }
        Result<Long, CwException> from = Stations.tryParse(m.group(1));
        if (from.isErr()) {
            return Result.err(from.error());
        }
        Result<Long, CwException> to = Stations.tryParse(m.group(2));
        if (to.isErr()) {
            return Result.err(to.error());
        }
        if (from.value() > to.value()) {
            return Result.err(backwards(from.value(), to.value()));
        }
        return Result.ok(new ChainageRange(from.value(), to.value()));
    }

    /** {@link #tryParse} that throws its failure. */
    public static ChainageRange parse(String text) {
        return Result.unwrap(tryParse(text));
    }

    /** {@code 12+000.000 to 12+340.000}, or the one station for a point. */
    public String format() {
        if (isPoint()) {
            return Stations.format(from);
        }
        return Stations.format(from) + " to " + Stations.format(to);
    }

    /** Length in millimetres. A point measures zero. */
    public long length() {
        return to - from;
    }

    public boolean isPoint() {
        return from == to;
    }

    /** Half open, so the upper station is outside the range. */
    public boolean contains(long station) {
        return station >= from && station < to;
    }

    /** Closed test, for asking whether a point sits on the end of a stretch. */
    public boolean touches(long station) {
        return station >= from && station <= to;
    }

    /** True when the two share length; abutting stretches do not. */
    public boolean overlaps(ChainageRange other) {
        return Math.max(from, other.from) < Math.min(to, other.to);
    }

    /** True when every station of {@code inner} is inside this range. */
    public boolean covers(ChainageRange inner) {
        return from <= inner.from && to >= inner.to;
    }

    /** The stretch both ranges share, if they share any length. */
    public Optional<ChainageRange> intersect(ChainageRange other) {
        long start = Math.max(from, other.from);
        long end = Math.min(to, other.to);
        return start < end ? Optional.of(new ChainageRange(start, end)) : Optional.empty();
    }

    /** This range with everything {@code cut} covers taken out. Gives nought, one or two pieces. */
    public List<ChainageRange> subtract(ChainageRange cut) {
        if (!overlaps(cut)) {
            return isPoint() ? List.of() : List.of(this);
        }
        List<ChainageRange> out = new ArrayList<>(2);
        if (from < cut.from) {
            out.add(new ChainageRange(from, cut.from));
        }
        if (cut.to < to) {
            out.add(new ChainageRange(cut.to, to));
        }
        return List.copyOf(out);
    }

    @Override
    public int compareTo(ChainageRange other) {
        return ORDER.compare(this, other);
    }

    /**
     * Sort and merge, so overlapping or abutting stretches collapse into one. A point that falls on
     * or inside an existing stretch disappears into it, which is right: it is already covered. A
     * point on its own survives as a point. The input is left alone.
     */
    public static List<ChainageRange> union(List<ChainageRange> ranges) {
        List<ChainageRange> sorted = new ArrayList<>(ranges);
        sorted.sort(ORDER);
        List<ChainageRange> out = new ArrayList<>();
        for (ChainageRange range : sorted) {
            ChainageRange last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && range.from <= last.to) {
                out.set(out.size() - 1, new ChainageRange(last.from, Math.max(last.to, range.to)));
            } else {
                out.add(range);
            }
        }
        return List.copyOf(out);
    }

    /** Total covered length, counting any overlap once. */
    public static long coveredLength(List<ChainageRange> ranges) {
        long total = 0;
        for (ChainageRange range : union(ranges)) {
            total += range.length();
        }
        return total;
    }

    /** Cut at a station. A cut outside the range, or on its start, leaves it whole. */
    public List<ChainageRange> splitAt(long station) {
        if (!contains(station) || station == from) {
            return List.of(this);
        }
        return List.of(new ChainageRange(from, station), new ChainageRange(station, to));
    }

    /**
     * The stretches two lists of ranges have in common.
     *
     * <p>Both sides are normalised first, so a caller can pass overlapping ranges without the
     * intersection counting the shared length twice.
     */
    public static List<ChainageRange> intersectLists(List<ChainageRange> left, List<ChainageRange> right) {
        List<ChainageRange> a = union(left);
        List<ChainageRange> b = union(right);
        List<ChainageRange> out = new ArrayList<>();
        for (ChainageRange one : a) {
            for (ChainageRange other : b) {
                one.intersect(other).ifPresent(out::add);
            }
        }
        return union(out);
    }
}
