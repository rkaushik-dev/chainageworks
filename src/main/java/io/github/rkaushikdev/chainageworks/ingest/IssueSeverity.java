package io.github.rkaushikdev.chainageworks.ingest;

import java.util.Optional;

/** Whether an import issue left its row out (an error) or took it with a note (a warning). */
public enum IssueSeverity {
    ERROR("error"),
    WARNING("warning");

    private final String wire;

    IssueSeverity(String wire) {
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
    public static Optional<IssueSeverity> fromWire(String text) {
        for (IssueSeverity value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
