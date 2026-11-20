package io.github.rkaushikdev.chainageworks.domain.defects;

import java.util.Optional;

/**
 * How bad a defect is, which decides how long there is to put it right.
 *
 * <p>A safety defect is separated from a major one because it does more than set a shorter clock:
 * it stops the defects certificate outright, however small the work to fix it. Nobody signs off a
 * job with an open safety item on it.
 */
public enum DefectSeverity {
    MINOR("minor", 20),
    MAJOR("major", 10),
    SAFETY("safety", 1);

    private final String wire;
    private final int rectificationDays;

    DefectSeverity(String wire, int rectificationDays) {
        this.wire = wire;
        this.rectificationDays = rectificationDays;
    }

    /** The severity as it is written in the job file. */
    public String wire() {
        return wire;
    }

    /** Working days allowed to put a defect of this severity right. */
    public int rectificationDays() {
        return rectificationDays;
    }

    @Override
    public String toString() {
        return wire;
    }

    /** The severity written as {@code text}, if there is one. */
    public static Optional<DefectSeverity> fromWire(String text) {
        for (DefectSeverity severity : values()) {
            if (severity.wire.equals(text)) {
                return Optional.of(severity);
            }
        }
        return Optional.empty();
    }
}
