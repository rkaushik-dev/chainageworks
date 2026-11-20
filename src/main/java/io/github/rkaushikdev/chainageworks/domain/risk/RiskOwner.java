package io.github.rkaushikdev.chainageworks.domain.risk;

import java.util.Optional;

/**
 * Who carries a risk if it happens.
 *
 * <p>The whole point of an early warning register is that this is written down before anybody
 * knows the answer to the money. A risk logged with no owner is a risk nobody is working on, which
 * is why the field has no default.
 */
public enum RiskOwner {
    CONTRACTOR("contractor"),
    EMPLOYER("employer"),
    SHARED("shared"),
    UNRESOLVED("unresolved");

    private final String wire;

    RiskOwner(String wire) {
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
    public static Optional<RiskOwner> fromWire(String text) {
        for (RiskOwner value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
