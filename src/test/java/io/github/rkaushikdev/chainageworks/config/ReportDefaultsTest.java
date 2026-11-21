package io.github.rkaushikdev.chainageworks.config;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.support.CliHarness;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The report defaults the config carries, and the commands and routes that take their defaults from it. */
class ReportDefaultsTest {

    private static Run run(List<String> argv, Map<String, String> env) {
        return CliHarness.runCommand(
                argv, Options.none().withJob(commercialJob()).withEnv(env));
    }

    private static ApiClient apiOver(Config config) {
        return ApiClient.over(new Service(commercialJob(), config));
    }

    @Nested
    class TheReportDefaultsOnTheConfig {

        @Test
        void hasASensibleSetOutOfTheBox() {
            assertThat(Config.DEFAULT.reports()).isEqualTo(new Config.Reports(12, 4, 1, 10));
        }

        @Test
        void takesEachOneOffTheEnvironment() {
            Config config = Config.fromEnv(Map.of(
                    "CHAINAGEWORKS_PERIODS", "24",
                    "CHAINAGEWORKS_LOOKAHEAD_WEEKS", "6",
                    "CHAINAGEWORKS_GANGS", "3",
                    "CHAINAGEWORKS_WORST_LINES", "40"));
            assertThat(config.reports()).isEqualTo(new Config.Reports(24, 6, 3, 40));
        }

        @Test
        void keepsTheDefaultsForAnythingTheEnvironmentLeavesOut() {
            Config config = Config.fromEnv(Map.of("CHAINAGEWORKS_PERIODS", "24"));
            assertThat(config.reports().periods()).isEqualTo(24);
            assertThat(config.reports().lookaheadWeeks()).isEqualTo(4);
        }

        @Test
        void refusesACountThatIsNotAWholeNumberFromOne() {
            assertThatThrownBy(() -> Config.fromEnv(Map.of("CHAINAGEWORKS_GANGS", "lots")))
                    .hasMessageContaining("whole number from one");
            assertThatThrownBy(() -> Config.fromEnv(Map.of("CHAINAGEWORKS_PERIODS", "0")))
                    .hasMessageContaining("whole number from one");
        }

        @Test
        void refusesACountOutsideWhatTheSchemaAllows() {
            assertThatThrownBy(() -> Config.fromEnv(Map.of("CHAINAGEWORKS_LOOKAHEAD_WEEKS", "400")))
                    .hasMessageContaining("lookaheadWeeks");
        }

        @Test
        void mergesAPartialSetOverABase() {
            Config merged = Config.DEFAULT.merge(ConfigOverrides.none().withPeriods(18));
            assertThat(merged.reports().periods()).isEqualTo(18);
            assertThat(merged.reports().gangs()).isEqualTo(1);
        }

        @Test
        void printsThemAll() {
            Config run = Config.fromEnv(Map.of());
            assertThat(run.reports().worstLines()).isEqualTo(10);
        }
    }

    @Nested
    class CwConfig {

        @Test
        void printsTheReportDefaultsAlongsideTheRest() {
            Run run = CliHarness.runCommand("config");
            assertThat(run.out()).contains("periods             12");
            assertThat(run.out()).contains("lookahead weeks     4");
            assertThat(run.out()).contains("worst lines         10");
        }

        @Test
        void printsWhatTheEnvironmentSaid() {
            Run run = CliHarness.runCommand(
                    List.of("config"), Options.none().withEnv(Map.of("CHAINAGEWORKS_PERIODS", "24")));
            assertThat(run.out()).contains("periods             24");
        }
    }

    @Nested
    class TheCommandsTakeTheirDefaultsFromTheConfig {

        @Test
        void runsACurveToTheConfiguredNumberOfPeriods() {
            JsonNode json = run(List.of("--json", "curve"), Map.of("CHAINAGEWORKS_PERIODS", "5"))
                    .json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.asText()).isEqualTo("5");
        }

        @Test
        void letsTheFlagWinOverTheConfig() {
            JsonNode json = run(List.of("--json", "curve", "-n", "7"), Map.of("CHAINAGEWORKS_PERIODS", "5"))
                    .json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.asText()).isEqualTo("7");
        }

        @Test
        void runsACashForecastToTheConfiguredNumberOfPeriods() {
            JsonNode json = run(List.of("--json", "cash"), Map.of("CHAINAGEWORKS_PERIODS", "3"))
                    .json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.asText()).isEqualTo("3");
        }

        @Test
        void runsALookaheadOverTheConfiguredWindow() {
            Run run = run(List.of("lookahead", "2025-03-26"), Map.of("CHAINAGEWORKS_LOOKAHEAD_WEEKS", "2"));
            assertThat(run.out()).containsPattern("working days in the window +10");
        }

        @Test
        void refusesAFlagThatIsNotAWholeNumberFromOne() {
            Run run = run(List.of("curve", "-n", "lots"), Map.of());
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).contains("whole number from one");
        }
    }

    @Nested
    class TheApiTakesItsDefaultsFromTheConfigToo {

        @Test
        void runsACurveToTheConfiguredNumberOfPeriods() {
            Config config = Config.DEFAULT.merge(ConfigOverrides.none().withPeriods(4));
            JsonNode body = apiOver(config).get("/curve").json();
            assertThat(body).hasSize(4);
        }

        @Test
        void letsTheQueryWinOverTheConfig() {
            Config config = Config.DEFAULT.merge(ConfigOverrides.none().withPeriods(4));
            JsonNode body = apiOver(config).get("/curve?periods=6").json();
            assertThat(body).hasSize(6);
        }

        @Test
        void runsALookaheadOverTheConfiguredWindow() {
            Config config = Config.DEFAULT.merge(ConfigOverrides.none().withLookaheadWeeks(2));
            JsonNode body = apiOver(config).get("/lookahead").json();
            assertThat(body.get("workingDays").doubleValue()).isEqualTo(10);
        }
    }
}
