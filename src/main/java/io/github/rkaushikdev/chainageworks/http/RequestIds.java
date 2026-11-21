package io.github.rkaushikdev.chainageworks.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Every response carries the request id it was answered under, as {@code x-request-id}.
 *
 * <p>Putting it on the response is what makes it useful, because the only time anybody wants it is
 * when they are quoting a failure back and the log is the other half of the story. So it goes on
 * before the request is handled, and a route that failed, a route that does not exist and a refused
 * body all carry one as well.
 *
 * <p>Ids count up from {@code req-1} for each server, written in base 36.
 */
@Component
final class RequestIds extends OncePerRequestFilter {

    /** The response header the id is written to. */
    static final String HEADER = "x-request-id";

    private final AtomicLong last = new AtomicLong();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, "req-" + Long.toString(last.incrementAndGet(), Character.MAX_RADIX));
        chain.doFilter(request, response);
    }
}
