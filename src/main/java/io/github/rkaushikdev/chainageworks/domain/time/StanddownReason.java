package io.github.rkaushikdev.chainageworks.domain.time;

import java.util.Optional;

/** Why the works were shut down on a working day, as the site diary records it. */
public enum StanddownReason {
    WEATHER("weather"),
    GROUND_CONDITIONS("ground_conditions"),
    ACCESS("access"),
    INDUSTRIAL("industrial"),
    OTHER("other");

    private final String wire;

    StanddownReason(String wire) {
        this.wire = wire;
    }

    /** The reason as it is written in the diary and the job file. */
    public String wire() {
        return wire;
    }

    @Override
    public String toString() {
        return wire;
    }

    /** The reason written as {@code text}, if there is one. */
    public static Optional<StanddownReason> fromWire(String text) {
        for (StanddownReason reason : values()) {
            if (reason.wire.equals(text)) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }
}
