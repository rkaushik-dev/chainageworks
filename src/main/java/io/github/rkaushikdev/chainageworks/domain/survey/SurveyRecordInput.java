package io.github.rkaushikdev.chainageworks.domain.survey;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import java.time.LocalDate;

/**
 * What a survey record is built from. The method defaults to a tape, the note to nothing, and a
 * {@code null} supersedes means a first booking.
 *
 * @param id the record's key
 * @param packageId the package it is booked against
 * @param code the bill item
 * @param stepId the step of the rule of credit it measures
 * @param extent the stretch measured
 * @param quantity what was measured
 * @param measuredOn the day it was measured
 * @param bookedBy who booked it
 * @param method how it was measured
 * @param supersedes the record this one replaces, if it is a remeasure
 * @param note anything the surveyor added
 */
public record SurveyRecordInput(
        String id,
        String packageId,
        ItemCode code,
        String stepId,
        ChainageRange extent,
        Quantity quantity,
        LocalDate measuredOn,
        String bookedBy,
        MeasurementMethod method,
        String supersedes,
        String note) {

    /** A first booking by tape with no note. */
    public static SurveyRecordInput of(
            String id,
            String packageId,
            ItemCode code,
            String stepId,
            ChainageRange extent,
            Quantity quantity,
            LocalDate measuredOn,
            String bookedBy) {
        return new SurveyRecordInput(
                id, packageId, code, stepId, extent, quantity, measuredOn, bookedBy, null, null, null);
    }

    public SurveyRecordInput withMethod(MeasurementMethod other) {
        return new SurveyRecordInput(
                id, packageId, code, stepId, extent, quantity, measuredOn, bookedBy, other, supersedes, note);
    }

    public SurveyRecordInput withSupersedes(String other) {
        return new SurveyRecordInput(
                id, packageId, code, stepId, extent, quantity, measuredOn, bookedBy, method, other, note);
    }

    public SurveyRecordInput withNote(String other) {
        return new SurveyRecordInput(
                id, packageId, code, stepId, extent, quantity, measuredOn, bookedBy, method, supersedes, other);
    }
}
