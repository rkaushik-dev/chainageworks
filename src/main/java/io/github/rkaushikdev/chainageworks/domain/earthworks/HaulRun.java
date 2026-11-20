package io.github.rkaushikdev.chainageworks.domain.earthworks;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A mass haul diagram: the running balance of cut less fill along a run of cross sections.
 *
 * @param ordinates the points of the diagram, in chainage order
 * @param bulkingFactor the factor the cut was multiplied by
 */
public record HaulRun(List<Ordinate> ordinates, double bulkingFactor) {

    /** The free haul distance a contract gives unless it says otherwise. */
    public static final double DEFAULT_FREE_HAUL_METRES = 300;

    public HaulRun {
        ordinates = List.copyOf(ordinates);
    }

    /**
     * One point on the mass haul diagram.
     *
     * <p>The ordinate is the running total of cut less fill from the start of the run. Where it
     * rises there is surplus material to move forward; where it falls there is a deficit to fill.
     * Where it crosses back through a level it has already been at, everything between the two
     * crossings balances, and that is the only thing on the diagram anybody actually reads off it.
     *
     * @param station where the point is
     * @param ordinate cut less fill from the start, in thousandths of a cubic metre
     */
    public record Ordinate(long station, long ordinate) {}

    /**
     * The haul moment split into what the contract gives away and what it pays for.
     *
     * @param freeHaulMetres the free haul distance
     * @param freeMoment the moment inside the free haul, which costs nothing extra
     * @param overhaulMoment the moment past it, which is what overhaul is paid on
     */
    public record Overhaul(double freeHaulMetres, double freeMoment, double overhaulMoment) {}

    /** The diagram with no bulking. */
    public static HaulRun build(List<CrossSection> sections) {
        return build(sections, 1);
    }

    /**
     * Build the diagram, applying the bulking factor to the cut.
     *
     * <p>The factor goes on the cut rather than the fill because that is the direction the material
     * moves: a cubic metre dug out makes some other number of cubic metres of compacted embankment,
     * and pretending otherwise is what leaves a job short of fill in its last month.
     */
    public static HaulRun build(List<CrossSection> sections, double bulkingFactor) {
        if (!Double.isFinite(bulkingFactor) || bulkingFactor <= 0) {
            throw new BadInputException(
                    "a bulking factor is a positive number", Details.of("bulkingFactor", bulkingFactor));
        }
        List<CrossSection> sorted = CrossSection.sort(sections);
        if (sorted.isEmpty()) {
            return new HaulRun(List.of(), bulkingFactor);
        }
        List<Ordinate> ordinates = new ArrayList<>();
        ordinates.add(new Ordinate(sorted.get(0).station(), 0));
        long running = 0;
        for (int i = 1; i < sorted.size(); i += 1) {
            CrossSection.Volumes volume = CrossSection.endAreaVolume(sorted.get(i - 1), sorted.get(i));
            running += (long) Numbers.round(volume.cut().milli() * bulkingFactor)
                    - volume.fill().milli();
            ordinates.add(new Ordinate(sorted.get(i).station(), running));
        }
        return new HaulRun(ordinates, bulkingFactor);
    }

    /** Where the diagram finishes: the surplus or the deficit of the whole run. */
    public Quantity closingOrdinate() {
        long last =
                ordinates.isEmpty() ? 0 : ordinates.get(ordinates.size() - 1).ordinate();
        return Quantity.ofMilli(last, Unit.M3);
    }

    /** Positive means surplus to dispose of, negative means fill to import. */
    public boolean isSurplus() {
        return closingOrdinate().milli() > 0;
    }

    /**
     * Chainages where the diagram crosses back through zero.
     *
     * <p>Between two of these the cut and the fill balance exactly, which is the stretch a haul is
     * planned over. The crossing is interpolated because the balance point almost never falls on a
     * surveyed section.
     */
    public List<Long> balancePoints() {
        List<Long> out = new ArrayList<>();
        for (int i = 1; i < ordinates.size(); i += 1) {
            Ordinate before = ordinates.get(i - 1);
            Ordinate here = ordinates.get(i);
            // The start is not a balance point. It is where the count began, and reporting it
            // puts a haul on the drawing that nobody is going to do.
            if (here.ordinate() == 0) {
                out.add(here.station());
                continue;
            }
            if ((before.ordinate() < 0 && here.ordinate() > 0) || (before.ordinate() > 0 && here.ordinate() < 0)) {
                long span = here.station() - before.station();
                double share = (double) Math.abs(before.ordinate()) / Math.abs(here.ordinate() - before.ordinate());
                out.add(before.station() + (long) Numbers.round(span * share));
            }
        }
        return List.copyOf(out);
    }

    /**
     * The haul moment, in cubic metre metres, to two places.
     *
     * <p>This is the area under the mass haul curve, and it is what a haul is priced on: moving a
     * lot of material a short way and a little a long way can come to the same money, and only the
     * moment says so.
     */
    public double haulMoment() {
        double moment = 0;
        for (int i = 1; i < ordinates.size(); i += 1) {
            Ordinate before = ordinates.get(i - 1);
            Ordinate here = ordinates.get(i);
            double lengthMetres = (double) (here.station() - before.station()) / Stations.MM_PER_METRE;
            double averageVolume = (double) (before.ordinate() + here.ordinate()) / 2 / 1000;
            moment += Math.abs(averageVolume) * lengthMetres;
        }
        return Numbers.round(moment * 100) / 100;
    }

    /** {@link #overhaul(double)} over the standard free haul distance. */
    public Overhaul overhaul() {
        return overhaul(DEFAULT_FREE_HAUL_METRES);
    }

    /**
     * Split the haul moment into what the contract gives away and what it pays for.
     *
     * <p>The free haul distance is the length material may be moved at the excavation rate. Past it
     * the contractor is paid overhaul on the extra distance only, so the split has to be made span
     * by span rather than on the total.
     */
    public Overhaul overhaul(double freeHaulMetres) {
        if (!Double.isFinite(freeHaulMetres) || freeHaulMetres < 0) {
            throw new BadInputException(
                    "a free haul distance is not negative", Details.of("freeHaulMetres", freeHaulMetres));
        }
        double free = 0;
        double over = 0;
        for (int i = 1; i < ordinates.size(); i += 1) {
            Ordinate before = ordinates.get(i - 1);
            Ordinate here = ordinates.get(i);
            double lengthMetres = (double) (here.station() - before.station()) / Stations.MM_PER_METRE;
            double averageVolume = Math.abs((double) (before.ordinate() + here.ordinate()) / 2 / 1000);
            double freeLength = Math.min(lengthMetres, freeHaulMetres);
            free += averageVolume * freeLength;
            over += averageVolume * (lengthMetres - freeLength);
        }
        return new Overhaul(freeHaulMetres, Numbers.round(free * 100) / 100, Numbers.round(over * 100) / 100);
    }

    /** {@code deficit of 3000 m3, 1 balance points at 12+250}. */
    public String describe() {
        Quantity closing = closingOrdinate();
        String state = closing.milli() == 0 ? "balanced" : isSurplus() ? "surplus" : "deficit";
        List<String> points =
                balancePoints().stream().map(s -> Stations.format(s, 0, false)).toList();
        return state + " of " + Numbers.format((double) Math.abs(closing.milli()) / 1000) + " m3, " + points.size()
                + " balance points"
                + (points.isEmpty() ? "" : " at " + points.stream().collect(Collectors.joining(", ")));
    }
}
