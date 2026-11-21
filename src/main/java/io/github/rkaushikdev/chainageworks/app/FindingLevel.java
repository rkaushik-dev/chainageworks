package io.github.rkaushikdev.chainageworks.app;

import java.util.Optional;

/**
 * How badly a finding breaks the job.
 *
 * <p>An error means a number somewhere is wrong or a report will refuse to build. A warning means
 * something is odd and worth a look but the valuation still stands. The distinction is what makes the
 * list worth reading: a check that reports everything at one level gets ignored the first week.
 */
public enum FindingLevel {
    ERROR("error"),
    WARNING("warning");

    private final String wire;

    FindingLevel(String wire) {
        this.wire = wire;
    }

    /** The level as a finding prints it. */
    public String wire() {
        return wire;
    }

    @Override
    public String toString() {
        return wire;
    }

    /** The level written as {@code text}, if there is one. */
    public static Optional<FindingLevel> fromWire(String text) {
        for (FindingLevel value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
