package io.github.rkaushikdev.chainageworks.http;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.http.ApiClient.Answer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** What the API does around the routes: request ids, wrong methods, the route listing and the body limit. */
class HttpHardeningTest {

    private static final ApiClient API = ApiClient.over(new Service(commercialJob(), Config.DEFAULT));

    @Nested
    class EveryResponseCarriesARequestId {

        @Test
        void putsItOnARouteThatAnswered() {
            assertThat(API.get("/health").header("x-request-id")).isPresent();
        }

        @Test
        void putsItOnARouteThatFailed() {
            Answer response = API.get("/valuations/nought/progress");
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.header("x-request-id")).isPresent();
        }

        @Test
        void givesADifferentOneToEachRequest() {
            Answer first = API.get("/health");
            Answer second = API.get("/health");
            assertThat(first.header("x-request-id")).isNotEqualTo(second.header("x-request-id"));
        }
    }

    @Nested
    class APathThatExistsReachedWithTheWrongMethod {

        @Test
        void answers405AndNamesWhatWouldHaveWorked() {
            Answer response = API.send("POST", "/job");
            assertThat(response.status()).isEqualTo(405);
            assertThat(response.header("allow")).hasValue("GET, HEAD");
            assertThat(response.json().get("code").asText()).isEqualTo("unsupported");
        }

        @Test
        void answers405OnARouteThatTakesAParameter() {
            Answer response = API.send("DELETE", "/valuations/3/progress");
            assertThat(response.status()).isEqualTo(405);
            assertThat(response.json().get("details").get("method").asText()).isEqualTo("DELETE");
        }

        @Test
        void answers405OnAnImportRouteReachedWithAGet() {
            Answer response = API.get("/import/records");
            assertThat(response.status()).isEqualTo(405);
            assertThat(response.header("allow"))
                    .hasValueSatisfying(allow -> assertThat(allow).contains("POST"));
        }

        @Test
        void stillAnswers404OnAPathNothingServes() {
            Answer response = API.get("/insurances");
            assertThat(response.status()).isEqualTo(404);
            assertThat(response.json().get("code").asText()).isEqualTo("not_found");
        }

        @Test
        void answers404RatherThan405OnAPathOfTheWrongDepth() {
            assertThat(API.get("/valuations/3").status()).isEqualTo(404);
        }
    }

    @Nested
    class GetRoutes {

        private static List<JsonNode> listed() {
            List<JsonNode> routes = new ArrayList<>();
            API.get("/routes").json().get("routes").forEach(routes::add);
            return routes;
        }

        private static Optional<JsonNode> route(List<JsonNode> routes, String path) {
            return routes.stream()
                    .filter(route -> route.get("path").asText().equals(path))
                    .findFirst();
        }

        private static List<String> methods(JsonNode route) {
            List<String> methods = new ArrayList<>();
            route.get("methods").forEach(method -> methods.add(method.asText()));
            return methods;
        }

        @Test
        void listsTheRoutesOffTheRouterItself() {
            List<JsonNode> routes = listed();
            assertThat(routes.size()).isGreaterThan(15);
            assertThat(route(routes, "/health")).isPresent();
            assertThat(route(routes, "/import/records")).isPresent();
        }

        @Test
        void namesTheMethodsEachRouteAnswers() {
            List<JsonNode> routes = listed();
            assertThat(methods(route(routes, "/health").orElseThrow())).containsExactly("GET", "HEAD");
            assertThat(methods(route(routes, "/import/records").orElseThrow())).containsExactly("POST");
        }

        @Test
        void joinsANestedRouteBackOntoItsPrefix() {
            List<JsonNode> routes = listed();
            assertThat(route(routes, "/packages/:id")).isPresent();
            assertThat(route(routes, "/:id")).isEmpty();
        }

        @Test
        void listsTheValuationRoutesItActuallyAnswers() {
            long valuations = listed().stream()
                    .filter(route -> route.get("path").asText().startsWith("/valuations/"))
                    .count();
            assertThat(valuations).isGreaterThan(5);
        }
    }

    @Nested
    class ABodyLargerThanTheServerTakes {

        @Test
        void isRefusedRatherThanRead() {
            Answer response = API.postCsv("/import/records", "a".repeat(3 * 1024 * 1024));
            assertThat(response.status()).isGreaterThanOrEqualTo(400);
            assertThat(response.status()).isNotEqualTo(200);
        }

        @Test
        void stillTakesABodyInsideTheLimit() {
            String header = "record,package,code,step,from,to,quantity,unit,measured on,booked by";
            String row = "SR-99,WP-01,E423.1,excavate,12+000,12+100,120,m3,2025-03-06,J Foreman";
            Answer response = API.postCsv("/import/records", String.join("\n", header, row));
            assertThat(response.status()).isEqualTo(200);
        }
    }
}
