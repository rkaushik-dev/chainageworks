package io.github.rkaushikdev.chainageworks.domain.defects;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One item on the defects list: what is wrong, who caused it, and when it has to be put right.
 *
 * @param id the defect's key
 * @param reference the defects list reference
 * @param packageId the package it is in
 * @param extent where on the alignment
 * @param description what is wrong
 * @param severity how bad it is
 * @param attribution who caused it
 * @param raisedOn the day it was raised
 * @param dueBy the date the contract gives for putting it right
 * @param rectifiedOn the day it was put right, absent while it is outstanding
 */
public record Defect(
        String id,
        String reference,
        String packageId,
        Optional<ChainageRange> extent,
        String description,
        DefectSeverity severity,
        Attribution attribution,
        LocalDate raisedOn,
        LocalDate dueBy,
        Optional<LocalDate> rectifiedOn) {

    public Defect {
        extent = extent == null ? Optional.empty() : extent;
        rectifiedOn = rectifiedOn == null ? Optional.empty() : rectifiedOn;
    }

    /**
     * The defects list at a date, counted.
     *
     * @param outstanding how many are open
     * @param overdue how many of those are past their due date
     * @param bySeverity the open ones by severity, minor first
     * @param disputed open ones nobody has decided the cause of
     */
    public record Summary(int outstanding, int overdue, Map<DefectSeverity, Integer> bySeverity, int disputed) {

        public Summary {
            bySeverity = Collections.unmodifiableMap(new EnumMap<>(bySeverity));
        }
    }

    /** The date a defect raised on {@code raisedOn} has to be put right by. */
    public static LocalDate dueDateFor(DefectSeverity severity, LocalDate raisedOn, SiteCalendar calendar) {
        return calendar.addWorkingDays(raisedOn, severity.rectificationDays());
    }

    /**
     * Build a defect, refusing a blank description, a due date before it was raised and a
     * rectification before it was raised.
     */
    public static Defect build(DefectInput input) {
        if (Text.trim(input.description()).isEmpty()) {
            throw new BadInputException("a defect needs a description", Details.of("defect", input.id()));
        }
        if (input.dueBy().isBefore(input.raisedOn())) {
            throw new BadInputException(
                    "a defect cannot be due before it was raised",
                    Details.of(
                            "defect", input.id(),
                            "raised", SiteDates.format(input.raisedOn()),
                            "due", SiteDates.format(input.dueBy())));
        }
        Optional<LocalDate> rectifiedOn = Optional.ofNullable(input.rectifiedOn());
        if (rectifiedOn.isPresent() && rectifiedOn.get().isBefore(input.raisedOn())) {
            throw new BadInputException(
                    "a defect cannot be put right before it was raised",
                    Details.of(
                            "defect", input.id(),
                            "raised", SiteDates.format(input.raisedOn()),
                            "rectified", SiteDates.format(rectifiedOn.get())));
        }
        return new Defect(
                input.id(),
                input.reference(),
                input.packageId(),
                Optional.ofNullable(input.extent()),
                Text.trim(input.description()),
                input.severity() == null ? DefectSeverity.MINOR : input.severity(),
                input.attribution() == null ? Attribution.CONTRACTOR : input.attribution(),
                input.raisedOn(),
                input.dueBy(),
                rectifiedOn);
    }

    /** Raised by {@code asAt} and not yet put right on that day. */
    public boolean isOutstandingAt(LocalDate asAt) {
        if (raisedOn.isAfter(asAt)) {
            return false;
        }
        return rectifiedOn.map(on -> on.isAfter(asAt)).orElse(true);
    }

    /** Outstanding and past the day it was due. */
    public boolean isOverdueAt(LocalDate asAt) {
        return isOutstandingAt(asAt) && dueBy.isBefore(asAt);
    }

    /** The defects outstanding at a date, soonest due first. */
    public static List<Defect> outstanding(List<Defect> defects, LocalDate asAt) {
        List<Defect> out = new ArrayList<>(
                defects.stream().filter(defect -> defect.isOutstandingAt(asAt)).toList());
        out.sort((a, b) -> {
            int byDue = a.dueBy().compareTo(b.dueBy());
            if (byDue != 0) {
                return byDue;
            }
            return a.id().compareTo(b.id()) < 0 ? -1 : 1;
        });
        return List.copyOf(out);
    }

    /** The outstanding defects past their due date. */
    public static List<Defect> overdue(List<Defect> defects, LocalDate asAt) {
        return outstanding(defects, asAt).stream()
                .filter(defect -> defect.isOverdueAt(asAt))
                .toList();
    }

    /** The list at a date, counted by state and severity. */
    public static Summary summarise(List<Defect> defects, LocalDate asAt) {
        List<Defect> open = outstanding(defects, asAt);
        Map<DefectSeverity, Integer> bySeverity = new EnumMap<>(DefectSeverity.class);
        for (DefectSeverity severity : DefectSeverity.values()) {
            bySeverity.put(severity, 0);
        }
        for (Defect defect : open) {
            bySeverity.merge(defect.severity(), 1, Integer::sum);
        }
        int disputed = (int) open.stream()
                .filter(defect -> defect.attribution() == Attribution.UNRESOLVED)
                .count();
        return new Summary(open.size(), overdue(defects, asAt).size(), bySeverity, disputed);
    }

    /**
     * Why the defects certificate cannot be issued, or empty when it can.
     *
     * <p>A defect the employer caused does not stop it, and a defect nobody has decided about
     * does, which is the right way round: the certificate cannot be signed while there is an open
     * question about who pays for what.
     */
    public static Optional<CertificateBlock> certificateBlock(
            List<Defect> defects, LocalDate periodEnds, LocalDate asAt) {
        if (asAt.isBefore(periodEnds)) {
            return Optional.of(CertificateBlock.PERIOD_NOT_ENDED);
        }
        List<Defect> open = outstanding(defects, asAt);
        if (open.stream().anyMatch(defect -> defect.severity() == DefectSeverity.SAFETY)) {
            return Optional.of(CertificateBlock.SAFETY_DEFECT_OPEN);
        }
        if (open.stream().anyMatch(defect -> defect.attribution() == Attribution.UNRESOLVED)) {
            return Optional.of(CertificateBlock.ATTRIBUTION_UNRESOLVED);
        }
        if (open.stream().anyMatch(defect -> defect.attribution() == Attribution.CONTRACTOR)) {
            return Optional.of(CertificateBlock.CONTRACTOR_DEFECTS_OPEN);
        }
        return Optional.empty();
    }

    public static boolean canIssueDefectsCertificate(List<Defect> defects, LocalDate periodEnds, LocalDate asAt) {
        return certificateBlock(defects, periodEnds, asAt).isEmpty();
    }

    /** {@code DEF/2025/011 minor, due 2025-12-01: description}, like a list line. */
    public String describe() {
        String state =
                rectifiedOn.map(on -> "put right " + SiteDates.format(on)).orElse("due " + SiteDates.format(dueBy));
        return reference + " " + severity.wire() + ", " + state + ": " + description;
    }
}
