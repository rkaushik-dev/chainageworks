package io.github.rkaushikdev.chainageworks;

import java.util.Map;

/** The caller handed over something the domain cannot accept. */
public class BadInputException extends CwException {

    public BadInputException(String message) {
        super(ErrorCode.BAD_INPUT, message);
    }

    public BadInputException(String message, Map<String, ?> details) {
        super(ErrorCode.BAD_INPUT, message, details);
    }
}
