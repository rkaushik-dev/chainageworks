package io.github.rkaushikdev.chainageworks.domain.earthworks;

import java.util.Optional;

/**
 * Somewhere material comes from, or goes to.
 *
 * <p>A cut is a source that costs nothing to win because the job has to dig it anyway. A borrow pit
 * is a source that has to be bought. A tip is a destination that has to be paid for. Keeping the
 * three apart is what lets the allocation prefer a long haul from a cut over a short haul from a
 * borrow pit, which is nearly always the cheaper answer and never the obvious one.
 */
public enum SiteKind {
    CUT("cut"),
    BORROW("borrow"),
    FILL("fill"),
    TIP("tip");

    private final String wire;

    SiteKind(String wire) {
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
    public static Optional<SiteKind> fromWire(String text) {
        for (SiteKind value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
