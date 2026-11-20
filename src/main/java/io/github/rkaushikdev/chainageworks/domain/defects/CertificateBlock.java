package io.github.rkaushikdev.chainageworks.domain.defects;

import java.util.Optional;

/** Why the defects certificate cannot be issued yet. */
public enum CertificateBlock {
    PERIOD_NOT_ENDED("period_not_ended"),
    SAFETY_DEFECT_OPEN("safety_defect_open"),
    CONTRACTOR_DEFECTS_OPEN("contractor_defects_open"),
    ATTRIBUTION_UNRESOLVED("attribution_unresolved");

    private final String wire;

    CertificateBlock(String wire) {
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
    public static Optional<CertificateBlock> fromWire(String text) {
        for (CertificateBlock value : values()) {
            if (value.wire.equals(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
