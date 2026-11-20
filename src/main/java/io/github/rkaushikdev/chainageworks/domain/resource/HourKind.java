package io.github.rkaushikdev.chainageworks.domain.resource;

import java.util.Optional;

/**
 * How an hour on site was spent.
 *
 * <p>The four states are the ones a foreman actually writes on a return, and the difference between
 * them is what makes the record worth keeping. Working hours bought progress, standing hours were
 * paid for and bought nothing, travelling hours were neither, and idle hours are the ones a delay
 * claim is eventually argued over.
 */
public enum HourKind {
    WORKING("working"),
    STANDING("standing"),
    TRAVELLING("travelling"),
    IDLE("idle");

    private final String wire;

    HourKind(String wire) {
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
    public static Optional<HourKind> fromWire(String text) {
        for (HourKind value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
