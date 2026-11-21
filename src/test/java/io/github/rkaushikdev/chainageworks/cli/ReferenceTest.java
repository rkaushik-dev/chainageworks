package io.github.rkaushikdev.chainageworks.cli;

import static io.github.rkaushikdev.chainageworks.support.CliHarness.runCommand;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.cli.Reference.CommandEntry;
import io.github.rkaushikdev.chainageworks.cli.Reference.OptionEntry;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Model.CommandSpec;

/** The command reference read off the tree, and the command that prints it. */
class ReferenceTest {

    /** Deps that answer the sample job and write nowhere: the reference never touches them. */
    private static final CliDeps DEPS = new CliDeps() {
        @Override
        public Job loadJob(String path) {
            return JobFixtures.sampleJob();
        }

        @Override
        public void saveJob(Job job, String path) {}

        @Override
        public String readText(String path) {
            return "";
        }

        @Override
        public void writeText(String path, String text) {}

        @Override
        public void out(String text) {}

        @Override
        public void err(String text) {}

        @Override
        public Map<String, String> env() {
            return Map.of();
        }
    };

    private static final CommandSpec PROGRAM = Program.build(DEPS).tree();

    private static CommandEntry entry(List<CommandEntry> entries, String name) {
        return entries.stream()
                .filter(entry -> entry.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    class CommandEntries {

        private final List<CommandEntry> entries = Reference.commandEntries(PROGRAM);

        @Test
        void findsEveryCommandOnTheTree() {
            assertThat(entries.size()).isGreaterThan(20);
            assertThat(entries).extracting(CommandEntry::name).contains("certificate");
            assertThat(entries).extracting(CommandEntry::name).contains("import-records");
        }

        @Test
        void givesEveryCommandADescription() {
            for (CommandEntry entry : entries) {
                assertThat(List.of(entry.name(), !entry.description().isEmpty()))
                        .isEqualTo(List.of(entry.name(), true));
            }
        }

        @Test
        void buildsAUsageLineOffTheCommandItself() {
            assertThat(entry(entries, "certificate").usage()).isEqualTo("cw certificate [options] <valuation>");
        }

        @Test
        void carriesTheOptionsACommandTakes() {
            assertThat(entry(entries, "cash").options())
                    .extracting(OptionEntry::flags)
                    .containsExactly("-n, --periods <count>", "--late <periods>");
        }

        @Test
        void leavesTheOptionsEmptyOnACommandThatTakesNone() {
            assertThat(entry(entries, "status").options()).isEmpty();
        }
    }

    @Nested
    class GlobalOptions {

        @Test
        void namesTheFlagsThatApplyToEveryCommand() {
            assertThat(Reference.globalOptions(PROGRAM))
                    .extracting(OptionEntry::flags)
                    .containsExactly("-f, --file <path>", "--json", "--markdown");
        }
    }

    @Nested
    class ReferenceMarkdown {

        private final String text = Reference.markdown(PROGRAM);

        @Test
        void opensWithTheToolAndItsGlobalFlags() {
            assertThat(Text.split(text, "\n").get(0)).isEqualTo("# cw");
            assertThat(text).contains("## Global options");
            assertThat(text).contains("`--json` print json rather than text");
        }

        @Test
        void givesEveryCommandAHeadingAndAUsageBlock() {
            assertThat(text).contains("### certificate");
            assertThat(text).contains("cw certificate [options] <valuation>");
        }

        @Test
        void keepsTheCommandsInTheOrderTheyWereRegistered() {
            int status = text.indexOf("### status");
            int certificate = text.indexOf("### certificate");
            int doctor = text.indexOf("### doctor");
            assertThat(status).isLessThan(certificate);
            assertThat(certificate).isLessThan(doctor);
        }

        @Test
        void endsWithExactlyOneNewline() {
            assertThat(text.endsWith("\n")).isTrue();
            assertThat(text.endsWith("\n\n")).isFalse();
        }
    }

    @Nested
    class ReferenceLines {

        @Test
        void linesTheNamesUpInOneColumn() {
            List<String> lines = Reference.lines(PROGRAM);
            Set<Integer> gaps = lines.stream().map(line -> line.indexOf("  ")).collect(Collectors.toSet());
            assertThat(lines.size()).isGreaterThan(20);
            assertThat(gaps.size()).isGreaterThan(0);
            assertThat(lines).allMatch(line -> line.contains("  "));
        }
    }

    @Nested
    class CwCommands {

        @Test
        void printsOneLineForEachCommand() {
            Run run = runCommand("commands");
            assertThat(run.code()).isZero();
            assertThat(run.outLines().size()).isGreaterThan(20);
            assertThat(run.out()).contains("certificate");
        }

        @Test
        void writesTheReferenceOutAsMarkdownWhenAsked() {
            Run run = runCommand("commands", "--markdown-to", "docs/cli.md");
            assertThat(run.code()).isZero();
            assertThat(run.out()).isEqualTo("docs/cli.md");
            assertThat(Text.split(run.written().get("docs/cli.md"), "\n").get(0))
                    .isEqualTo("# cw");
        }

        @Test
        void printsTheWholeTreeAsJson() {
            JsonNode entries = runCommand("--json", "commands").json();
            List<String> names = new ArrayList<>();
            entries.forEach(entry -> names.add(entry.path("name").asText()));
            assertThat(names).contains("outturn");
        }

        @Test
        void needsNoJobFileToAnswer() {
            Run run = runCommand(
                    List.of("commands"), Options.none().withLoadError(new IllegalStateException("no job here")));
            assertThat(run.code()).isZero();
        }
    }
}
