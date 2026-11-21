package io.github.rkaushikdev.chainageworks.reporting;

import java.util.Optional;

/**
 * What a stretch of one step looks like on the ground.
 *
 * <p>Held and withheld are separated because the two need different people. Held work is waiting
 * on an inspection, which is a phone call. Withheld work is waiting on a non conformance being put
 * right, which is a gang.
 */
public enum CellState {
    NOT_STARTED("not_started", "."),
    CREDITED("credited", "#"),
    HELD("held", "o"),
    WITHHELD("withheld", "x");

    private final String wire;
    private final String glyph;

    CellState(String wire, String glyph) {
        this.wire = wire;
        this.glyph = glyph;
    }

    /** The value as it is written in the job file and the API. */
    public String wire() {
        return wire;
    }

    /** The character the strip map draws for a cell in this state. */
    public String glyph() {
        return glyph;
    }

    @Override
    public String toString() {
        return wire;
    }

    /** The value written as {@code text}, if there is one. */
    public static Optional<CellState> fromWire(String text) {
        for (CellState value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
