package io.github.rkaushikdev.chainageworks.domain.variation;

import java.util.Optional;

/**
 * How far along a variation is.
 *
 * <p>Only {@code instructed} and {@code agreed} change the works. A proposal has been priced and
 * not ordered, and a rejected one is kept so the file shows it was considered, but neither of them
 * may reach a valuation.
 */
public enum VariationStatus {
    PROPOSED("proposed"),
    INSTRUCTED("instructed"),
    AGREED("agreed"),
    REJECTED("rejected");

    private final String wire;

    VariationStatus(String wire) {
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
    public static Optional<VariationStatus> fromWire(String text) {
        for (VariationStatus value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
