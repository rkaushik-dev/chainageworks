package io.github.rkaushikdev.chainageworks.domain.itp;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;

/**
 * One inspection actually carried out, over a stretch of the works.
 *
 * <p>A sign-off is not a flag on the package. It covers the chainage that was inspected and nothing
 * else, so a hold point signed for the first two hundred metres opens the first two hundred metres
 * and leaves the rest held.
 *
 * @param id the sign off's key
 * @param pointId the inspection point
 * @param packageId the package inspected
 * @param extent the stretch inspected
 * @param signedOn the day it was signed
 * @param by who signed it
 * @param outcome what the inspection found
 * @param note anything the inspector added
 */
public record SignOff(
        String id,
        String pointId,
        String packageId,
        ChainageRange extent,
        LocalDate signedOn,
        String by,
        SignOffOutcome outcome,
        String note) {

    /** Build a sign off, refusing one that names nobody as signing it. */
    public static SignOff build(SignOffInput input) {
        if (Text.trim(input.by()).isEmpty()) {
            throw new BadInputException("a sign off has to name who signed it", Details.of("signOff", input.id()));
        }
        return new SignOff(
                input.id(),
                input.pointId(),
                input.packageId(),
                input.extent(),
                input.signedOn(),
                Text.trim(input.by()),
                input.outcome() == null ? SignOffOutcome.PASSED : input.outcome(),
                input.note() == null ? "" : input.note());
    }

    /** A failed inspection is a record of an inspection, not a release. */
    public boolean releases() {
        return outcome != SignOffOutcome.FAILED;
    }

    /** {@code IP-01 passed on 2025-03-10 by A Surveyor}, like a register line. */
    public String describe() {
        return pointId + " " + outcome.wire() + " on " + SiteDates.format(signedOn) + " by " + by;
    }
}
