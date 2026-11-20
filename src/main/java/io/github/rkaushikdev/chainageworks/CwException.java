package io.github.rkaushikdev.chainageworks;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.Map;

/**
 * Every failure this package raises on purpose: a message for a person, a stable {@link
 * ErrorCode} for a program, and a few details naming what was wrong.
 */
public class CwException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public CwException(ErrorCode code, String message) {
        this(code, message, Details.none());
    }

    public CwException(ErrorCode code, String message, Map<String, ?> details) {
        super(message);
        this.code = code;
        this.details = Details.copyOf(details);
    }

    /** The machine code. */
    public ErrorCode code() {
        return code;
    }

    /** What was wrong, by name. Never null; empty when there is nothing to add. */
    public Map<String, Object> details() {
        return details;
    }

    /** The name of the concrete failure, which is what a log line or a stack calls it. */
    public String name() {
        return getClass().getSimpleName();
    }

    /** The shape used by both the JSON API and the {@code --json} flag of the command line. */
    public ObjectNode toJson() {
        ObjectNode json = Json.object();
        json.put("code", code.wire());
        json.put("message", getMessage());
        json.set("details", Json.valueOf(details));
        return json;
    }

    /** True when {@code value} is one of the failures this package raises. */
    public static boolean isCwException(Object value) {
        return value instanceof CwException;
    }

    /**
     * Turn anything thrown by a lower layer into a {@code CwException}, so the outer boundary only
     * ever sees one kind of failure. One that already is passes through untouched, because the
     * code it carries is more specific than anything that could be made up here.
     */
    public static CwException from(Object value) {
        if (value instanceof CwException known) {
            return known;
        }
        if (value instanceof Throwable thrown) {
            String message = thrown.getMessage() == null ? "" : thrown.getMessage();
            return new CwException(
                    ErrorCode.INTERNAL,
                    message,
                    Details.of("cause", thrown.getClass().getSimpleName()));
        }
        return new CwException(ErrorCode.INTERNAL, String.valueOf(value));
    }
}
