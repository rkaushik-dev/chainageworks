package io.github.rkaushikdev.chainageworks.valuation;

import java.util.Optional;

/** Why a lot of unfixed materials cannot be certified at a cut-off. */
public enum NotClaimableReason {
    NOT_DELIVERED("not_delivered"),
    NOT_VESTED("not_vested"),
    NOT_PROTECTED("not_protected"),
    INCORPORATED("incorporated");

    private final String wire;

    NotClaimableReason(String wire) {
        this.wire = wire;
    }

    /** The value as it is written in reports and the job file. */
    public String wire() {
        return wire;
    }

    @Override
    public String toString() {
        return wire;
    }

    /** The value written as {@code text}, if there is one. */
    public static Optional<NotClaimableReason> fromWire(String text) {
        for (NotClaimableReason value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
