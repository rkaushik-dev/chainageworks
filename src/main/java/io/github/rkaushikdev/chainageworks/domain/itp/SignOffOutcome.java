package io.github.rkaushikdev.chainageworks.domain.itp;

import java.util.Optional;

/**
 * What an inspection found.
 *
 * <p>Not attended is the contractor's record that notice of the inspection was given and nobody
 * came. On most contracts the work may then go ahead as if it had been inspected, because an
 * engineer who stays away cannot also hold the job up, but only when the notice was given in time.
 */
public enum SignOffOutcome {
    PASSED("passed"),
    PASSED_WITH_COMMENT("passed_with_comment"),
    FAILED("failed"),
    NOT_ATTENDED("not_attended");

    private final String wire;

    SignOffOutcome(String wire) {
        this.wire = wire;
    }

    /** The value as it is written in the job file and the imports. */
    public String wire() {
        return wire;
    }

    @Override
    public String toString() {
        return wire;
    }

    /** The value written as {@code text}, if there is one. */
    public static Optional<SignOffOutcome> fromWire(String text) {
        for (SignOffOutcome value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
