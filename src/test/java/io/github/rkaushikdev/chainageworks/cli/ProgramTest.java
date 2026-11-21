package io.github.rkaushikdev.chainageworks.cli;

import static io.github.rkaushikdev.chainageworks.support.CliHarness.runCommand;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProgramTest {

    @Nested
    class CwStatus {

        @Test
        void printsWhatTheJobHolds() {
            Run run = runCommand("status");
            assertThat(run.code()).isZero();
            assertThat(run.out()).containsPattern("job +A1-IMP A1 improvement, section 2");
            assertThat(run.out()).containsPattern("contract sum +£312,190\\.00");
            assertThat(run.out()).containsPattern("packages +3");
            assertThat(run.out()).containsPattern("blockers +none");
        }

        @Test
        void namesTheBlockersOnAnEmptyJob() {
            Run run = runCommand(List.of("status"), Options.none().withJob(JobFixtures.emptyJob()));
            assertThat(run.out()).containsPattern("blockers +no bill has been loaded; ");
        }

        @Test
        void printsJsonWhenAsked() {
            JsonNode json = runCommand("--json", "status").json();
            assertThat(json.path("id").asText()).isEqualTo("A1-IMP");
            assertThat(json.path("blockers").isArray()).isTrue();
            assertThat(json.path("blockers")).isEmpty();
        }
    }

    @Nested
    class CwConfig {

        @Test
        void printsTheSettingsThisRunWouldUse() {
            Run run = runCommand("config");
            assertThat(run.out()).contains("job file            chainageworks.json");
            assertThat(run.out()).contains("http                127.0.0.1:8730");
        }

        @Test
        void takesTheFileFromTheFlagAndTheRestFromTheEnvironment() {
            Run run = runCommand(
                    List.of("--file", "other.json", "config"),
                    Options.none().withEnv(Map.of("CHAINAGEWORKS_PORT", "9100")));
            assertThat(run.out()).contains("job file            other.json");
            assertThat(run.out()).contains("http                127.0.0.1:9100");
        }

        @Test
        void reportsABadEnvironmentVariableRatherThanCrashing() {
            Run run = runCommand(List.of("config"), Options.none().withEnv(Map.of("CHAINAGEWORKS_PORT", "lots")));
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).contains("bad_input: CHAINAGEWORKS_PORT is not a whole number");
        }
    }

    @Nested
    class CwBill {

        @Test
        void printsTheBillInForce() {
            Run run = runCommand("bill");
            List<String> lines = run.outLines();
            assertThat(lines.get(0)).matches("code\\s+description\\s+quantity\\s+rate\\s+kind");
            assertThat(lines).hasSize(10);
            assertThat(run.out()).contains("E423.1");
            assertThat(run.out()).contains("£4.85/m3");
        }

        @Test
        void pricesAtTheDateAskedFor() {
            Run run = runCommand("bill", "--as-at", "2025-06-01");
            assertThat(run.code()).isZero();
        }

        @Test
        void refusesADateBeforeTheFirstBill() {
            Run run = runCommand("bill", "--as-at", "2024-01-01");
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).contains("no bill revision was in force");
        }

        @Test
        void refusesADateItCannotRead() {
            Run run = runCommand("bill", "--as-at", "yesterday");
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).contains("yyyy-mm-dd");
        }
    }

    @Nested
    class CwPackages {

        @Test
        void printsTheRegister() {
            Run run = runCommand("packages");
            List<String> lines = run.outLines();
            assertThat(lines.get(0)).matches("package\\s+section\\s+extent\\s+rule of credit\\s+items");
            assertThat(lines).hasSize(5);
            assertThat(run.out()).contains("bulk-earthworks");
        }
    }

    @Nested
    class CwProgress {

        @Test
        void printsTheReportForAValuation() {
            Run run = runCommand("progress", "3");
            assertThat(run.code()).isZero();
            assertThat(run.out()).contains("progress report, valuation 3 to 2025-03-25");
            assertThat(run.out()).containsPattern("earned +£16,152\\.50");
        }

        @Test
        void refusesAValuationNumberThatIsNotAWholeNumber() {
            Run run = runCommand("progress", "three");
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).contains("counts from one");
        }
    }

    @Nested
    class CwCertificate {

        @Test
        void printsTheCertificate() {
            Run run = runCommand("certificate", "3");
            assertThat(run.out()).contains("payment certificate 3");
            assertThat(run.out()).containsPattern("amount due this certificate +£15,344\\.87");
        }
    }

    @Nested
    class CwStrip {

        @Test
        void drawsTheStripUnderAHeading() {
            List<String> lines = runCommand("strip", "WP-01", "3").outLines();
            assertThat(lines.get(0)).isEqualTo("WP-01, valuation 3 to 2025-03-25, issued 2025-04-03, due 2025-04-17");
            assertThat(lines.get(1)).containsPattern("^strip {4}#+");
        }

        @Test
        void reportsAPackageItDoesNotHave() {
            Run run = runCommand("strip", "WP-99", "3");
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).contains("not_found: no work package with id WP-99");
        }
    }

    @Nested
    class CwCurve {

        @Test
        void printsTwelvePeriodsByDefault() {
            assertThat(runCommand("curve").outLines()).hasSize(14);
        }

        @Test
        void honoursAPeriodCount() {
            assertThat(runCommand("curve", "--periods", "4").outLines()).hasSize(6);
        }
    }

    @Nested
    class CwReconcile {

        @Test
        void listsThePackagesThatDoNotBalanceAgainstTheBill() {
            Run run = runCommand("reconcile");
            assertThat(run.out()).contains("under allocated");
            assertThat(run.out()).contains("not allocated");
        }
    }

    @Nested
    class Failures {

        @Test
        void reportsAJobFileItCannotRead() {
            Run run = runCommand(
                    List.of("status"),
                    Options.none()
                            .withLoadError(
                                    new BadInputException("no job file at that path", Details.of("path", "x.json"))));
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).isEqualTo("bad_input: no job file at that path");
        }

        @Test
        void exitsTwoOnSomethingThatIsNotOneOfOurErrors() {
            Run run = runCommand(List.of("status"), Options.none().withLoadError(new IllegalStateException("boom")));
            assertThat(run.code()).isEqualTo(2);
            assertThat(run.err()).contains("internal: boom");
        }

        @Test
        void reportsAnUnknownCommand() {
            Run run = runCommand("nonsense");
            assertThat(run.code()).isEqualTo(1);
        }

        @Test
        void printsHelpWithoutFailing() {
            Run run = runCommand("--help");
            assertThat(run.code()).isZero();
            assertThat(run.out()).contains("progress measurement and interim valuation");
        }
    }
}
