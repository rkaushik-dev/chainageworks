package io.github.rkaushikdev.chainageworks.http;

import static io.github.rkaushikdev.chainageworks.support.CliHarness.runCommand;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.http.ApiClient.Answer;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The delay, defects and payment reports over the API and the command line, on a job that carries the commercial
 * registers and on one that does not.
 */
class CommercialSurfacesTest {

    private static final ApiClient APP = ApiClient.over(commercialJob());
    private static final ApiClient PLAIN = ApiClient.over(sampleJob());

    private static final List<String> SUFFIXES = List.of("progress", "certificate", "delay", "defects", "payments");

    private static Answer get(String url) {
        return APP.get(url);
    }

    private static Run commercial(String... argv) {
        return runCommand(List.of(argv), Options.none().withJob(commercialJob()));
    }

    private static List<String> lines(Run run) {
        return Text.split(run.out(), "\n");
    }

    @Nested
    class GetValuationsNumberDelay {

        @Test
        void answersWithThePositionAndTheEvents() {
            JsonNode body = get("/valuations/3/delay").json();
            assertThat(body.get("asAt").asText()).isEqualTo("2025-03-25");
            assertThat(body.get("awardedDays").isNumber()).isTrue();
            assertThat(body.get("awardedDays").doubleValue()).isEqualTo(8);
            assertThat(body.get("barred")).isEqualTo(Json.valueOf(List.of("DE-02")));
            assertThat(body.get("events")).hasSize(2);
        }

        @Test
        void keepsTheClaimedDaysApartFromTheAwardedDays() {
            JsonNode body = get("/valuations/3/delay").json();
            assertThat(body.get("claimedDays").doubleValue())
                    .isGreaterThan(body.get("awardedDays").doubleValue());
        }

        @Test
        void answers204WhenTheContractFixesNoCompletionDate() {
            Answer response = PLAIN.get("/valuations/3/delay");
            assertThat(response.status()).isEqualTo(204);
            assertThat(response.body()).isEmpty();
        }
    }

    @Nested
    class GetValuationsNumberDefects {

        @Test
        void summarisesWhatIsOpenAndWhatItBlocks() {
            JsonNode body = get("/valuations/3/defects").json();
            assertThat(body.get("block").asText()).isEqualTo("period_not_ended");
            assertThat(body.get("summary").get("outstanding").doubleValue()).isEqualTo(2);
            assertThat(body.get("summary").get("overdue").doubleValue()).isEqualTo(2);
            assertThat(body.get("overdue")).isEqualTo(Json.valueOf(List.of("DF-01", "DF-02")));
        }

        @Test
        void givesTheChainageOfADefectThatHasOne() {
            JsonNode outstanding = get("/valuations/3/defects").json().get("outstanding");
            assertThat(outstanding.get(0).get("extent").asText()).isEqualTo("12+400.000 to 12+600.000");
            assertThat(outstanding.get(1).has("extent")).isTrue();
            assertThat(outstanding.get(1).get("extent").isNull()).isTrue();
        }

        @Test
        void answers204WhenTheContractFixesNoDefectsPeriod() {
            Answer response = PLAIN.get("/valuations/3/defects");
            assertThat(response.status()).isEqualTo(204);
        }
    }

    @Nested
    class GetValuationsNumberPayments {

        @Test
        void valuesEveryOrderAtTheValuationCutOff() {
            JsonNode orders = get("/valuations/3/payments").json().get("orders");
            assertThat(orders).hasSize(2);
            assertThat(orders.get(0).get("certified").get("amount").doubleValue())
                    .isEqualTo(80_750);
            assertThat(orders.get(0).get("remaining").get("amount").doubleValue())
                    .isEqualTo(169_250);
            assertThat(orders.get(1).get("certified").get("amount").doubleValue())
                    .isZero();
        }

        @Test
        void namesTheWindowThatClosedUnanswered() {
            JsonNode body = get("/valuations/3/payments").json();
            assertThat(body.get("exposure").asText()).isEqualTo("£40,000.00");
            ObjectNode entry = Json.object()
                    .put("applicationId", "SA-01")
                    .put("subcontractId", "SC-EARTH")
                    .put("contractor", "Dowsett Plant Hire")
                    .put("appliedOn", "2025-01-25")
                    .put("noticeBy", "2025-03-01")
                    .put("amount", "£40,000.00")
                    .put("missed", true);
            assertThat(body.get("diary")).isEqualTo(Json.array().add(entry));
        }

        @Test
        void isAnEmptyPositionOnAJobWithNoOrders() {
            JsonNode body = PLAIN.get("/valuations/3/payments").json();
            assertThat(body.get("orders").isArray()).isTrue();
            assertThat(body.get("orders")).isEmpty();
            assertThat(body.get("diary").isArray()).isTrue();
            assertThat(body.get("diary")).isEmpty();
        }
    }

    @Nested
    class GetJobCountsTheCommercialRegisters {

        @Test
        void reportsTheNewRegistersAlongsideTheOldOnes() {
            JsonNode counts = get("/job").json().get("counts");
            assertThat(counts.get("delayEvents").doubleValue()).isEqualTo(2);
            assertThat(counts.get("defects").doubleValue()).isEqualTo(2);
            assertThat(counts.get("subcontracts").doubleValue()).isEqualTo(2);
            assertThat(counts.get("paymentApplications").doubleValue()).isEqualTo(2);
        }
    }

    @Nested
    class CwDelay {

        @Test
        void printsTheExtensionOfTimePosition() {
            Run run = commercial("delay", "3");
            assertThat(run.code()).isZero();
            assertThat(lines(run).get(0)).isEqualTo("extension of time position at 2025-03-25");
            assertThat(run.out()).containsPattern("days awarded +8");
        }

        @Test
        void saysSoWhenTheContractFixesNoCompletionDate() {
            Run run = runCommand("delay", "3");
            assertThat(run.code()).isZero();
            assertThat(run.out()).isEqualTo("the contract fixes no completion date");
        }

        @Test
        void printsNullAsJsonRatherThanAnEmptyReport() {
            Run run = runCommand("--json", "delay", "3");
            assertThat(run.json().isNull()).isTrue();
        }
    }

    @Nested
    class CwDefects {

        @Test
        void printsTheDefectsPosition() {
            Run run = commercial("defects", "3");
            assertThat(lines(run).get(0)).isEqualTo("defects at 2025-03-25");
            assertThat(run.out()).containsPattern("outstanding +2");
            assertThat(run.out()).contains("DEF/2025/002");
        }

        @Test
        void saysSoWhenTheContractFixesNoDefectsPeriod() {
            Run run = runCommand("defects", "3");
            assertThat(run.out()).isEqualTo("the contract fixes no defects period");
        }
    }

    @Nested
    class CwPayments {

        @Test
        void printsTheOrdersAndTheNoticeDiary() {
            Run run = commercial("payments", "3");
            assertThat(lines(run).get(0)).isEqualTo("subcontract payments at 2025-03-25");
            assertThat(run.out()).contains("notice diary");
            assertThat(run.out()).containsPattern("notices missed +1");
            assertThat(run.out()).contains("Dowsett Plant Hire");
        }

        @Test
        void printsTheOrderCountAsJson() {
            JsonNode json = commercial("--json", "payments", "3").json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.asText()).isEqualTo("2");
        }

        @Test
        void printsAnEmptyPositionRatherThanFailingOnAJobWithNoOrders() {
            Run run = runCommand("payments", "3");
            assertThat(run.code()).isZero();
            assertThat(run.out()).containsPattern("orders +0");
        }
    }

    @Nested
    class EveryValuationReportAnswersTheSameWay {

        @Test
        void answers200OnTheLoadedJob() {
            for (String suffix : SUFFIXES) {
                Answer response = get("/valuations/3/" + suffix);
                assertThat(response.status()).as(suffix).isEqualTo(200);
            }
        }

        @Test
        void complainsAboutAValuationNumberThatIsNotOne() {
            for (String suffix : SUFFIXES) {
                Answer response = get("/valuations/nought/" + suffix);
                assertThat(response.status()).as(suffix).isEqualTo(400);
                assertThat(response.json().get("code").asText()).as(suffix).isEqualTo("bad_input");
            }
        }

        @Test
        void hasNoRouteUnderANameNobodyReportsOn() {
            Answer response = get("/valuations/3/insurance");
            assertThat(response.status()).isEqualTo(404);
        }
    }

    @Nested
    class EveryValuationCommandAnswersTheSameWay {

        @Test
        void takesTheValuationNumberAsItsOnlyArgument() {
            for (String name : SUFFIXES) {
                Run run = commercial(name, "3");
                assertThat(run.code()).as(name).isZero();
                assertThat(run.out()).as(name).isNotEmpty();
            }
        }

        @Test
        void refusesACommandWithNoValuationNumber() {
            Run run = runCommand("payments");
            assertThat(run.code()).isEqualTo(1);
        }
    }
}
