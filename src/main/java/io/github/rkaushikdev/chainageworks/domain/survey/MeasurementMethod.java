package io.github.rkaushikdev.chainageworks.domain.survey;

import java.util.Optional;

/** How a quantity was arrived at, which is what an audit asks first. */
public enum MeasurementMethod {
    TAPE("tape"),
    TOTAL_STATION("total_station"),
    GPS("gps"),
    WEIGHBRIDGE("weighbridge"),
    COUNT("count"),
    DRAWING("drawing");

    private final String wire;

    MeasurementMethod(String wire) {
        this.wire = wire;
    }

    /** The method as it is written in the survey book and the job file. */
    public String wire() {
        return wire;
    }

    @Override
    public String toString() {
        return wire;
    }

    /** The method written as {@code text}, if there is one. */
    public static Optional<MeasurementMethod> fromWire(String text) {
        for (MeasurementMethod method : values()) {
            if (method.wire.equals(text)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }
}
