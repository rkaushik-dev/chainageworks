package io.github.rkaushikdev.chainageworks.config;

import java.util.Optional;

/** How much the process says while it runs, from nothing at all to everything. */
public enum LogLevel {
    SILENT("silent"),
    ERROR("error"),
    WARN("warn"),
    INFO("info"),
    DEBUG("debug");

    private final String wire;

    LogLevel(String wire) {
        this.wire = wire;
    }

    /** The level as the environment and the config spell it. */
    public String wire() {
        return wire;
    }

    @Override
    public String toString() {
        return wire;
    }

    /** The level spelled {@code text}, if there is one. */
    public static Optional<LogLevel> fromWire(String text) {
        for (LogLevel level : values()) {
            if (level.wire.equals(text)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }
}
