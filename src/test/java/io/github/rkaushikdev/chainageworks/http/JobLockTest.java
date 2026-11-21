package io.github.rkaushikdev.chainageworks.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Reads share the job, and a request that changes it has the job to itself. */
class JobLockTest {

    private static final long WAIT_SECONDS = 5;

    private final JobLock lock = new JobLock();
    private final ExecutorService clients = Executors.newFixedThreadPool(2);

    @AfterEach
    void stopTheClients() {
        clients.shutdownNow();
    }

    /** Send {@code method} through the lock; the request is inside it from {@code inside} until {@code done}. */
    private CompletableFuture<Void> send(String method, CountDownLatch inside, CountDownLatch done) {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        lock.doFilter(
                                new MockHttpServletRequest(method, "/job"),
                                new MockHttpServletResponse(),
                                (in, out) -> {
                                    inside.countDown();
                                    try {
                                        done.await(WAIT_SECONDS, TimeUnit.SECONDS);
                                    } catch (InterruptedException interrupted) {
                                        Thread.currentThread().interrupt();
                                    }
                                });
                    } catch (Exception failed) {
                        throw new IllegalStateException(failed);
                    }
                },
                clients);
    }

    @Test
    void letsTwoReadsRunSideBySide() throws Exception {
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch secondInside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> first = send("GET", firstInside, release);
        assertThat(firstInside.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> second = send("HEAD", secondInside, release);
        assertThat(secondInside.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        CompletableFuture.allOf(first, second).get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void holdsAReadOffWhileAnImportHasTheJob() throws Exception {
        CountDownLatch importInside = new CountDownLatch(1);
        CountDownLatch readInside = new CountDownLatch(1);
        CountDownLatch importDone = new CountDownLatch(1);
        CountDownLatch readDone = new CountDownLatch(0);
        CompletableFuture<Void> post = send("POST", importInside, importDone);
        assertThat(importInside.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> read = send("GET", readInside, readDone);
        assertThat(readInside.await(300, TimeUnit.MILLISECONDS)).isFalse();
        importDone.countDown();
        assertThat(readInside.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        CompletableFuture.allOf(post, read).get(WAIT_SECONDS, TimeUnit.SECONDS);
    }
}
