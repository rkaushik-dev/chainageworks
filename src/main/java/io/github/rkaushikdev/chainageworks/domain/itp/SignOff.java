package io.github.rkaushikdev.chainageworks.domain.itp;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.Optional;

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
 * @param noticeGivenOn when the engineer was given notice of the inspection, if the register says
 */
public record SignOff(
        String id,
        String pointId,
        String packageId,
        ChainageRange extent,
        LocalDate signedOn,
        String by,
        SignOffOutcome outcome,
        String note,
        Optional<LocalDate> noticeGivenOn) {

    public SignOff {
        noticeGivenOn = noticeGivenOn == null ? Optional.empty() : noticeGivenOn;
    }

    /** A sign off with no notice date on the register. */
    public SignOff(
            String id,
            String pointId,
            String packageId,
            ChainageRange extent,
            LocalDate signedOn,
            String by,
            SignOffOutcome outcome,
            String note) {
        this(id, pointId, packageId, extent, signedOn, by, outcome, note, Optional.empty());
    }

    /**
     * Build a sign off, refusing one that names nobody as signing it, notice given after the
     * inspection, and a record that nobody attended with no notice behind it.
     */
    public static SignOff build(SignOffInput input) {
        if (Text.trim(input.by()).isEmpty()) {
            throw new BadInputException("a sign off has to name who signed it", Details.of("signOff", input.id()));
        }
        Optional<LocalDate> notice = Optional.ofNullable(input.noticeGivenOn());
        if (notice.isPresent() && notice.get().isAfter(input.signedOn())) {
            throw new BadInputException(
                    "notice of an inspection cannot come after it", Details.of("signOff", input.id()));
        }
        if (input.outcome() == SignOffOutcome.NOT_ATTENDED && notice.isEmpty()) {
            throw new BadInputException(
                    "nobody can fail to attend an inspection they were never told about",
                    Details.of("signOff", input.id()));
        }
        return new SignOff(
                input.id(),
                input.pointId(),
                input.packageId(),
                input.extent(),
                input.signedOn(),
                Text.trim(input.by()),
                input.outcome() == null ? SignOffOutcome.PASSED : input.outcome(),
                input.note() == null ? "" : input.note(),
                notice);
    }

    /** Clear days the engineer has to be given before an inspection nobody attends releases the work. */
    public static final int NOTICE_DAYS = 1;

    /**
     * Whether the sign off lets the work go on. A failed inspection is a record of an inspection,
     * not a release. One nobody attended releases the work only when the notice gave the engineer
     * at least {@link #NOTICE_DAYS} to get there; notice on the morning itself is not notice.
     */
    public boolean releases() {
        return switch (outcome) {
            case PASSED, PASSED_WITH_COMMENT -> true;
            case FAILED -> false;
            case NOT_ATTENDED ->
                noticeGivenOn
                        .map(on -> SiteDates.diffDays(on, signedOn) >= NOTICE_DAYS)
                        .orElse(false);
        };
    }

    /** True for a release that rests on the engineer not attending rather than on an inspection. */
    public boolean isDeemed() {
        return outcome == SignOffOutcome.NOT_ATTENDED && releases();
    }

    /** {@code IP-01 passed on 2025-03-10 by A Surveyor}, like a register line. */
    public String describe() {
        return pointId + " " + outcome.wire() + " on " + SiteDates.format(signedOn) + " by " + by;
    }
}
