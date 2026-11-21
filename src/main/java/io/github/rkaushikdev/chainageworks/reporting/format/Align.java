package io.github.rkaushikdev.chainageworks.reporting.format;

import java.util.Optional;

/** Which edge of its column a table cell is set against. */
public enum Align {
    LEFT("left"),
    RIGHT("right");

    private final String wire;

    Align(String wire) {
        this.wire = wire;
    }

    /** The value as it is written in a report definition. */
    public String wire() {
        return wire;
    }

    @Override
    public String toString() {
        return wire;
    }

    /** The value written as {@code text}, if there is one. */
    public static Optional<Align> fromWire(String text) {
        for (Align value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
