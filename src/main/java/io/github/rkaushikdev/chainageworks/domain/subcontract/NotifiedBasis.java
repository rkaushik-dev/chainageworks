package io.github.rkaushikdev.chainageworks.domain.subcontract;

import java.util.Optional;

/** What the sum payable on an application was worked out from. */
public enum NotifiedBasis {
    PAYMENT_NOTICE("payment_notice"),
    PAY_LESS_NOTICE("pay_less_notice"),
    APPLICATION_BY_DEFAULT("application_by_default");

    private final String wire;

    NotifiedBasis(String wire) {
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
    public static Optional<NotifiedBasis> fromWire(String text) {
        for (NotifiedBasis value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
