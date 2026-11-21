package io.github.rkaushikdev.chainageworks.cli;

import static io.github.rkaushikdev.chainageworks.support.CliHarness.runCommand;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.fluctuatingJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The markdown pack the export command writes, and the markdown flag on the report commands. */
class ExportCommandTest {

    private static Run commercial(String... argv) {
        return runCommand(List.of(argv), Options.none().withJob(commercialJob()));
    }

    @Nested
    class CwExport {

        @Test
        void writesOneFileForEachReportInThePack() {
            Run run = commercial("export", "3", "out/val-03");
            assertThat(run.code()).isZero();
            assertThat(run.written().keySet())
                    .containsExactly("out/val-03/progress.md", "out/val-03/certificate.md", "out/val-03/payments.md");
        }

        @Test
        void writesMarkdownNotTheTerminalLayout() {
            Run run = commercial("export", "3", "out");
            assertThat(Text.split(run.written().get("out/certificate.md"), "\n").get(0))
                    .isEqualTo("# Payment certificate 3");
            assertThat(run.written().get("out/progress.md")).contains("| Package | Section |");
            assertThat(run.written().get("out/payments.md")).contains("## Notice diary");
        }

        @Test
        void listsTheFilesItWrote() {
            Run run = commercial("export", "3", "out");
            assertThat(run.outLines()).hasSize(3);
            assertThat(run.out()).contains("out/payments.md");
        }

        @Test
        void listsTheFilesAsJsonWhenAsked() {
            JsonNode json = commercial("--json", "export", "3", "out").json();
            assertThat(json)
                    .isEqualTo(Json.valueOf(List.of("out/progress.md", "out/certificate.md", "out/payments.md")));
        }

        @Test
        void carriesTheFluctuationClauseIntoTheExportedCertificate() {
            Run run = runCommand(List.of("export", "3", "out"), Options.none().withJob(fluctuatingJob()));
            assertThat(run.written().get("out/certificate.md")).contains("Price adjustment at 12.750%");
        }

        @Test
        void savesNothingBackToTheJobFile() {
            assertThat(commercial("export", "3", "out").saved()).isEmpty();
        }

        @Test
        void refusesAnExportWithNoDirectoryNamed() {
            assertThat(commercial("export", "3").code()).isEqualTo(1);
        }
    }

    @Nested
    class TheMarkdownFlagOnAReportCommand {

        @Test
        void printsTheCertificateAsMarkdown() {
            assertThat(commercial("--markdown", "certificate", "3").outLines().get(0))
                    .isEqualTo("# Payment certificate 3");
        }

        @Test
        void printsTheProgressReportAsMarkdown() {
            assertThat(commercial("--markdown", "progress", "3").out()).contains("| Package | Section |");
        }

        @Test
        void printsThePaymentPositionAsMarkdown() {
            assertThat(commercial("--markdown", "payments", "3").out())
                    .contains("# Subcontract payments at 2025-03-25");
        }

        @Test
        void printsPlainTextWithoutTheFlag() {
            assertThat(commercial("certificate", "3").outLines().get(0)).isEqualTo("payment certificate 3");
        }

        @Test
        void fallsBackToPlainTextOnAReportWithNoMarkdownOfItsOwn() {
            assertThat(commercial("--markdown", "defects", "3").outLines().get(0))
                    .isEqualTo("defects at 2025-03-25");
        }

        @Test
        void letsJsonWinOverMarkdown() {
            JsonNode json =
                    commercial("--json", "--markdown", "certificate", "3").json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.asText()).isEqualTo("3");
        }
    }
}
