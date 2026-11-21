package io.github.rkaushikdev.chainageworks.cli;

import static io.github.rkaushikdev.chainageworks.support.CliHarness.runCommand;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The commands, and the readings of their counts and numbers, that the other command line suites do not reach. */
class CommandsTest {

    @Nested
    class TheCountsAndNumbersACommandReads {

        @Test
        void fallsBackToTheConfiguredCountWhenNoneIsGiven() {
            Run run = runCommand(List.of("curve"), Options.none().withEnv(Map.of("CHAINAGEWORKS_PERIODS", "3")));
            assertThat(run.outLines()).hasSize(5);
            assertThat(runCommand("curve", "--periods", "").outLines()).hasSize(14);
        }

        @Test
        void refusesACountThatIsNotAWholeNumberFromOne() {
            for (String count : List.of("0", "1.5", "lots", "3000000000")) {
                Run run = runCommand("lookahead", "2025-03-03", "--weeks", count);
                assertThat(List.of(count, run.code(), run.err()))
                        .isEqualTo(List.of(count, 1, "bad_input: that has to be a whole number from one"));
            }
        }

        @Test
        void refusesALatenessThatIsNotAWholeNumber() {
            for (String late : List.of("0.5", "soon")) {
                Run run = runCommand("cash", "--late", late);
                assertThat(List.of(late, run.err()))
                        .isEqualTo(List.of(late, "bad_input: late payment is a whole number of periods"));
            }
            assertThat(runCommand("cash", "--late", "1", "-n", "4").code()).isZero();
        }

        @Test
        void refusesAValuationNumberBelowOneOnEveryReport() {
            for (String command : List.of("certificate", "delay", "outturn", "payments")) {
                Run run = runCommand(command, "0");
                assertThat(List.of(command, run.err()))
                        .isEqualTo(List.of(command, "bad_input: a valuation number counts from one"));
            }
        }
    }

    @Nested
    class TheRestOfTheCommands {

        private static Run hauling(String... argv) {
            return runCommand(List.of(argv), Options.none().withJob(JobFixtures.haulingJob()));
        }

        @Test
        void saysSoWhenTheContractFixesNoSuchTerm() {
            Run text = runCommand("delay", "3");
            assertThat(text.out()).isEqualTo("the contract fixes no completion date");
            assertThat(runCommand("--json", "defects", "3").out()).isEqualTo("null");
        }

        @Test
        void printsTheLookaheadAndTheInspectionsToChase() {
            Run plan = runCommand("lookahead", "2025-03-03", "-w", "2", "-g", "2");
            assertThat(plan.code()).isZero();
            assertThat(plan.out()).isNotEmpty();
            Run chase = runCommand("--json", "lookahead", "2025-03-03", "--chase");
            assertThat(chase.code()).isZero();
            assertThat(chase.json().isArray()).isTrue();
        }

        @Test
        void printsTheHaulPlanAndWhatHasToLeaveEachSource() {
            Run plan = hauling("haul");
            assertThat(plan.code()).isZero();
            assertThat(plan.out()).isNotEmpty();
            Run muckAway = hauling("--json", "haul", "--muck-away");
            assertThat(muckAway.code()).isZero();
            assertThat(muckAway.json().isArray()).isTrue();
        }

        @Test
        void printsTheRevisionsAndTheRateMovements() {
            assertThat(runCommand("revisions").code()).isZero();
            Run rates = runCommand("--json", "revisions", "--rates");
            assertThat(rates.code()).isZero();
            assertThat(rates.json().isArray()).isTrue();
        }

        @Test
        void printsTheCashForecast() {
            Run run = runCommand("cash", "-n", "4");
            assertThat(run.code()).isZero();
            assertThat(run.outLines()).hasSize(6);
        }

        @Test
        void saysNothingIsWrongWithASoundJobAndFailsOnABrokenOne() {
            Run sound = runCommand("doctor");
            assertThat(sound.code()).isZero();
            assertThat(sound.out()).startsWith("nothing wrong, ");
            Run empty = runCommand(List.of("doctor", "--all"), Options.none().withJob(JobFixtures.emptyJob()));
            assertThat(empty.code()).isEqualTo(1);
            assertThat(empty.out()).startsWith("error job: no bill has been loaded");
            assertThat(empty.err()).isEqualTo("bad_input: the job has errors");
        }

        @Test
        void printsTheDiagnosisAsJson() {
            Run run = runCommand("--json", "doctor");
            assertThat(run.json().path("errors").asText()).isEqualTo("0");
            assertThat(run.json().path("findings").isArray()).isTrue();
            assertThat(run.json().path("blockers").isArray()).isTrue();
        }
    }
}
