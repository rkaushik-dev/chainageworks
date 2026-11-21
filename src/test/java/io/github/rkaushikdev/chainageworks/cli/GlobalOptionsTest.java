package io.github.rkaushikdev.chainageworks.cli;

import static io.github.rkaushikdev.chainageworks.support.CliHarness.runCommand;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The flags every command takes, and when the job behind them is read. */
class GlobalOptionsTest {

    @Nested
    class TheGlobalFlagsAreReadWhenTheCommandRuns {

        @Test
        void takesAFlagWrittenBeforeTheCommand() {
            Run run = runCommand("--json", "status");
            assertThat(run.json().path("id").asText()).isEqualTo("A1-IMP");
        }

        @Test
        void takesAFlagWrittenAfterTheCommand() {
            Run run = runCommand("status", "--json");
            assertThat(run.json().path("id").asText()).isEqualTo("A1-IMP");
        }

        @Test
        void takesTheFileFlagEitherSideOfTheCommand() {
            Run before = runCommand("--file", "other.json", "config");
            Run after = runCommand("config", "--file", "other.json");
            assertThat(before.out()).contains("job file            other.json");
            assertThat(after.out()).contains("job file            other.json");
        }

        @Test
        void takesTheMarkdownFlagAfterTheCommand() {
            Run run = runCommand(
                    List.of("certificate", "3", "--markdown"), Options.none().withJob(commercialJob()));
            assertThat(run.outLines().get(0)).isEqualTo("# Payment certificate 3");
        }
    }

    @Nested
    class TheJobIsLoadedOnceACommandNeedsIt {

        private static Options failingLoad() {
            return Options.none().withLoadError(new BadInputException("no job file"));
        }

        @Test
        void loadsItForACommandThatReadsTheJob() {
            Run run = runCommand(List.of("status"), failingLoad());
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).contains("no job file");
        }

        @Test
        void doesNotLoadItForACommandThatDoesNot() {
            Run run = runCommand(List.of("config"), failingLoad());
            assertThat(run.code()).isZero();
        }

        @Test
        void doesNotLoadItToPrintTheReference() {
            Run run = runCommand(List.of("commands"), failingLoad());
            assertThat(run.code()).isZero();
        }
    }

    @Nested
    class EveryReportCommandAnswersTheSameWayToTheJsonFlag {

        private static final List<List<String>> COMMANDS = List.of(
                List.of("progress", "3"),
                List.of("certificate", "3"),
                List.of("outturn", "3"),
                List.of("variations", "3"),
                List.of("risks", "3"),
                List.of("payments", "3"),
                List.of("output", "3"),
                List.of("defects", "3"),
                List.of("delay", "3"));

        @Test
        void printsSomethingAReaderCanParse() {
            for (List<String> argv : COMMANDS) {
                List<String> withJson = new ArrayList<>(List.of("--json"));
                withJson.addAll(argv);
                Run run = runCommand(withJson, Options.none().withJob(commercialJob()));
                assertThat(List.of(argv.get(0), run.code())).isEqualTo(List.of(argv.get(0), 0));
                assertThatCode(() -> Json.parse(run.out())).doesNotThrowAnyException();
            }
        }

        @Test
        void printsTextWithoutTheFlag() {
            for (List<String> argv : COMMANDS) {
                Run run = runCommand(argv, Options.none().withJob(commercialJob()));
                assertThat(List.of(argv.get(0), run.code())).isEqualTo(List.of(argv.get(0), 0));
                assertThat(run.out().length()).isGreaterThan(0);
            }
        }

        @Test
        void runsAgainstABareJobWithoutFallingOver() {
            for (List<String> argv : COMMANDS) {
                Run run = runCommand(argv, Options.none().withJob(sampleJob()));
                assertThat(List.of(argv.get(0), run.code())).isEqualTo(List.of(argv.get(0), 0));
            }
        }
    }

    @Nested
    class ACommandThatDoesNotExist {

        @Test
        void isAMisuseRatherThanACrash() {
            Run run = runCommand("reticulate");
            assertThat(run.code()).isEqualTo(1);
        }

        @Test
        void leavesTheJobFileAlone() {
            Run run = runCommand("reticulate");
            assertThat(run.saved()).isEmpty();
            assertThat(run.written()).isEmpty();
        }
    }
}
