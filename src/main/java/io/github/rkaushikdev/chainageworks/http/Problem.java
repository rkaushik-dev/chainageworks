package io.github.rkaushikdev.chainageworks.http;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.ErrorCode;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.Map;
import java.util.Objects;

/**
 * The body every failing request comes back with.
 *
 * <p>One shape for everything, so a client never has to guess. The code is the domain's own machine
 * code rather than the status, because two different problems can share a status and only the code
 * tells them apart.
 *
 * @param code the machine code
 * @param message what went wrong, for a person
 * @param details what was wrong, by name
 */
public record Problem(ErrorCode code, String message, Map<String, Object> details) {

    public Problem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        details = Details.copyOf(details);
    }

    /** The problem a domain failure describes. */
    public static Problem of(CwException failure) {
        return new Problem(failure.code(), failure.getMessage(), failure.details());
    }

    /** The body as it goes over the wire: {@code {code, message, details}}. */
    public ObjectNode toJson() {
        ObjectNode json = Json.object();
        json.put("code", code.wire());
        json.put("message", message);
        json.set("details", Json.valueOf(details));
        return json;
    }

    /** The status a failure with {@code code} is answered with. */
    public static int statusFor(ErrorCode code) {
        return switch (code) {
            case BAD_INPUT -> 400;
            case NOT_FOUND -> 404;
            case CONFLICT -> 409;
            case UNSUPPORTED -> 501;
            case INTERNAL -> 500;
        };
    }

    /**
     * Turn anything thrown anywhere below into a status and a body.
     *
     * <p>Nothing else in the HTTP layer is allowed to choose a status. That is what keeps the mapping
     * honest: a new error code shows up here as a missing case in one switch rather than as a 500 from a
     * route nobody thought about. Anything that is not one of the job's own failures, including a
     * refusal from the web framework itself, is an internal failure.
     */
    public static Failure toFailure(Object error) {
        Problem body = of(CwException.from(error));
        return new Failure(statusFor(body.code()), body);
    }

    /**
     * A failure as it is answered: a status and a body.
     *
     * @param status the HTTP status
     * @param body the problem
     */
    public record Failure(int status, Problem body) {

        public Failure {
            Objects.requireNonNull(body, "body");
        }

        /** True for everything the caller can fix. */
        public boolean isClientFailure() {
            return status >= 400 && status < 500;
        }

        /** A one line summary for the request log: {@code 404 not_found: no package with id WP-99}. */
        public String describe() {
            return status + " " + body.code().wire() + ": " + body.message();
        }
    }
}
