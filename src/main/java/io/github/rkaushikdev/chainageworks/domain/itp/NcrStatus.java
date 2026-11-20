package io.github.rkaushikdev.chainageworks.domain.itp;

import java.util.Optional;

/**
 * How a non conformance was settled.
 *
 * <p>{@code closed} means the work was put right. {@code accepted} means it was left as built under
 * a concession. The difference matters for the record and not for the money, because both release
 * the credit that was being withheld, and running them together would lose the concession history
 * the defects list needs.
 */
public enum NcrStatus {
    OPEN("open"),
    CLOSED("closed"),
    ACCEPTED("accepted");

    private final String wire;

    NcrStatus(String wire) {
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
    public static Optional<NcrStatus> fromWire(String text) {
        for (NcrStatus value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
