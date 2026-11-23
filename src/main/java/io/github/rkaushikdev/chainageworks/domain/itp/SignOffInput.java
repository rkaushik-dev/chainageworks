package io.github.rkaushikdev.chainageworks.domain.itp;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import java.time.LocalDate;

/**
 * What a sign off is built from. The outcome defaults to passed, the note to nothing, and the
 * notice date to none.
 *
 * @param id the sign off's key
 * @param pointId the inspection point
 * @param packageId the package inspected
 * @param extent the stretch inspected
 * @param signedOn the day it was signed
 * @param by who signed it
 * @param outcome what the inspection found, or null for passed
 * @param note anything the inspector added, or null
 * @param noticeGivenOn the day the engineer was given notice of the inspection, or null
 */
public record SignOffInput(
        String id,
        String pointId,
        String packageId,
        ChainageRange extent,
        LocalDate signedOn,
        String by,
        SignOffOutcome outcome,
        String note,
        LocalDate noticeGivenOn) {

    /** A sign off in the shape a register without notice dates exports. */
    public SignOffInput(
            String id,
            String pointId,
            String packageId,
            ChainageRange extent,
            LocalDate signedOn,
            String by,
            SignOffOutcome outcome,
            String note) {
        this(id, pointId, packageId, extent, signedOn, by, outcome, note, null);
    }

    /** A passed inspection with no note. */
    public static SignOffInput of(
            String id, String pointId, String packageId, ChainageRange extent, LocalDate signedOn, String by) {
        return new SignOffInput(id, pointId, packageId, extent, signedOn, by, null, null, null);
    }

    public SignOffInput withOutcome(SignOffOutcome other) {
        return new SignOffInput(id, pointId, packageId, extent, signedOn, by, other, note, noticeGivenOn);
    }

    public SignOffInput withNote(String other) {
        return new SignOffInput(id, pointId, packageId, extent, signedOn, by, outcome, other, noticeGivenOn);
    }

    /** The sign off with the day notice of the inspection was given. */
    public SignOffInput withNoticeGivenOn(LocalDate other) {
        return new SignOffInput(id, pointId, packageId, extent, signedOn, by, outcome, note, other);
    }
}
