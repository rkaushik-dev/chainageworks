package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarning;
import io.github.rkaushikdev.chainageworks.domain.risk.RiskOwner;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.reporting.RiskReport;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.CliHarness;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.support.JobDocuments;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The early warning register at a valuation, through the service, the report, the API, the CLI and the job file. */
class RiskRegisterTest {

    private static final ApiClient API = ApiClient.over(new Service(commercialJob(), Config.DEFAULT));

    private static Service service() {
        return new Service(commercialJob(), Config.DEFAULT);
    }

    private static Run commercial(String... argv) {
        return CliHarness.runCommand(List.of(argv), Options.none().withJob(commercialJob()));
    }

    @Nested
    class RisksFor {

        private final EarlyWarning.Position position = service().risksFor(3);

        @Test
        void takesTheRegisterAtTheValuationCutOff() {
            assertThat(position.open()).extracting(EarlyWarning::id).containsExactly("EW-01", "EW-02");
        }

        @Test
        void keepsTheExpectedCostApartFromTheWorstCase() {
            assertThat(position.expected().major()).isEqualTo(39_000);
            assertThat(position.worstCase().major()).isEqualTo(80_000);
        }

        @Test
        void countsOnlyWhatTheContractorCarries() {
            assertThat(position.carried().major()).isEqualTo(15_000);
        }

        @Test
        void namesTheMeetingNobodyHeld() {
            assertThat(position.overdueMeetings()).extracting(EarlyWarning::id).containsExactly("EW-02");
        }

        @Test
        void addsTheWorkingDaysAtRisk() {
            assertThat(position.delayDays()).isEqualTo(17);
        }

        @Test
        void leavesOutAWarningRaisedAfterTheCutOff() {
            assertThat(service().risksFor(1).open()).isEmpty();
        }

        @Test
        void isEmptyOnAJobWithNoRegister() {
            EarlyWarning.Position bare = new Service(sampleJob(), Config.DEFAULT).risksFor(3);
            assertThat(bare.open()).isEmpty();
            assertThat(bare.expected().minor()).isZero();
            assertThat(bare.unowned()).isEmpty();
        }
    }

    @Nested
    class RenderRiskReport {

        @Test
        void opensWithTheTotalsAndTablesTheRegisterByExpectedCost() {
            String text = RiskReport.render(service().risksFor(3));
            assertThat(Text.split(text, "\n").get(0)).isEqualTo("early warnings at 2025-03-25");
            assertThat(text).containsPattern("expected cost +£39,000\\.00");
            assertThat(text).containsPattern("meetings overdue +1");
            assertThat(text).contains("12+400.000 to 12+600.000");
        }

        @Test
        void leavesTheTableOutOnAnEmptyRegister() {
            String text = RiskReport.render(new Service(sampleJob(), Config.DEFAULT).risksFor(3));
            assertThat(text).containsPattern("open +0");
            assertThat(text).doesNotContain("if it happens");
        }
    }

    @Nested
    class MeetingsToArrange {

        @Test
        void namesTheMeetingsSomebodyHasToArrange() {
            List<String> chase = RiskReport.meetingsToArrange(service().risksFor(3));
            assertThat(chase).containsExactly("EW/2025/002 raised 2025-02-20, no meeting held");
        }
    }

    @Nested
    class GetValuationsNumberRisks {

        @Test
        void answersWithTheRegisterAndWhatItMightCost() {
            JsonNode body = API.get("/valuations/3/risks").json();
            assertThat(body.get("warnings")).hasSize(2);
            assertThat(body.get("expected").get("amount").doubleValue()).isEqualTo(39_000);
            assertThat(body.get("warnings").get(0).get("expected").get("amount").doubleValue())
                    .isEqualTo(24_000);
            assertThat(body.get("overdueMeetings")).isEqualTo(Json.valueOf(List.of("EW-02")));
            assertThat(body.get("unowned")).isEqualTo(Json.array());
        }

        @Test
        void countsTheRegisterOnTheJob() {
            JsonNode counts = API.get("/job").json().get("counts");
            assertThat(counts.get("earlyWarnings").doubleValue()).isEqualTo(2);
        }
    }

    @Nested
    class CwRisks {

        @Test
        void printsTheRegister() {
            Run run = commercial("risks", "3");
            assertThat(run.code()).isZero();
            assertThat(run.out()).contains("EW/2025/001");
        }

        @Test
        void printsTheOpenCountAsJson() {
            JsonNode json = commercial("--json", "risks", "3").json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.asText()).isEqualTo("2");
        }
    }

    @Nested
    class TheRiskRegisterThroughTheJobFile {

        @Test
        void roundTripsTheWarningsAndTheMeetingWindow() {
            Job again = JobDocuments.throughText(commercialJob());
            assertThat(again.earlyWarnings().count()).isEqualTo(2);
            assertThat(again.earlyWarnings().require("EW-01").owner()).isEqualTo(RiskOwner.EMPLOYER);
            assertThat(again.earlyWarnings().require("EW-01").extent()).isPresent();
            assertThat(again.earlyWarnings().require("EW-02").meetingOn()).isEmpty();
            assertThat(again.settings().riskMeetingDays()).isEqualTo(14);
        }

        @Test
        void givesTheSamePositionOffTheReloadedJob() {
            Job again = JobDocuments.throughText(commercialJob());
            assertThat(new Service(again, Config.DEFAULT).risksFor(3).expected().major())
                    .isEqualTo(39_000);
        }

        @Test
        void carriesAJobWithNoRegisterThroughAsEmpty() {
            Job again = JobDocuments.throughText(sampleJob());
            assertThat(again.earlyWarnings().count()).isZero();
        }
    }

    @Nested
    class CwDoctorWithARegister {

        @Test
        void stillFindsNothingWrong() {
            Run run = commercial("doctor");
            assertThat(run.code()).isZero();
        }
    }
}
