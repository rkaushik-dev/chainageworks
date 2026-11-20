package io.github.rkaushikdev.chainageworks.domain.resource;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.time.DateRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A daily resource return: the gangs and plant on a package for a shift, and how their hours went.
 *
 * @param id the return's key
 * @param packageId the package the resources worked in
 * @param stepId the credit step they worked on, absent for the whole package
 * @param date the day
 * @param shift the shift
 * @param labour the gangs
 * @param plant the plant
 * @param returnedBy who filled it in
 * @param note anything the foreman added
 */
public record ResourceReturn(
        String id,
        String packageId,
        Optional<String> stepId,
        LocalDate date,
        Shift shift,
        List<ResourceLine> labour,
        List<ResourceLine> plant,
        String returnedBy,
        String note) {

    /** The four kinds of hour, in the order a return lists them. */
    public static final List<HourKind> HOUR_KINDS = List.of(HourKind.values());

    public ResourceReturn {
        stepId = stepId == null ? Optional.empty() : stepId;
        labour = List.copyOf(labour);
        plant = List.copyOf(plant);
    }

    /**
     * A set of returns added up.
     *
     * @param labourHours every labour hour
     * @param plantHours every plant hour
     * @param workingHours hours that bought progress
     * @param standingHours hours paid for that bought nothing
     * @param idleHours hours a delay claim is argued over
     * @param returns how many returns
     * @param days how many distinct days they cover
     */
    public record Totals(
            double labourHours,
            double plantHours,
            double workingHours,
            double standingHours,
            double idleHours,
            int returns,
            int days) {

        /**
         * The share of the hours that bought nothing.
         *
         * <p>Reported as a fraction rather than a count so two packages of different sizes can be
         * set against each other, which is the only comparison anybody makes with this number.
         */
        public double lostFraction() {
            double all = workingHours + standingHours + idleHours;
            return all == 0 ? 0 : (standingHours + idleHours) / all;
        }
    }

    private static void checkLine(ResourceLine line, String where) {
        if (line.count() < 1) {
            throw new BadInputException(
                    "a resource line covers at least one of something",
                    Details.of("where", where, "resource", line.resourceId(), "count", line.count()));
        }
        if (!Double.isFinite(line.hours()) || line.hours() < 0) {
            throw new BadInputException(
                    "hours on a resource line cannot be negative",
                    Details.of("where", where, "resource", line.resourceId(), "hours", line.hours()));
        }
        if (line.hours() > 24) {
            throw new BadInputException(
                    "nobody works more than twenty four hours in a day",
                    Details.of("where", where, "resource", line.resourceId(), "hours", line.hours()));
        }
    }

    /** Build a return, refusing one with nothing on it and lines with no count or impossible hours. */
    public static ResourceReturn build(ResourceReturnInput input) {
        if (input.labour().isEmpty() && input.plant().isEmpty()) {
            throw new BadInputException(
                    "a resource return with nothing on it says nothing", Details.of("return", input.id()));
        }
        for (ResourceLine line : input.labour()) {
            checkLine(line, input.id() + " labour");
        }
        for (ResourceLine line : input.plant()) {
            checkLine(line, input.id() + " plant");
        }
        return new ResourceReturn(
                input.id(),
                input.packageId(),
                Optional.ofNullable(input.stepId()),
                input.date(),
                input.shift() == null ? Shift.DAY : input.shift(),
                input.labour(),
                input.plant(),
                input.returnedBy(),
                input.note() == null ? "" : input.note());
    }

    /** The hours of one kind across {@code lines}. */
    public static double hoursOfKind(List<ResourceLine> lines, HourKind kind) {
        double total = 0;
        for (ResourceLine line : lines) {
            if (line.kind() == kind) {
                total += line.lineHours();
            }
        }
        return total;
    }

    /** Every hour across {@code lines}. */
    public static double totalHours(List<ResourceLine> lines) {
        double total = 0;
        for (ResourceLine line : lines) {
            total += line.lineHours();
        }
        return total;
    }

    /**
     * Add up a set of returns.
     *
     * <p>Labour and plant are kept apart because they are not interchangeable: an excavator
     * standing costs money whether or not the gang is working, and a single combined hours figure
     * hides which of the two the job is losing.
     */
    public static Totals total(List<ResourceReturn> returns) {
        List<ResourceLine> labour = new ArrayList<>();
        List<ResourceLine> plant = new ArrayList<>();
        Set<LocalDate> days = new HashSet<>();
        for (ResourceReturn entry : returns) {
            labour.addAll(entry.labour());
            plant.addAll(entry.plant());
            days.add(entry.date());
        }
        List<ResourceLine> all = new ArrayList<>(labour);
        all.addAll(plant);
        return new Totals(
                totalHours(labour),
                totalHours(plant),
                hoursOfKind(all, HourKind.WORKING),
                hoursOfKind(all, HourKind.STANDING),
                hoursOfKind(all, HourKind.IDLE),
                returns.size(),
                days.size());
    }

    public static List<ResourceReturn> returnsFor(List<ResourceReturn> returns, String packageId) {
        return returns.stream().filter(r -> r.packageId().equals(packageId)).toList();
    }

    public static List<ResourceReturn> returnsIn(List<ResourceReturn> returns, DateRange range) {
        return returns.stream().filter(r -> range.contains(r.date())).toList();
    }

    /** The returns up to a cut off, the day itself included. */
    public static List<ResourceReturn> returnsTo(List<ResourceReturn> returns, LocalDate asAt) {
        return returns.stream().filter(r -> !r.date().isAfter(asAt)).toList();
    }

    /** {@code RR-01 WP-01 2025-03-03 day, 60 labour hours, 16 plant hours}. */
    public String describe() {
        Totals totals = total(List.of(this));
        return id + " " + packageId + " " + SiteDates.format(date) + " " + shift.wire() + ", "
                + Numbers.format(totals.labourHours()) + " labour hours, " + Numbers.format(totals.plantHours())
                + " plant hours";
    }
}
