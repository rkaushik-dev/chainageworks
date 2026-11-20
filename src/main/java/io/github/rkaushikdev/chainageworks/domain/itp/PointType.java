package io.github.rkaushikdev.chainageworks.domain.itp;

import java.util.Optional;

/**
 * What kind of gate an inspection point is.
 *
 * <p>A hold point stops the works: nothing past it may be credited until somebody signs. A witness
 * point gives the engineer the chance to attend and does not stop anything if they do not. A review
 * point is paperwork, checked later. Treating all three the same is how a job either stalls or
 * overclaims.
 */
public enum PointType {
    HOLD("hold"),
    WITNESS("witness"),
    REVIEW("review");

    private final String wire;

    PointType(String wire) {
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
    public static Optional<PointType> fromWire(String text) {
        for (PointType value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
