package io.github.rkaushikdev.chainageworks.cli;

import static io.github.rkaushikdev.chainageworks.support.CliHarness.runCommand;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The help the command line prints, and what it says about a command line it cannot read. */
class HelpTest {

    private static String lines(String... lines) {
        return String.join("\n", lines);
    }

    @Nested
    class TheHelpText {

        @Test
        void opensWithTheUsageAndTheDescriptionAndListsEveryCommandWithTheHelpCommandLast() {
            Run run = runCommand("--help");
            List<String> lines = run.outLines();
            assertThat(lines.subList(0, 9))
                    .containsExactly(
                            "Usage: cw [options] [command]",
                            "",
                            "progress measurement and interim valuation for linear civil works",
                            "",
                            "Options:",
                            "  -f, --file <path>                     the job file to read",
                            "  --json                                print json rather than text",
                            "  --markdown                            print markdown rather than plain text",
                            "  -h, --help                            display help for command");
            assertThat(lines)
                    .contains("Commands:", "  import-records [options] <file>       load a survey book export");
            assertThat(lines.getLast()).isEqualTo("  help [command]                        display help for command");
            assertThat(run.err()).isEmpty();
        }

        @Test
        void wrapsALongDescriptionUnderItsOwnColumn() {
            assertThat(runCommand("-h").out())
                    .contains(lines(
                            "  csv <table> <valuation> <file>        write a table out as csv: bill, records,",
                            "                                        certificate, totals, outturn"));
        }

        @Test
        void listsACommandsOptionsWithTheHelpOptionLast() {
            assertThat(runCommand("lookahead", "--help").out())
                    .isEqualTo(lines(
                            "Usage: cw lookahead [options] <from>",
                            "",
                            "what the next few weeks have to get through",
                            "",
                            "Options:",
                            "  -w, --weeks <count>  how many weeks, default from the config",
                            "  -g, --gangs <count>  gangs for each step, default from the config",
                            "  --chase              print only the inspections holding work up",
                            "  -h, --help           display help for command"));
        }

        @Test
        void namesTheDefaultAnOptionTakes() {
            assertThat(runCommand("cash", "-h").out())
                    .contains("  --late <periods>       periods the payer runs behind (default: \"0\")");
        }

        @Test
        void isPrintedForACommandWhoseArgumentsAreMissing() {
            Run run = runCommand("strip", "--help");
            assertThat(run.code()).isZero();
            assertThat(run.outLines().get(0)).isEqualTo("Usage: cw strip [options] <package> <valuation>");
        }

        @Test
        void goesToTheErrorStreamWhenNoCommandIsNamed() {
            Run run = runCommand();
            assertThat(run.code()).isZero();
            assertThat(run.out()).isEmpty();
            assertThat(run.err()).startsWith("Usage: cw [options] [command]");
        }

        @Test
        void isWhatTheHelpCommandPrintsForTheCommandItNames() {
            assertThat(runCommand("help").out()).isEqualTo(runCommand("--help").out());
            assertThat(runCommand("help", "status").out())
                    .isEqualTo(runCommand("status", "--help").out());
            Run unknown = runCommand("help", "insurances");
            assertThat(unknown.code()).isZero();
            assertThat(unknown.err()).isEqualTo(runCommand("--help").out());
        }

        @Test
        void loadsNoJob() {
            Run run = runCommand(
                    List.of("certificate", "--help"),
                    Options.none().withLoadError(new IllegalStateException("no job here")));
            assertThat(run.code()).isZero();
        }
    }

    @Nested
    class ACommandLineItCannotRead {

        private static Run refused(String... argv) {
            Run run = runCommand(argv);
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.out()).isEmpty();
            return run;
        }

        @Test
        void namesACommandItDoesNotHaveAndGuessesTheOneMeant() {
            assertThat(refused("certficate", "3").err())
                    .isEqualTo(lines("error: unknown command 'certficate'", "(Did you mean certificate?)"));
            assertThat(refused("insurances").err()).isEqualTo("error: unknown command 'insurances'");
        }

        @Test
        void namesAnOptionItDoesNotTakeAndGuessesAmongTheGlobalFlagsToo() {
            assertThat(refused("status", "--jsn").err())
                    .isEqualTo(lines("error: unknown option '--jsn'", "(Did you mean --json?)"));
            assertThat(refused("lookahead", "2025-03-03", "--chace").err())
                    .isEqualTo(lines("error: unknown option '--chace'", "(Did you mean --chase?)"));
            assertThat(refused("--bogus", "status").err()).isEqualTo("error: unknown option '--bogus'");
        }

        @Test
        void namesTheArgumentThatIsMissing() {
            assertThat(refused("strip", "WP-01").err()).isEqualTo("error: missing required argument 'valuation'");
        }

        @Test
        void namesTheOptionWhoseValueIsMissing() {
            assertThat(refused("lookahead", "2025-03-03", "-w").err())
                    .isEqualTo("error: option '-w, --weeks <count>' argument missing");
        }

        @Test
        void countsTheArgumentsThereAreTooManyOf() {
            assertThat(refused("status", "extra").err())
                    .isEqualTo("error: too many arguments for 'status'. Expected 0 arguments but got 1.");
            assertThat(refused("progress", "3", "4").err())
                    .isEqualTo("error: too many arguments for 'progress'. Expected 1 argument but got 2.");
        }
    }

    @Nested
    class WhatTheParserLeavesAlone {

        @Test
        void takesAnArgumentStartingWithAnAtSignAsTyped() {
            Run run = runCommand("strip", "@WP-01", "3");
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).isEqualTo("not_found: no work package with id @WP-01");
        }

        @Test
        void takesEverythingAfterADoubleDashAsAnArgument() {
            assertThat(runCommand("progress", "--", "3").out()).contains("progress report, valuation 3");
            assertThat(runCommand("strip", "--", "--json", "3").err())
                    .isEqualTo("not_found: no work package with id --json");
        }

        @Test
        void letsTheLaterOfTwoCopiesOfAnOptionWin() {
            assertThat(runCommand("curve", "-n", "2", "--periods", "4").outLines())
                    .hasSize(6);
            assertThat(runCommand("-f", "a.json", "config", "-f", "b.json").out())
                    .contains("job file            b.json");
        }

        @Test
        void takesAValueAttachedToItsOption() {
            assertThat(runCommand("curve", "-n4").outLines()).hasSize(6);
            assertThat(runCommand("curve", "--periods=4").outLines()).hasSize(6);
            assertThat(runCommand("-fx.json", "config").out()).contains("job file            x.json");
        }

        @Test
        void takesAValueThatLooksLikeANegativeNumber() {
            Run run = runCommand("cash", "--late", "-1");
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).isEqualTo("bad_input: late payment is a whole number of periods");
        }
    }

    @Nested
    class TheGuess {

        @Test
        void countsASwapOfNeighboursAsOneEdit() {
            assertThat(Suggestion.distance("pakcages", "packages")).isEqualTo(1);
            assertThat(Suggestion.distance("status", "status")).isZero();
            assertThat(Suggestion.distance("cash", "cashflows")).isEqualTo(9);
        }

        @Test
        void offersEveryCandidateAtTheClosestDistanceInOrder() {
            assertThat(Suggestion.of("risk", List.of("whisk", "risks", "disk")))
                    .isEqualTo("\n(Did you mean one of disk, risks?)");
            assertThat(Suggestion.of("--mardown", List.of("--markdown", "--json")))
                    .isEqualTo("\n(Did you mean --markdown?)");
        }

        @Test
        void offersNothingFarOffNothingOfOneCharacterAndNotTheWordItself() {
            assertThat(Suggestion.of("insurances", List.of("certificate", "status")))
                    .isEmpty();
            assertThat(Suggestion.of("x", List.of("x", "y"))).isEmpty();
            assertThat(Suggestion.of("status", List.of("status"))).isEmpty();
        }
    }
}
