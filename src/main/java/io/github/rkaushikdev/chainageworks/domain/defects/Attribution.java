package io.github.rkaushikdev.chainageworks.domain.defects;

import java.util.Optional;

/** Who caused a defect, and so who pays to put it right. */
public enum Attribution {
    CONTRACTOR("contractor"),
    EMPLOYER("employer"),
    UNRESOLVED("unresolved");

    private final String wire;

    Attribution(String wire) {
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
    public static Optional<Attribution> fromWire(String text) {
        for (Attribution value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
