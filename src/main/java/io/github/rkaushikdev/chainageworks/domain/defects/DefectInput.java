package io.github.rkaushikdev.chainageworks.domain.defects;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import java.time.LocalDate;

/**
 * What a defect is built from. A {@code null} severity means minor, a {@code null} attribution
 * means the contractor, a {@code null} extent or rectification date means none, and a {@code null}
 * use flag means the works stayed in use.
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
 * @param rectifiedOn the day it was put right
 * @param preventsUse whether the defect kept the works out of the use they were built for
 */
public record DefectInput(
        String id,
        String reference,
        String packageId,
        ChainageRange extent,
        String description,
        DefectSeverity severity,
        Attribution attribution,
        LocalDate raisedOn,
        LocalDate dueBy,
        LocalDate rectifiedOn,
        Boolean preventsUse) {

    /** A minor contractor defect with no extent, not yet put right. */
    public static DefectInput of(
            String id, String reference, String packageId, String description, LocalDate raisedOn, LocalDate dueBy) {
        return new DefectInput(id, reference, packageId, null, description, null, null, raisedOn, dueBy, null, null);
    }

    public DefectInput withId(String other) {
        Draft draft = new Draft(this);
        draft.id = other;
        return draft.input();
    }

    public DefectInput withReference(String other) {
        Draft draft = new Draft(this);
        draft.reference = other;
        return draft.input();
    }

    public DefectInput withExtent(ChainageRange other) {
        Draft draft = new Draft(this);
        draft.extent = other;
        return draft.input();
    }

    public DefectInput withDescription(String other) {
        Draft draft = new Draft(this);
        draft.description = other;
        return draft.input();
    }

    public DefectInput withSeverity(DefectSeverity other) {
        Draft draft = new Draft(this);
        draft.severity = other;
        return draft.input();
    }

    public DefectInput withAttribution(Attribution other) {
        Draft draft = new Draft(this);
        draft.attribution = other;
        return draft.input();
    }

    public DefectInput withDates(LocalDate raised, LocalDate due) {
        Draft draft = new Draft(this);
        draft.raisedOn = raised;
        draft.dueBy = due;
        return draft.input();
    }

    public DefectInput withDueBy(LocalDate other) {
        Draft draft = new Draft(this);
        draft.dueBy = other;
        return draft.input();
    }

    public DefectInput withRectifiedOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.rectifiedOn = other;
        return draft.input();
    }

    /** The defect as one that kept the works out of use, or as one that did not. */
    public DefectInput withPreventsUse(boolean other) {
        Draft draft = new Draft(this);
        draft.preventsUse = other;
        return draft.input();
    }

    /** A copy of the input with some fields changed, so each wither names only its own fields. */
    private static final class Draft {
        private String id;
        private String reference;
        private String packageId;
        private ChainageRange extent;
        private String description;
        private DefectSeverity severity;
        private Attribution attribution;
        private LocalDate raisedOn;
        private LocalDate dueBy;
        private LocalDate rectifiedOn;
        private Boolean preventsUse;

        private Draft(DefectInput input) {
            id = input.id;
            reference = input.reference;
            packageId = input.packageId;
            extent = input.extent;
            description = input.description;
            severity = input.severity;
            attribution = input.attribution;
            raisedOn = input.raisedOn;
            dueBy = input.dueBy;
            rectifiedOn = input.rectifiedOn;
            preventsUse = input.preventsUse;
        }

        private DefectInput input() {
            return new DefectInput(
                    id,
                    reference,
                    packageId,
                    extent,
                    description,
                    severity,
                    attribution,
                    raisedOn,
                    dueBy,
                    rectifiedOn,
                    preventsUse);
        }
    }
}
