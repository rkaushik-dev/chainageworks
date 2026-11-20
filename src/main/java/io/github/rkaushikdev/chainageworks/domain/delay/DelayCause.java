package io.github.rkaushikdev.chainageworks.domain.delay;

import java.util.Optional;

/**
 * Why the works stopped.
 *
 * <p>The cause decides the entitlement, and the entitlement is what the argument is actually
 * about. Weather buys time and no money; a variation buys both; anything the contractor caused
 * buys neither, and recording it is still worth doing because a delay with no entitlement still
 * moves the programme.
 */
public enum DelayCause {
    WEATHER("weather", Entitlement.TIME_ONLY),
    VARIATION("variation", Entitlement.TIME_AND_MONEY),
    ACCESS("access", Entitlement.TIME_AND_MONEY),
    DESIGN("design", Entitlement.TIME_AND_MONEY),
    UTILITIES("utilities", Entitlement.TIME_AND_MONEY),
    GROUND("ground", Entitlement.TIME_AND_MONEY),
    CONTRACTOR("contractor", Entitlement.NONE);

    private final String wire;
    private final Entitlement entitlement;

    DelayCause(String wire, Entitlement entitlement) {
        this.wire = wire;
        this.entitlement = entitlement;
    }

    /** The cause as it is written in the job file and the imports. */
    public String wire() {
        return wire;
    }

    /** What a delay of this cause buys. */
    public Entitlement entitlement() {
        return entitlement;
    }

    @Override
    public String toString() {
        return wire;
    }

    /** The cause written as {@code text}, if there is one. */
    public static Optional<DelayCause> fromWire(String text) {
        for (DelayCause cause : values()) {
            if (cause.wire.equals(text)) {
                return Optional.of(cause);
            }
        }
        return Optional.empty();
    }
}
