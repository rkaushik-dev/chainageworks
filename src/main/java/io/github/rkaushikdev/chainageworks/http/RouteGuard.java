package io.github.rkaushikdev.chainageworks.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Keeps every request to the methods the route listing says the server answers.
 *
 * <p>An {@code OPTIONS} request is answered like any other method no route takes, with a 405 naming
 * the methods that would have worked, rather than with a listing of its own that could disagree with
 * {@code /routes}.
 */
final class RouteGuard implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws HttpRequestMethodNotSupportedException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            throw new HttpRequestMethodNotSupportedException(request.getMethod());
        }
        return true;
    }
}
