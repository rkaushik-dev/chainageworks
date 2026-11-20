package io.github.rkaushikdev.chainageworks.domain.delay;

import java.util.Optional;

/** What a delay buys the contractor: time and money, time alone, or nothing. */
public enum Entitlement {
    TIME_AND_MONEY("time_and_money"),
    TIME_ONLY("time_only"),
    NONE("none");

    private final String wire;

    Entitlement(String wire) {
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
    public static Optional<Entitlement> fromWire(String text) {
        for (Entitlement value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
