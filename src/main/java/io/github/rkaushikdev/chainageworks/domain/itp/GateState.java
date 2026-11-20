package io.github.rkaushikdev.chainageworks.domain.itp;

import java.util.Optional;

/** Whether a step may be credited over the stretch asked about: all of it, part of it, or none. */
public enum GateState {
    OPEN("open"),
    PART_OPEN("part_open"),
    HELD("held");

    private final String wire;

    GateState(String wire) {
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
    public static Optional<GateState> fromWire(String text) {
        for (GateState value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
