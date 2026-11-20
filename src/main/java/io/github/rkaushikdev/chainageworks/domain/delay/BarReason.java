package io.github.rkaushikdev.chainageworks.domain.delay;

import java.util.Optional;

/** Why a delay event cannot carry an award. */
public enum BarReason {
    NOT_NOTIFIED("not_notified"),
    NOTIFIED_LATE("notified_late"),
    NO_ENTITLEMENT("no_entitlement");

    private final String wire;

    BarReason(String wire) {
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
    public static Optional<BarReason> fromWire(String text) {
        for (BarReason value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
