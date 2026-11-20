package io.github.rkaushikdev.chainageworks.domain.earthworks;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A cross section taken at a chainage.
 *
 * <p>Areas are held as whole thousandths of a square metre, the same trick the rest of the package
 * uses: a section area comes off a survey to two or three decimal places and a run of two hundred
 * of them added as floats drifts by enough to argue about.
 *
 * @param station where the section was taken
 * @param cutArea area of cut at this section, in thousandths of a square metre
 * @param fillArea area of fill at this section, in thousandths of a square metre
 */
public record CrossSection(long station, long cutArea, long fillArea) {

    /**
     * Cut and fill volumes over a stretch.
     *
     * @param cut volume of cut
     * @param fill volume of fill
     */
    public record Volumes(Quantity cut, Quantity fill) {}

    /** A section, refusing an area that is negative or not a whole number of thousandths. */
    public static CrossSection of(long station, double cutArea, double fillArea) {
        checkArea("cut area", cutArea);
        checkArea("fill area", fillArea);
        return new CrossSection(station, (long) cutArea, (long) fillArea);
    }

    private static void checkArea(String name, double value) {
        if (!Numbers.isInteger(value) || value < 0) {
            throw new BadInputException(
                    "a " + name + " is a whole number of thousandths and not negative", Details.of("value", value));
        }
    }

    /** Areas from square metres, rounded to the thousandth. */
    public static CrossSection fromMetres(long station, double cut, double fill) {
        return of(station, Numbers.round(cut * 1000), Numbers.round(fill * 1000));
    }

    /** The sections in chainage order, refusing two taken at the same chainage. */
    public static List<CrossSection> sort(List<CrossSection> sections) {
        List<CrossSection> sorted = new ArrayList<>(sections);
        sorted.sort(Comparator.comparingLong(CrossSection::station));
        for (int i = 1; i < sorted.size(); i += 1) {
            if (sorted.get(i).station() == sorted.get(i - 1).station()) {
                throw new BadInputException(
                        "two cross sections were taken at the same chainage",
                        Details.of("station", Stations.format(sorted.get(i).station())));
            }
        }
        return List.copyOf(sorted);
    }

    private static long volumeMilli(long areaA, long areaB, long lengthMm) {
        // Areas are thousandths of a square metre and the length is millimetres. The two scales
        // cancel to leave thousandths of a cubic metre, which is what a quantity is held in, so
        // the only divisor left is the two of the average.
        return (long) Numbers.round(((double) (areaA + areaB) * lengthMm) / 2000);
    }

    /**
     * Average end area, which is what a bill of quantities is measured on.
     *
     * <p>It is not the exact volume of the solid between the two sections and it is not meant to
     * be. It is the method the specification names, so it is the method the payment is worked out
     * by, and using a better one would produce a figure the engineer will not certify.
     */
    public static Volumes endAreaVolume(CrossSection a, CrossSection b) {
        long length = Math.abs(b.station - a.station);
        return new Volumes(
                Quantity.ofMilli(volumeMilli(a.cutArea, b.cutArea, length), Unit.M3),
                Quantity.ofMilli(volumeMilli(a.fillArea, b.fillArea, length), Unit.M3));
    }

    /**
     * The prismoidal rule, for checking the end area figure.
     *
     * <p>End area overstates a solid that swells in the middle and understates one that pinches,
     * and on a deep cutting the difference runs to a few percent. The check is worth having even
     * though the payment stays on end area.
     */
    public static Volumes prismoidalVolume(CrossSection a, CrossSection middle, CrossSection b) {
        long length = Math.abs(b.station - a.station);
        return new Volumes(
                Quantity.ofMilli(prismoid(a.cutArea, middle.cutArea, b.cutArea, length), Unit.M3),
                Quantity.ofMilli(prismoid(a.fillArea, middle.fillArea, b.fillArea, length), Unit.M3));
    }

    private static long prismoid(long areaA, long areaMid, long areaB, long length) {
        return (long) Numbers.round(((double) (areaA + 4 * areaMid + areaB) * length) / 6000);
    }

    /** Every volume between consecutive sections, added. */
    public static Volumes totalVolumes(List<CrossSection> sections) {
        List<CrossSection> sorted = sort(sections);
        long cut = 0;
        long fill = 0;
        for (int i = 1; i < sorted.size(); i += 1) {
            Volumes volume = endAreaVolume(sorted.get(i - 1), sorted.get(i));
            cut += volume.cut().milli();
            fill += volume.fill().milli();
        }
        return new Volumes(Quantity.ofMilli(cut, Unit.M3), Quantity.ofMilli(fill, Unit.M3));
    }

    /**
     * Volumes over a stretch, interpolating a section at each end.
     *
     * <p>The interpolation matters because a package boundary almost never falls on a surveyed
     * section, and taking the nearest one instead moves a chainage worth of earthworks from one
     * package to the next.
     */
    public static Volumes volumesOver(List<CrossSection> sections, ChainageRange extent) {
        List<CrossSection> sorted = sort(sections);
        List<CrossSection> clipped = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i += 1) {
            CrossSection here = sorted.get(i);
            if (here.station >= extent.from() && here.station <= extent.to()) {
                clipped.add(here);
            }
            if (i + 1 >= sorted.size()) {
                continue;
            }
            CrossSection next = sorted.get(i + 1);
            for (long edge : new long[] {extent.from(), extent.to()}) {
                if (here.station < edge && edge < next.station) {
                    clipped.add(interpolate(here, next, edge));
                }
            }
        }
        return totalVolumes(clipped);
    }

    /** A section at a chainage between two surveyed ones, areas taken straight. */
    public static CrossSection interpolate(CrossSection a, CrossSection b, long at) {
        long span = b.station - a.station;
        if (span == 0) {
            return new CrossSection(at, a.cutArea, a.fillArea);
        }
        double share = (double) (at - a.station) / span;
        return of(at, between(a.cutArea, b.cutArea, share), between(a.fillArea, b.fillArea, share));
    }

    private static double between(long from, long to, double share) {
        return Numbers.round(from + (to - from) * share);
    }

    /**
     * Fill needs more material out of the cut than it finishes up as, because it is compacted. A
     * bulking factor over one means the cut goes further than its own volume suggests; under one,
     * less.
     */
    public static Quantity balanceAfterBulking(Volumes volumes, double bulkingFactor) {
        if (!Double.isFinite(bulkingFactor) || bulkingFactor <= 0) {
            throw new BadInputException(
                    "a bulking factor is a positive number", Details.of("bulkingFactor", bulkingFactor));
        }
        long usable = (long) Numbers.round(volumes.cut().milli() * bulkingFactor);
        return Quantity.ofMilli(usable - volumes.fill().milli(), Unit.M3);
    }

    public static boolean isBorrowNeeded(Volumes volumes, double bulkingFactor) {
        return balanceAfterBulking(volumes, bulkingFactor).milli() < 0;
    }
}
