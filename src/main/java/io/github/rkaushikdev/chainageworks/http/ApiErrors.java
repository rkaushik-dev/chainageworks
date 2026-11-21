package io.github.rkaushikdev.chainageworks.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.ErrorCode;
import io.github.rkaushikdev.chainageworks.http.Problem.Failure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Every failure the server answers, in the one shape {@link Problem} defines.
 *
 * <p>A request no route answers is told apart from one that reached a known path with a method that
 * path does not take. The second is a 405 naming the methods that would have worked, rather than a
 * 404: the difference matters to a client that is one line of code away from working. Everything else
 * that goes wrong, the job's own refusals and anything the framework refuses, goes through {@link
 * Problem#toFailure}, which is the only place a status is chosen.
 */
@RestControllerAdvice
final class ApiErrors {

    @ExceptionHandler({
        NoHandlerFoundException.class,
        NoResourceFoundException.class,
        HttpRequestMethodNotSupportedException.class
    })
    ResponseEntity<JsonNode> noRoute(HttpServletRequest request) {
        String url = request.getQueryString() == null
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString();
        List<String> allowed = RouteTable.of(request).methodsFor(url);
        if (allowed.isEmpty()) {
            Problem missing = new Problem(ErrorCode.NOT_FOUND, "no such route", Details.none());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(missing.toJson());
        }
        String methods = String.join(", ", allowed);
        Problem wrongMethod = new Problem(
                ErrorCode.UNSUPPORTED,
                "that route answers " + methods,
                Details.of("method", request.getMethod(), "url", url));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, methods)
                .body(wrongMethod.toJson());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<JsonNode> failed(Exception error) {
        Failure failure = Problem.toFailure(error);
        return ResponseEntity.status(failure.status()).body(failure.body().toJson());
    }
}
