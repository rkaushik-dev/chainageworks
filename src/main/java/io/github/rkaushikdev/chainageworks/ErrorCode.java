package io.github.rkaushikdev.chainageworks;

import java.util.Optional;

/**
 * The stable machine code every deliberate failure carries. The command line prints it next to
 * the message and the HTTP layer maps it to a status, so renaming one is a breaking change for
 * both.
 */
public enum ErrorCode {
    /** The caller handed over something the domain cannot accept. */
    BAD_INPUT("bad_input"),
    /** A referenced record does not exist in the store. */
    NOT_FOUND("not_found"),
    /** The request is well formed but the current state refuses it. */
    CONFLICT("conflict"),
    /** A shape that is known about but not implemented for this contract type. */
    UNSUPPORTED("unsupported"),
    /** Something failed that nobody meant to fail. */
    INTERNAL("internal");

    private final String wire;

    ErrorCode(String wire) {
        this.wire = wire;
    }

    /** The code as it is written on the wire and on the terminal. */
    public String wire() {
        return wire;
    }

    /** The code written as {@code text}, if there is one. */
    public static Optional<ErrorCode> fromWire(String text) {
        for (ErrorCode code : values()) {
            if (code.wire.equals(text)) {
                return Optional.of(code);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return wire;
    }
}
