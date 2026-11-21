package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.progress.Lookahead;
import io.github.rkaushikdev.chainageworks.reporting.LookaheadReport;
import io.github.rkaushikdev.chainageworks.support.CliHarness;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The lookahead from a date, through the service, the report, the API and the command line. */
class LookaheadPlanTest {

    private static final ApiClient API = ApiClient.over(new Service(commercialJob(), Config.DEFAULT));

    private static Service service() {
        return new Service(commercialJob(), Config.DEFAULT);
    }

    private static Run commercial(String... argv) {
        return CliHarness.runCommand(List.of(argv), Options.none().withJob(commercialJob()));
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(entry -> values.add(entry.asText()));
        return values;
    }

    @Nested
    class LookaheadFrom {

        @Test
        void runsFourWeeksFromTheDateAndReadsTheJobAllowances() {
            Lookahead plan = service().lookaheadFrom(d("2025-03-26"));
            assertThat(SiteDates.format(plan.to())).isEqualTo("2025-04-22");
            assertThat(plan.workingDays()).isEqualTo(20);
            assertThat(plan.steps()).anyMatch(step -> step.days().isPresent());
        }

        @Test
        void takesTheWeeksAndTheGangsOffTheOptions() {
            Lookahead one = service().lookaheadFrom(d("2025-03-26"), 2);
            Lookahead pushed = service().lookaheadFrom(d("2025-03-26"), 2, 2);
            assertThat(one.workingDays()).isEqualTo(10);
            assertThat(pushed.daysOfWork()).isCloseTo(one.daysOfWork() / 2, byLessThan(5e-7));
        }

        @Test
        void leavesOutAStepWithNothingLeftToBuild() {
            Lookahead plan = service().lookaheadFrom(d("2025-03-26"));
            assertThat(plan.steps())
                    .noneMatch(step ->
                            step.packageId().equals("WP-01") && step.stepId().equals("strip"));
        }

        @Test
        void refusesALookaheadWithNoWeeksInIt() {
            assertThatThrownBy(() -> service().lookaheadFrom(d("2025-03-26"), 0))
                    .hasMessageContaining("at least one week");
        }
    }

    @Nested
    class RenderLookahead {

        @Test
        void opensWithTheWindowAndSplitsReadyFromWaiting() {
            String text = LookaheadReport.render(service().lookaheadFrom(d("2025-03-26")));
            assertThat(Text.split(text, "\n").get(0)).isEqualTo("lookahead 2025-03-26 to 2025-04-22");
            assertThat(text).containsPattern("working days in the window +20");
            assertThat(text).contains("ready to start");
            assertThat(text).contains("waiting");
        }

        @Test
        void namesWhatIsHoldingEachWaitingStep() {
            String text = LookaheadReport.render(service().lookaheadFrom(d("2025-03-26")));
            assertThat(text).containsPattern("held by IP-E[12]");
        }
    }

    @Nested
    class ChaseInspections {

        @Test
        void listsTheSignOffsStandingBetweenAGangAndTheWork() {
            List<String> chase = LookaheadReport.chaseInspections(service().lookaheadFrom(d("2025-03-26")));
            assertThat(chase).isNotEmpty();
            assertThat(chase.get(0)).containsPattern("^WP-\\d+ \\w+ at 1[23]\\+");
            assertThat(chase).allMatch(line -> line.contains("held by"));
        }
    }

    @Nested
    class GetLookahead {

        @Test
        void runsFromTheFirstCutOffWhenNothingSaysOtherwise() {
            JsonNode body = API.get("/lookahead").json();
            assertThat(body.get("from").asText()).isEqualTo("2025-01-24");
            assertThat(body.get("workingDays").doubleValue()).isEqualTo(20);
        }

        @Test
        void takesTheDateTheWeeksAndTheGangsOffTheQuery() {
            JsonNode body =
                    API.get("/lookahead?from=2025-03-26&weeks=2&gangs=2").json();
            assertThat(body.get("from").asText()).isEqualTo("2025-03-26");
            assertThat(body.get("to").asText()).isEqualTo("2025-04-08");
            assertThat(body.get("workingDays").doubleValue()).isEqualTo(10);
        }

        @Test
        void keepsTheReadyListApartFromTheHeldList() {
            JsonNode body = API.get("/lookahead?from=2025-03-26").json();
            List<String> ready = texts(body.get("ready"));
            List<String> held = texts(body.get("heldForInspection"));
            assertThat(ready).isNotEmpty();
            assertThat(held).isNotEmpty();
            for (String step : held) {
                assertThat(ready).doesNotContain(step);
            }
        }

        @Test
        void refusesADateItCannotRead() {
            assertThat(API.get("/lookahead?from=26%2F03%2F2025").status()).isEqualTo(400);
        }
    }

    @Nested
    class CwLookahead {

        @Test
        void printsThePlan() {
            Run run = commercial("lookahead", "2025-03-26");
            assertThat(run.code()).isZero();
            assertThat(run.outLines().get(0)).isEqualTo("lookahead 2025-03-26 to 2025-04-22");
        }

        @Test
        void takesTheWeeksAndTheGangsOffTheFlags() {
            Run run = commercial("lookahead", "2025-03-26", "-w", "2", "-g", "3");
            assertThat(run.out()).containsPattern("working days in the window +10");
        }

        @Test
        void printsOnlyTheChaseListWhenAsked() {
            Run run = commercial("lookahead", "2025-03-26", "--chase");
            assertThat(run.out()).contains("held by");
            assertThat(run.out()).doesNotContain("ready to start");
        }

        @Test
        void printsTheStepCountAsJson() {
            JsonNode json = commercial("--json", "lookahead", "2025-03-26").json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.doubleValue()).isGreaterThan(0);
        }

        @Test
        void refusesADateItCannotRead() {
            Run run = commercial("lookahead", "monday");
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).contains("bad_input");
        }
    }
}
