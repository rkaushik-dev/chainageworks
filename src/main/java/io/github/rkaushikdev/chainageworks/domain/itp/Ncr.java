package io.github.rkaushikdev.chainageworks.domain.itp;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A non conformance report: work found not to meet the specification, and whether credit for it
 * is held back until it is put right.
 *
 * @param id the report's key
 * @param packageId the package it is raised against
 * @param extent the stretch it covers
 * @param stepId the credit step affected, or absent when the whole package is
 * @param raisedOn the day it was raised
 * @param settledOn the day it was settled, absent while it is open
 * @param status how it stands
 * @param description what is wrong
 * @param withholdsCredit whether credit is held back while it is open
 */
public record Ncr(
        String id,
        String packageId,
        ChainageRange extent,
        Optional<String> stepId,
        LocalDate raisedOn,
        Optional<LocalDate> settledOn,
        NcrStatus status,
        String description,
        boolean withholdsCredit) {

    public Ncr {
        stepId = stepId == null ? Optional.empty() : stepId;
        settledOn = settledOn == null ? Optional.empty() : settledOn;
    }

    /**
     * What to look for when asking which stretches credit is being held off.
     *
     * @param ncrs every report on the job
     * @param packageId the package
     * @param stepId the step
     * @param asAt the date the question is asked on
     */
    public record WithholdQuery(List<Ncr> ncrs, String packageId, String stepId, LocalDate asAt) {

        public WithholdQuery {
            ncrs = List.copyOf(ncrs);
        }
    }

    /**
     * Build a report, refusing an open one with a settled date, a settled one without, one settled
     * before it was raised, and one with no description.
     */
    public static Ncr build(NcrInput input) {
        NcrStatus status = input.status() == null ? NcrStatus.OPEN : input.status();
        Optional<LocalDate> settledOn = Optional.ofNullable(input.settledOn());
        if (status == NcrStatus.OPEN && settledOn.isPresent()) {
            throw new BadInputException(
                    "an open non conformance cannot carry a settled date", Details.of("ncr", input.id()));
        }
        if (status != NcrStatus.OPEN && settledOn.isEmpty()) {
            throw new BadInputException(
                    "a settled non conformance needs the date it was settled",
                    Details.of("ncr", input.id(), "status", status.wire()));
        }
        if (settledOn.isPresent() && settledOn.get().isBefore(input.raisedOn())) {
            throw new BadInputException(
                    "a non conformance cannot be settled before it was raised",
                    Details.of(
                            "ncr", input.id(),
                            "raised", SiteDates.format(input.raisedOn()),
                            "settled", SiteDates.format(settledOn.get())));
        }
        if (Text.trim(input.description()).isEmpty()) {
            throw new BadInputException("a non conformance needs a description", Details.of("ncr", input.id()));
        }
        return new Ncr(
                input.id(),
                input.packageId(),
                input.extent(),
                Optional.ofNullable(input.stepId()),
                input.raisedOn(),
                settledOn,
                status,
                Text.trim(input.description()),
                input.withholdsCredit() == null || input.withholdsCredit());
    }

    /**
     * Whether the non conformance was open on a date.
     *
     * <p>It counts from the day it was raised and stops counting on the day it was settled, so a
     * report closed on the cut-off releases that month rather than the next one.
     */
    public boolean isOpenAt(LocalDate date) {
        if (raisedOn.isAfter(date)) {
            return false;
        }
        return settledOn.map(settled -> settled.isAfter(date)).orElse(true);
    }

    /** The reports settled between {@code from} and {@code to}, both days included. */
    public static List<Ncr> settledIn(List<Ncr> ncrs, LocalDate from, LocalDate to) {
        return ncrs.stream()
                .filter(n -> n.settledOn()
                        .map(settled -> !settled.isBefore(from) && !settled.isAfter(to))
                        .orElse(false))
                .toList();
    }

    /**
     * The stretches credit is being held off on a step. A report with no step named applies to
     * every step in the package, which is what a workmanship report covering a whole bay means in
     * practice.
     */
    public static List<ChainageRange> withheldExtent(WithholdQuery query) {
        List<ChainageRange> biting = query.ncrs().stream()
                .filter(n -> n.packageId().equals(query.packageId())
                        && n.withholdsCredit()
                        && n.stepId().map(step -> step.equals(query.stepId())).orElse(true)
                        && n.isOpenAt(query.asAt()))
                .map(Ncr::extent)
                .toList();
        return ChainageRange.union(biting);
    }

    /** {@code NCR-01 raised 2025-03-05, open: description}, like a register line. */
    public String describe() {
        String settled =
                settledOn.map(on -> status.wire() + " " + SiteDates.format(on)).orElse("open");
        return id + " raised " + SiteDates.format(raisedOn) + ", " + settled + ": " + description;
    }
}
