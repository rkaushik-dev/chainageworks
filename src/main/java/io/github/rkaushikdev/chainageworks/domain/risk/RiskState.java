package io.github.rkaushikdev.chainageworks.domain.risk;

import java.util.Optional;

/** Where a risk on the early warning register stands. */
public enum RiskState {
    OPEN("open"),
    MITIGATED("mitigated"),
    HAPPENED("happened"),
    AVOIDED("avoided");

    private final String wire;

    RiskState(String wire) {
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
    public static Optional<RiskState> fromWire(String text) {
        for (RiskState value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
