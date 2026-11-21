package io.github.rkaushikdev.chainageworks.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Keeps every request to what the route listing says the server answers.
 *
 * <p>An {@code OPTIONS} request is answered like any other method no route takes, with a 405 naming
 * the methods that would have worked, rather than with a listing of its own. A path parameter longer
 * than {@value #LONGEST_PARAMETER} characters means no route was asked for: a csv import names one
 * register and a package id is a short code, so a long path is a mistake rather than a request.
 */
final class RouteGuard implements HandlerInterceptor {

    /** The longest path parameter a route is handed. */
    static final int LONGEST_PARAMETER = 200;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws HttpRequestMethodNotSupportedException, NoHandlerFoundException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            throw new HttpRequestMethodNotSupportedException(request.getMethod());
        }
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables
                && variables.values().stream()
                        .anyMatch(value -> String.valueOf(value).length() > LONGEST_PARAMETER)) {
            throw new NoHandlerFoundException(
                    request.getMethod(), request.getRequestURI(), new ServletServerHttpRequest(request).getHeaders());
        }
        return true;
    }
}
