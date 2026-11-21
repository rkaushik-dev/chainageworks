package io.github.rkaushikdev.chainageworks.http;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.http.ApiClient.Answer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The API over the sample job, route by route. */
class HttpApiTest {

    private static final ApiClient API = ApiClient.over(new Service(sampleJob(), Config.DEFAULT));

    private static Answer get(String url) {
        return API.get(url);
    }

    @Nested
    class GetHealth {

        @Test
        void answersOk() {
            Answer response = get("/health");
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"status\":\"ok\"}");
        }
    }

    @Nested
    class GetJob {

        @Test
        void describesTheJobAndCountsEveryRegister() {
            JsonNode body = get("/job").json();
            assertThat(body.get("id").asText()).isEqualTo("A1-IMP");
            assertThat(body.get("contractSum").get("amount").doubleValue()).isEqualTo(312_190);
            JsonNode counts = body.get("counts");
            assertThat(counts.get("packages").intValue()).isEqualTo(3);
            assertThat(counts.get("records").intValue()).isEqualTo(3);
            assertThat(counts.get("signOffs").intValue()).isEqualTo(2);
            assertThat(body.get("blockers")).isEmpty();
        }
    }

    @Nested
    class GetSchedule {

        @Test
        void givesTwelvePeriodsByDefault() {
            assertThat(get("/schedule").json()).hasSize(12);
        }

        @Test
        void honoursAPeriodCount() {
            assertThat(get("/schedule?periods=3").json()).hasSize(3);
        }

        @Test
        void refusesAPeriodCountThatIsNotAWholeNumber() {
            Answer response = get("/schedule?periods=lots");
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.json().get("code").asText()).isEqualTo("bad_input");
        }
    }

    @Nested
    class GetBill {

        @Test
        void givesTheBillInForce() {
            JsonNode body = get("/bill").json();
            assertThat(body.get("currency").asText()).isEqualTo("GBP");
            assertThat(body.get("items")).hasSize(8);
        }

        @Test
        void refusesADateItCannotRead() {
            assertThat(get("/bill?asAt=yesterday").status()).isEqualTo(400);
        }

        @Test
        void refusesADateBeforeAnyBillWasInForce() {
            Answer response = get("/bill?asAt=2024-01-01");
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.json().get("message").asText()).containsPattern("no bill revision");
        }
    }

    @Nested
    class GetPackages {

        @Test
        void listsThemAll() {
            JsonNode body = get("/packages").json();
            assertThat(body).hasSize(3);
            assertThat(body.get(0).get("extent").asText()).isEqualTo("12+000.000 to 12+800.000");
        }

        @Test
        void givesOneById() {
            JsonNode body = get("/packages/WP-02").json();
            assertThat(body.get("profileId").asText()).isEqualTo("bulk-earthworks");
            assertThat(body.get("allocations")).hasSize(3);
        }

        @Test
        void givesA404WithTheDomainCodeForOneItDoesNotHave() {
            Answer response = get("/packages/WP-99");
            assertThat(response.status()).isEqualTo(404);
            JsonNode body = response.json();
            assertThat(body.get("code").asText()).isEqualTo("not_found");
            assertThat(body.get("details").get("kind").asText()).isEqualTo("work package");
            assertThat(body.get("details").get("id").asText()).isEqualTo("WP-99");
        }
    }

    @Nested
    class GetRecords {

        @Test
        void listsTheRecordsThatCountAtADate() {
            assertThat(get("/records?asAt=2025-03-31").json()).hasSize(3);
        }

        @Test
        void leavesOutAnythingNotMeasuredYet() {
            assertThat(get("/records?asAt=2025-03-05").json()).hasSize(1);
        }

        @Test
        void filtersByPackage() {
            assertThat(get("/records?asAt=2025-03-31&package=WP-02").json()).isEmpty();
        }
    }

    @Nested
    class GetValuationProgress {

        @Test
        void reportsThePeriod() {
            JsonNode body = get("/valuations/3/progress").json();
            assertThat(body.get("period").get("number").intValue()).isEqualTo(3);
            assertThat(body.get("earned").get("amount").doubleValue()).isEqualTo(16_152.5);
            assertThat(body.get("packages")).hasSize(3);
        }

        @Test
        void refusesAValuationNumberThatIsNotAWholeNumber() {
            assertThat(get("/valuations/nought/progress").status()).isEqualTo(400);
            assertThat(get("/valuations/0/progress").status()).isEqualTo(400);
        }
    }

    @Nested
    class GetValuationCertificate {

        @Test
        void walksDownToTheAmountDue() {
            JsonNode body = get("/valuations/3/certificate").json();
            assertThat(body.get("amountDue").get("amount").doubleValue()).isEqualTo(15_344.87);
            assertThat(body.get("retention").get("capReached").booleanValue()).isFalse();
        }
    }

    @Nested
    class GetValuationStrip {

        @Test
        void drawsTheStrip() {
            JsonNode body = get("/valuations/3/strip/WP-01").json();
            assertThat(body.get("packageId").asText()).isEqualTo("WP-01");
            assertThat(body.get("rows")).hasSize(4);
        }

        @Test
        void givesA404ForAPackageItDoesNotHave() {
            assertThat(get("/valuations/3/strip/WP-99").status()).isEqualTo(404);
        }
    }

    @Nested
    class GetCurve {

        @Test
        void givesOnePointPerPeriod() {
            assertThat(get("/curve?periods=6").json()).hasSize(6);
        }
    }

    /** Each import gets a job of its own, so what one test loads never reaches another. */
    @Nested
    class PostImportRecords {

        private static final String HEADER = "record,package,code,step,from,to,quantity,unit,measured on,booked by";

        private final ApiClient api = ApiClient.over(new Service(sampleJob(), Config.DEFAULT));

        @Test
        void takesACleanSheetAndAnswers200() {
            Answer response = api.postCsv(
                    "/import/records",
                    String.join("\n", HEADER, "SR-20,WP-02,E423.1,excavate,12+800,13+000,900,m3,2025-03-24,J Foreman"));
            assertThat(response.status()).isEqualTo(200);
            JsonNode body = response.json();
            assertThat(body.get("rowsRead").intValue()).isEqualTo(1);
            assertThat(body.get("accepted").intValue()).isEqualTo(1);
            assertThat(body.get("rejected").intValue()).isZero();
        }

        @Test
        void answers207AndNamesTheLineWhenARowIsWrong() {
            Answer response = api.postCsv(
                    "/import/records",
                    String.join("\n", HEADER, "SR-21,WP-02,nonsense,excavate,12+800,13+000,900,m3,2025-03-24,J"));
            assertThat(response.status()).isEqualTo(207);
            JsonNode body = response.json();
            assertThat(body.get("rejected").intValue()).isEqualTo(1);
            assertThat(body.get("issues").get(0).get("line").intValue()).isEqualTo(2);
        }

        @Test
        void takesADiaryToo() {
            Answer response = api.postCsv("/import/diary", String.join("\n", "date,reason", "2025-03-05,rain"));
            assertThat(response.status()).isEqualTo(200);
        }
    }

    @Nested
    class AnUnknownRoute {

        @Test
        void answers404InTheSameShapeAsEverythingElse() {
            Answer response = get("/nothing-here");
            assertThat(response.status()).isEqualTo(404);
            assertThat(response.body())
                    .isEqualTo("{\"code\":\"not_found\",\"message\":\"no such route\",\"details\":{}}");
        }
    }
}
