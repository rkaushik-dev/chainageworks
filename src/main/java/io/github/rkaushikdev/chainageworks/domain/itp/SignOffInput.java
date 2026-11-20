package io.github.rkaushikdev.chainageworks.domain.itp;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import java.time.LocalDate;

/**
 * What a sign off is built from. The outcome defaults to passed and the note to nothing.
 *
 * @param id the sign off's key
 * @param pointId the inspection point
 * @param packageId the package inspected
 * @param extent the stretch inspected
 * @param signedOn the day it was signed
 * @param by who signed it
 * @param outcome what the inspection found, or null for passed
 * @param note anything the inspector added, or null
 */
public record SignOffInput(
        String id,
        String pointId,
        String packageId,
        ChainageRange extent,
        LocalDate signedOn,
        String by,
        SignOffOutcome outcome,
        String note) {

    /** A passed inspection with no note. */
    public static SignOffInput of(
            String id, String pointId, String packageId, ChainageRange extent, LocalDate signedOn, String by) {
        return new SignOffInput(id, pointId, packageId, extent, signedOn, by, null, null);
    }

    public SignOffInput withOutcome(SignOffOutcome other) {
        return new SignOffInput(id, pointId, packageId, extent, signedOn, by, other, note);
    }

    public SignOffInput withNote(String other) {
        return new SignOffInput(id, pointId, packageId, extent, signedOn, by, outcome, other);
    }
}
