package io.github.rkaushikdev.chainageworks.domain.resource;

import java.util.Optional;

/** Which shift a resource return covers. */
public enum Shift {
    DAY("day"),
    NIGHT("night");

    private final String wire;

    Shift(String wire) {
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
    public static Optional<Shift> fromWire(String text) {
        for (Shift value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
