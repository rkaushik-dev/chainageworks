package io.github.rkaushikdev.chainageworks;

import java.util.Map;

/** A shape that is known about but not implemented for this contract type. */
public class UnsupportedException extends CwException {

    public UnsupportedException(String message) {
        super(ErrorCode.UNSUPPORTED, message);
    }

    public UnsupportedException(String message, Map<String, ?> details) {
        super(ErrorCode.UNSUPPORTED, message, details);
    }
}
