package io.github.rkaushikdev.chainageworks;

import java.util.Map;

/** The request is well formed but the current state refuses it. */
public class ConflictException extends CwException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public ConflictException(String message, Map<String, ?> details) {
        super(ErrorCode.CONFLICT, message, details);
    }
}
