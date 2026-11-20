package io.github.rkaushikdev.chainageworks.domain.itp;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import java.time.LocalDate;

/**
 * What a non conformance report is built from. A {@code null} step means the whole package, a
 * {@code null} status means open, and a {@code null} withholding flag means it withholds credit.
 *
 * @param id the report's key
 * @param packageId the package it is raised against
 * @param extent the stretch it covers
 * @param stepId the credit step affected, or null for every step
 * @param raisedOn the day it was raised
 * @param settledOn the day it was settled, or null while it is open
 * @param status how it stands, or null for open
 * @param description what is wrong
 * @param withholdsCredit whether credit is held back while it is open, or null for yes
 */
public record NcrInput(
        String id,
        String packageId,
        ChainageRange extent,
        String stepId,
        LocalDate raisedOn,
        LocalDate settledOn,
        NcrStatus status,
        String description,
        Boolean withholdsCredit) {

    /** An open report against every step of the package, withholding credit. */
    public static NcrInput of(
            String id, String packageId, ChainageRange extent, LocalDate raisedOn, String description) {
        return new NcrInput(id, packageId, extent, null, raisedOn, null, null, description, null);
    }

    /** The input a report was built from, to change and build again. */
    public static NcrInput from(Ncr ncr) {
        return new NcrInput(
                ncr.id(),
                ncr.packageId(),
                ncr.extent(),
                ncr.stepId().orElse(null),
                ncr.raisedOn(),
                ncr.settledOn().orElse(null),
                ncr.status(),
                ncr.description(),
                ncr.withholdsCredit());
    }

    public NcrInput withId(String other) {
        return new NcrInput(
                other, packageId, extent, stepId, raisedOn, settledOn, status, description, withholdsCredit);
    }

    public NcrInput withStep(String other) {
        return new NcrInput(id, packageId, extent, other, raisedOn, settledOn, status, description, withholdsCredit);
    }

    public NcrInput withSettlement(NcrStatus how, LocalDate on) {
        return new NcrInput(id, packageId, extent, stepId, raisedOn, on, how, description, withholdsCredit);
    }

    public NcrInput withStatus(NcrStatus other) {
        return new NcrInput(id, packageId, extent, stepId, raisedOn, settledOn, other, description, withholdsCredit);
    }

    public NcrInput withSettledOn(LocalDate other) {
        return new NcrInput(id, packageId, extent, stepId, raisedOn, other, status, description, withholdsCredit);
    }

    public NcrInput withDescription(String other) {
        return new NcrInput(id, packageId, extent, stepId, raisedOn, settledOn, status, other, withholdsCredit);
    }

    public NcrInput withWithholdsCredit(boolean other) {
        return new NcrInput(id, packageId, extent, stepId, raisedOn, settledOn, status, description, other);
    }
}
