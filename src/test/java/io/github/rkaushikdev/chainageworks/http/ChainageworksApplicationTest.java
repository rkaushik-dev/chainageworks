package io.github.rkaushikdev.chainageworks.http;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

/** The API served by the real application on a real socket, as the command line starts it. */
class ChainageworksApplicationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static Service service;
    private static ConfigurableApplicationContext application;

    @BeforeAll
    static void start() {
        service = new Service(sampleJob(), Config.DEFAULT);
        application = ChainageworksApplication.run(service, "--server.port=0");
    }

    @AfterAll
    static void stop() {
        application.close();
    }

    private static HttpResponse<String> send(String method, String path) {
        URI uri = URI.create(
                "http://" + Config.DEFAULT.http().host() + ":" + ChainageworksApplication.port(application) + path);
        try {
            return HTTP.send(
                    HttpRequest.newBuilder(uri)
                            .method(method, HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException failed) {
            throw new AssertionError("the server did not answer " + method + " " + path, failed);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for " + method + " " + path, interrupted);
        }
    }

    @Test
    void listensOnTheFreePortItWasGiven() {
        assertThat(ChainageworksApplication.port(application)).isPositive();
        HttpResponse<String> health = send("GET", "/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(health.headers().firstValue("content-type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
    }

    @Test
    void putsARequestIdOnEveryAnswer() {
        HttpResponse<String> first = send("GET", "/health");
        HttpResponse<String> missing = send("GET", "/insurances");
        assertThat(first.headers().firstValue("x-request-id")).isPresent();
        assertThat(missing.headers().firstValue("x-request-id")).isPresent();
        assertThat(missing.headers().firstValue("x-request-id"))
                .isNotEqualTo(first.headers().firstValue("x-request-id"));
    }

    @Test
    void answersAnUnknownRouteInTheApiShape() {
        HttpResponse<String> response = send("GET", "/insurances");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEqualTo("{\"code\":\"not_found\",\"message\":\"no such route\",\"details\":{}}");
    }

    @Test
    void answersAWrongMethodWithTheMethodsThatWork() {
        HttpResponse<String> response = send("POST", "/job");
        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("allow")).hasValue("GET, HEAD");
        assertThat(send("OPTIONS", "/job").statusCode()).isEqualTo(405);
    }

    @Test
    void listsTheSameRoutesAsTheInProcessClient() {
        HttpResponse<String> served = send("GET", "/routes");
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.body()).contains("\"/health\"");
        assertThat(served.body())
                .isEqualTo(ApiClient.over(service).get("/routes").body());
    }

    @Test
    void waitsUntilItIsClosedAndClosesWhenTheWaitIsInterrupted() throws Exception {
        ConfigurableApplicationContext closedElsewhere = ChainageworksApplication.run(service, "--server.port=0");
        Thread waiting = Thread.ofPlatform().start(() -> {
            try {
                ChainageworksApplication.awaitClose(closedElsewhere);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        closedElsewhere.close();
        waiting.join(TimeUnit.SECONDS.toMillis(10));
        assertThat(waiting.isAlive()).isFalse();

        ConfigurableApplicationContext interruptedWait = ChainageworksApplication.run(service, "--server.port=0");
        AtomicBoolean sawTheInterrupt = new AtomicBoolean();
        Thread stopped = Thread.ofPlatform().start(() -> {
            try {
                ChainageworksApplication.awaitClose(interruptedWait);
            } catch (InterruptedException interrupted) {
                sawTheInterrupt.set(true);
            }
        });
        stopped.interrupt();
        stopped.join(TimeUnit.SECONDS.toMillis(10));
        assertThat(sawTheInterrupt).isTrue();
        assertThat(interruptedWait.isActive()).isFalse();
    }

    @Test
    void leavesTheLoggingOfTheProcessAlone() {
        assertThat(application.containsBean(LoggingApplicationListener.LOGGING_SYSTEM_BEAN_NAME))
                .isFalse();
        assertThat(application.containsBean("service")).isTrue();
    }
}
