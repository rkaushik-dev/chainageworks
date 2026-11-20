package io.github.rkaushikdev.chainageworks.domain.bill;

import java.util.Optional;

/**
 * What kind of money a bill item is.
 *
 * <p>The distinction is not cosmetic. A measured item earns as it is built. A provisional item is
 * in the bill so the tender adds up and earns nothing until it is instructed. A star rate is one
 * agreed after the event for work the bill never described, and it is the only kind that can
 * appear mid contract.
 */
public enum BillItemKind {
    MEASURED("measured"),
    PROVISIONAL("provisional"),
    PRIME_COST("prime_cost"),
    LUMP("lump"),
    STAR("star");

    private final String wire;

    BillItemKind(String wire) {
        this.wire = wire;
    }

    /** The kind as it is written in the job file and the bill import. */
    public String wire() {
        return wire;
    }

    @Override
    public String toString() {
        return wire;
    }

    /** The kind written as {@code text}, if there is one. */
    public static Optional<BillItemKind> fromWire(String text) {
        for (BillItemKind kind : values()) {
            if (kind.wire.equals(text)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
