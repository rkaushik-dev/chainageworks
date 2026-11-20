package io.github.rkaushikdev.chainageworks.domain.variation;

import java.util.Optional;

/**
 * What a change is priced on.
 *
 * <p>The order here is the order the contract asks them to be tried in: the bill rate if the work
 * is the same, a pro rata rate if it is similar, a star rate agreed for the job if it is not, and
 * daywork only when the work cannot be measured at all.
 */
public enum ValuationBasis {
    BILL_RATES("bill_rates"),
    PRO_RATA("pro_rata"),
    STAR_RATE("star_rate"),
    DAYWORK("daywork"),
    LUMP_SUM("lump_sum");

    private final String wire;

    ValuationBasis(String wire) {
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
    public static Optional<ValuationBasis> fromWire(String text) {
        for (ValuationBasis value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
