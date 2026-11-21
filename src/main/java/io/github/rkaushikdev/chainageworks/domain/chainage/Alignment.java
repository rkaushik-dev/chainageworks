package io.github.rkaushikdev.chainageworks.domain.chainage;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.Details;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The line the works are set out along: a start and end station, and the chainage equations where
 * the stationing stops being continuous.
 *
 * <p>Built through {@link #build}, which is where the equations are checked: building the segments
 * is the only place that can tell an equation that advances from one that folds the route back on
 * itself.
 *
 * @param id the alignment's key
 * @param name what people call it
 * @param start the first station
 * @param end the last station
 * @param equations the breaks in the stationing, in order along the ground
 */
public record Alignment(String id, String name, long start, long end, List<ChainageEquation> equations) {

    public Alignment {
        equations = equations == null ? List.of() : List.copyOf(equations);
    }

    /**
     * A point where the through chainage stops being continuous.
     *
     * <p>When a route is realigned after the bill is written, nobody restations the whole job. The
     * designer writes an equation instead: the last station of the old series equals the first
     * station of the new one at the same physical point. {@code 12+340.000 back = 12+310.000 ahead}
     * means the two labels name one place, and thirty metres of chainage exist twice.
     *
     * @param back last station of the outgoing series
     * @param ahead first station of the incoming series, at the same place on the ground
     */
    public record ChainageEquation(long back, long ahead) {

        /** Whether the equation repeats chainage, skips it, or changes nothing. */
        public EquationKind kind() {
            int delta = Long.compare(ahead, back);
            if (delta < 0) {
                return EquationKind.OVERLAP;
            }
            if (delta > 0) {
                return EquationKind.GAP;
            }
            return EquationKind.CONTINUOUS;
        }

        /** {@code 12+340.000 back = 12+310.000 ahead}: the surveyor's form. */
        public String describe() {
            return Stations.format(back) + " back = " + Stations.format(ahead) + " ahead";
        }
    }

    /** The three shapes an equation can take. */
    public enum EquationKind {
        OVERLAP("overlap"),
        GAP("gap"),
        CONTINUOUS("continuous");

        private final String wire;

        EquationKind(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        @Override
        public String toString() {
            return wire;
        }
    }

    /**
     * One continuous run of stationing, with where it begins on the ground.
     *
     * @param series zero based series number; series 0 is the stationing before the first equation
     * @param from its first station
     * @param to its last station
     * @param startDistance millimetres along the ground from the start of the alignment
     */
    public record Segment(int series, long from, long to, long startDistance) {}

    /**
     * Where a station label lands.
     *
     * @param series the series it is read in
     * @param distance millimetres along the ground from the start
     */
    public record Position(int series, long distance) {}

    /**
     * The label at a distance along the ground.
     *
     * @param station the station label
     * @param series the series it is in
     */
    public record Located(long station, int series) {}

    /** An alignment with no equations. */
    public static Alignment build(String id, String name, long start, long end) {
        return build(id, name, start, end, List.of());
    }

    /** Build an alignment, refusing equations that do not advance and an end before the last series. */
    public static Alignment build(String id, String name, long start, long end, List<ChainageEquation> equations) {
        Alignment alignment = new Alignment(id, name, start, end, equations);
        alignment.segments();
        return alignment;
    }

    /** The continuous runs of stationing, in order along the ground. */
    public List<Segment> segments() {
        List<Segment> segments = new ArrayList<>();
        long from = start;
        long distance = 0;
        for (int index = 0; index < equations.size(); index++) {
            ChainageEquation equation = equations.get(index);
            if (equation.back() <= from) {
                throw new BadInputException(
                        "a chainage equation must sit ahead of the series it closes",
                        Details.of(
                                "equation", equation.describe(),
                                "seriesStart", Stations.format(from),
                                "index", index));
            }
            long length = equation.back() - from;
            segments.add(new Segment(index, from, equation.back(), distance));
            distance += length;
            from = equation.ahead();
        }
        if (end <= from) {
            throw new BadInputException(
                    "the alignment ends at or before the start of its last series",
                    Details.of("end", Stations.format(end), "seriesStart", Stations.format(from)));
        }
        segments.add(new Segment(equations.size(), from, end, distance));
        return List.copyOf(segments);
    }

    /** Real distance on the ground from one end to the other, in millimetres. */
    public long length() {
        List<Segment> segments = segments();
        Segment last = segments.get(segments.size() - 1);
        return last.startDistance() + (last.to() - last.from());
    }

    /**
     * Every place a station label lands. Both ends of a segment count, so the two labels either
     * side of an equation resolve to the same distance and a continuous equation is not reported
     * as ambiguous.
     */
    public List<Position> locate(long station) {
        List<Position> out = new ArrayList<>();
        for (Segment segment : segments()) {
            if (station >= segment.from() && station <= segment.to()) {
                out.add(new Position(segment.series(), segment.startDistance() + (station - segment.from())));
            }
        }
        return List.copyOf(out);
    }

    public boolean isOnAlignment(long station) {
        return !locate(station).isEmpty();
    }

    /**
     * A station is ambiguous when an overlap equation makes the same label name two different
     * places. The caller has to say which series it meant.
     */
    public boolean isAmbiguous(long station) {
        return distinctDistances(locate(station)).size() > 1;
    }

    private static Set<Long> distinctDistances(List<Position> positions) {
        Set<Long> distances = new LinkedHashSet<>();
        for (Position position : positions) {
            distances.add(position.distance());
        }
        return distances;
    }

    /** Distance along the ground to {@code station}, refusing one that names two places. */
    public long distanceAt(long station) {
        List<Position> positions = located(station);
        Set<Long> distances = distinctDistances(positions);
        if (distances.size() > 1) {
            throw new ConflictException(
                    "station is ambiguous, name the series",
                    Details.of(
                            "alignment", id,
                            "station", Stations.format(station),
                            "series",
                                    positions.stream()
                                            .map(p -> Integer.toString(p.series()))
                                            .collect(Collectors.joining(","))));
        }
        return distances.iterator().next();
    }

    /** Distance along the ground to {@code station} read in {@code series}. */
    public long distanceAt(long station, int series) {
        Optional<Position> wanted =
                located(station).stream().filter(p -> p.series() == series).findFirst();
        if (wanted.isEmpty()) {
            throw new BadInputException(
                    "station is not in that series",
                    Details.of("alignment", id, "station", Stations.format(station), "series", series));
        }
        return wanted.get().distance();
    }

    private List<Position> located(long station) {
        List<Position> positions = locate(station);
        if (positions.isEmpty()) {
            throw new BadInputException(
                    "station is not on this alignment",
                    Details.of("alignment", id, "station", Stations.format(station)));
        }
        return positions;
    }

    /** Ground distance between two labels. Negative when they are back to front. */
    public long trueLength(long from, long to) {
        return distanceAt(to) - distanceAt(from);
    }

    /** The label at a given distance along the ground, and the series it is in. */
    public Located stationAtDistance(long distance) {
        if (distance < 0) {
            throw new BadInputException(
                    "distance along the alignment must not be negative", Details.of("distance", distance));
        }
        for (Segment segment : segments()) {
            long length = segment.to() - segment.from();
            if (distance <= segment.startDistance() + length) {
                long offset = distance - segment.startDistance();
                return new Located(segment.from() + offset, segment.series());
            }
        }
        throw new BadInputException(
                "distance runs off the end of the alignment",
                Details.of("alignment", id, "distance", distance, "length", length()));
    }
}
