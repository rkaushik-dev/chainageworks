package io.github.rkaushikdev.chainageworks.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets reads of the job run side by side and gives every other request the job to itself.
 *
 * <p>The job is one document held in memory, and its registers are plain collections. A report reads
 * a dozen of them to build one answer, so an import landing half way through would hand back a
 * certificate valued partly before the import and partly after it. Requests that only read, {@code GET}
 * and {@code HEAD}, share the job; an import waits until they are done and holds the readers off until
 * it is.
 */
@Component
final class JobLock extends OncePerRequestFilter {

    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean reads = HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod());
        Lock held = reads ? lock.readLock() : lock.writeLock();
        held.lock();
        try {
            chain.doFilter(request, response);
        } finally {
            held.unlock();
        }
    }
}
