package io.github.rkaushikdev.chainageworks.domain.defects;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import java.time.LocalDate;

/**
 * What a defect is built from. A {@code null} severity means minor, a {@code null} attribution
 * means the contractor, and a {@code null} extent or rectification date means none.
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
        LocalDate rectifiedOn) {

    /** A minor contractor defect with no extent, not yet put right. */
    public static DefectInput of(
            String id, String reference, String packageId, String description, LocalDate raisedOn, LocalDate dueBy) {
        return new DefectInput(id, reference, packageId, null, description, null, null, raisedOn, dueBy, null);
    }

    public DefectInput withId(String other) {
        return new DefectInput(
                other, reference, packageId, extent, description, severity, attribution, raisedOn, dueBy, rectifiedOn);
    }

    public DefectInput withReference(String other) {
        return new DefectInput(
                id, other, packageId, extent, description, severity, attribution, raisedOn, dueBy, rectifiedOn);
    }

    public DefectInput withExtent(ChainageRange other) {
        return new DefectInput(
                id, reference, packageId, other, description, severity, attribution, raisedOn, dueBy, rectifiedOn);
    }

    public DefectInput withDescription(String other) {
        return new DefectInput(
                id, reference, packageId, extent, other, severity, attribution, raisedOn, dueBy, rectifiedOn);
    }

    public DefectInput withSeverity(DefectSeverity other) {
        return new DefectInput(
                id, reference, packageId, extent, description, other, attribution, raisedOn, dueBy, rectifiedOn);
    }

    public DefectInput withAttribution(Attribution other) {
        return new DefectInput(
                id, reference, packageId, extent, description, severity, other, raisedOn, dueBy, rectifiedOn);
    }

    public DefectInput withDates(LocalDate raised, LocalDate due) {
        return new DefectInput(
                id, reference, packageId, extent, description, severity, attribution, raised, due, rectifiedOn);
    }

    public DefectInput withDueBy(LocalDate other) {
        return new DefectInput(
                id, reference, packageId, extent, description, severity, attribution, raisedOn, other, rectifiedOn);
    }

    public DefectInput withRectifiedOn(LocalDate other) {
        return new DefectInput(
                id, reference, packageId, extent, description, severity, attribution, raisedOn, dueBy, other);
    }
}
