package io.github.rkaushikdev.chainageworks.http;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.fluctuatingJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.haulingJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.http.ApiClient.Answer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Every route the listing names, answered over the jobs the fixtures build, and the refusals around them. */
class RoutesTest {

    private static final Map<String, ApiClient> JOBS = Map.of(
            "sample", ApiClient.over(sampleJob()),
            "hauling", ApiClient.over(haulingJob()),
            "fluctuating", ApiClient.over(fluctuatingJob()));

    private static List<JsonNode> listed() {
        List<JsonNode> routes = new ArrayList<>();
        JOBS.get("sample").get("/routes").json().get("routes").forEach(routes::add);
        return routes;
    }

    private static List<String> pathsAnswering(String method) {
        return listed().stream()
                .filter(route -> route.get("methods").toString().contains("\"" + method + "\""))
                .map(route -> route.get("path").asText())
                .toList();
    }

    static Stream<Arguments> everyReadingRoute() {
        return JOBS.keySet().stream().sorted().flatMap(job -> pathsAnswering("GET").stream()
                .map(path -> Arguments.of(
                        job,
                        path.replace(":number", "3")
                                .replace(":packageId", "WP-01")
                                .replace(":id", "WP-01"))));
    }

    @Nested
    class TheListing {

        @Test
        void namesEveryReportAndEveryImport() {
            assertThat(pathsAnswering("GET"))
                    .contains("/health", "/packages/:id", "/valuations/:number/strip/:packageId", "/curve")
                    .hasSize(24);
            assertThat(pathsAnswering("POST"))
                    .contains("/import/records", "/import/pay-less")
                    .hasSize(9);
        }

        @ParameterizedTest(name = "{0} job: GET {1}")
        @MethodSource("io.github.rkaushikdev.chainageworks.http.RoutesTest#everyReadingRoute")
        void answersEveryReadingRouteItLists(String job, String path) {
            Answer answer = JOBS.get(job).get(path);
            assertThat(answer.status()).isIn(200, 204);
            if (answer.status() == 200) {
                assertThat(answer.json().isContainerNode()).isTrue();
            } else {
                assertThat(answer.body()).isEmpty();
            }
        }
    }

    @Nested
    class Refusals {

        @Test
        void refusesACountNamedTwice() {
            Answer answer = JOBS.get("sample").get("/schedule?periods=2&periods=3");
            assertThat(answer.status()).isEqualTo(400);
            assertThat(answer.body())
                    .isEqualTo("{\"code\":\"bad_input\",\"message\":\"periods has to be a whole number from one\","
                            + "\"details\":{\"what\":\"periods\"}}");
        }

        @Test
        void refusesALateCountBelowNought() {
            Answer answer = JOBS.get("sample").get("/cash?late=-1");
            assertThat(answer.status()).isEqualTo(400);
            assertThat(answer.json().get("details").get("value").asText()).isEqualTo("-1");
        }

        @Test
        void readsAnEmptyPostAsAnEmptySheet() {
            Answer answer = ApiClient.over(sampleJob()).send("POST", "/import/diary");
            assertThat(answer.status()).isEqualTo(200);
            assertThat(answer.json().get("rowsRead").intValue()).isZero();
        }

        @Test
        void refusesABodyThatIsNotText() {
            Answer answer = JOBS.get("sample")
                    .send(MockMvcRequestBuilders.request(HttpMethod.POST, "/import/records")
                            .contentType("application/json")
                            .content("{}".getBytes(StandardCharsets.UTF_8)));
            assertThat(answer.status()).isEqualTo(500);
            assertThat(answer.json().get("message").asText()).isEqualTo("Unsupported Media Type: application/json");
            assertThat(answer.header(RequestIds.HEADER)).isPresent();
        }

        @Test
        void answersOptionsWithTheMethodsThePathTakes() {
            Answer answer = JOBS.get("sample").send("OPTIONS", "/valuations/3/progress");
            assertThat(answer.status()).isEqualTo(405);
            assertThat(answer.header("allow")).hasValue("GET, HEAD");
        }
    }
}
