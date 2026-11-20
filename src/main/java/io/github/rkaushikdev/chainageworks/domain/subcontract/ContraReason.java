package io.github.rkaushikdev.chainageworks.domain.subcontract;

import java.util.Optional;

/**
 * What a contra charge is for.
 *
 * <p>The reason matters more than the amount, because the reasons are not equally defensible.
 * Attendance and welfare are usually agreed in the order and go through unopposed; rectification
 * and damage are charged after the event and are the ones that end up in an adjudication.
 */
public enum ContraReason {
    ATTENDANCE("attendance"),
    WELFARE("welfare"),
    PLANT_HIRE("plant_hire"),
    RECTIFICATION("rectification"),
    DAMAGE("damage"),
    CLEAN_UP("clean_up"),
    OTHER("other");

    private final String wire;

    ContraReason(String wire) {
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

    /** Whether the order normally covers this reason, so the subcontractor cannot dispute it. */
    public boolean agreedInOrder() {
        return this == ATTENDANCE || this == WELFARE || this == PLANT_HIRE;
    }

    /** The value written as {@code text}, if there is one. */
    public static Optional<ContraReason> fromWire(String text) {
        for (ContraReason value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
