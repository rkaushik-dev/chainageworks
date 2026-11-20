package io.github.rkaushikdev.chainageworks.domain.quantity;

/** What a unit measures. Two units of one dimension are still never added together. */
public enum Dimension {
    LENGTH("length"),
    AREA("area"),
    VOLUME("volume"),
    MASS("mass"),
    COUNT("count"),
    TIME("time"),
    LUMP("lump");

    private final String wire;

    Dimension(String wire) {
        this.wire = wire;
    }

    /** The dimension as it is written. */
    public String wire() {
        return wire;
    }

    @Override
    public String toString() {
        return wire;
    }
}
