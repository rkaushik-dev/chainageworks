package io.github.rkaushikdev.chainageworks.cli;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.emptyJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.http.ChainageworksApplication;
import io.github.rkaushikdev.chainageworks.store.FileStore;
import io.github.rkaushikdev.chainageworks.store.Job;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

/** The server's entry point, run on a thread the way the process runs it, on any free port. */
class ServeTest {

    @TempDir
    Path dir;

    private Map<String, String> envFor(Job job) throws IOException {
        Path file = dir.resolve("job.json");
        FileStore.save(job, file);
        return Map.of("CHAINAGEWORKS_JOB_FILE", file.toString());
    }

    @Test
    void servesTheJobTheEnvironmentNamesUntilItIsClosed() throws Exception {
        Map<String, String> env = envFor(sampleJob());
        List<String> err = new CopyOnWriteArrayList<>();
        CompletableFuture<ConfigurableApplicationContext> started = new CompletableFuture<>();
        AtomicInteger code = new AtomicInteger(-1);
        Thread server = Thread.ofPlatform().start(() -> {
            try {
                code.set(Serve.run(env, err::add, started::complete, List.of("--server.port=0")));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException failed) {
                started.completeExceptionally(failed);
            }
        });

        ConfigurableApplicationContext application = started.get(60, TimeUnit.SECONDS);
        try {
            URI health = URI.create("http://127.0.0.1:" + ChainageworksApplication.port(application) + "/health");
            HttpResponse<String> answer = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder(health).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(answer.statusCode()).isEqualTo(200);
            assertThat(answer.body()).isEqualTo("{\"status\":\"ok\"}");
        } finally {
            application.close();
        }
        server.join(TimeUnit.SECONDS.toMillis(10));
        assertThat(server.isAlive()).isFalse();
        assertThat(code.get()).isZero();
        assertThat(err.getFirst()).isEqualTo("chainageworks serving " + env.get("CHAINAGEWORKS_JOB_FILE"));
        assertThat(err).contains("job          A1-IMP A1 improvement, section 2", "listening    127.0.0.1:8730");
        assertThat(err.getLast()).isEqualTo("ready");
    }

    @Test
    void refusesAJobThatCannotBeValuedAndServesNothing() throws Exception {
        Map<String, String> env = envFor(emptyJob());
        List<String> err = new CopyOnWriteArrayList<>();
        List<ConfigurableApplicationContext> served = new CopyOnWriteArrayList<>();

        int code = Serve.run(env, err::add, served::add, List.of("--server.port=0"));

        assertThat(code).isEqualTo(1);
        assertThat(served).isEmpty();
        assertThat(err).anyMatch(line -> line.startsWith("blockers     no bill has been loaded"));
        assertThat(err.getLast()).isEqualTo("refusing to serve, the job cannot be valued");
    }

    @Test
    void failsWithoutAJobFileToServe() {
        Map<String, String> env =
                Map.of("CHAINAGEWORKS_JOB_FILE", dir.resolve("missing.json").toString());
        assertThatThrownBy(() -> Serve.run(env, line -> {}, application -> {}, List.of()))
                .isInstanceOf(BadInputException.class)
                .hasMessage("no job file at that path");
    }
}
