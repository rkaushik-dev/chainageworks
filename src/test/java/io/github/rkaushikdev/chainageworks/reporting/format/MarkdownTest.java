package io.github.rkaushikdev.chainageworks.reporting.format;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MarkdownTest {

    private static List<String> lines(String text) {
        return Text.split(text, "\n");
    }

    private static Service service(Job job) {
        return new Service(job, Config.DEFAULT);
    }

    @Nested
    class EscapeCell {

        @Test
        void escapesAPipeSoItCannotSplitARow() {
            assertThat(Markdown.escapeCell("300 mm | 450 mm")).isEqualTo("300 mm \\| 450 mm");
        }

        @Test
        void flattensANewlineThatWouldBreakTheTable() {
            assertThat(Markdown.escapeCell("two\nlines")).isEqualTo("two lines");
            assertThat(Markdown.escapeCell("windows\r\nlines")).isEqualTo("windows lines");
        }

        @Test
        void leavesOrdinaryTextAlone() {
            assertThat(Markdown.escapeCell("capping to 12+400")).isEqualTo("capping to 12+400");
        }
    }

    @Nested
    class MarkdownTable {

        @Test
        void writesAHeadingARuleAndTheRows() {
            String text = Markdown.table(
                    List.of(Column.of("code"), Column.right("rate")), List.of(List.of("E423.1", "4.85")));
            assertThat(lines(text)).containsExactly("| code | rate |", "| :--- | ---: |", "| E423.1 | 4.85 |");
        }

        @Test
        void writesATableWithNoRowsAtAll() {
            assertThat(lines(Markdown.table(List.of(Column.of("code")), List.of())))
                    .hasSize(2);
        }
    }

    @Nested
    class MarkdownHeadingAndMarkdownPairs {

        @Test
        void writesAHeadingAtTheLevelAskedFor() {
            assertThat(Markdown.heading("Progress", 1)).isEqualTo("# Progress");
            assertThat(Markdown.heading("Packages")).isEqualTo("## Packages");
        }

        @Test
        void writesThePairsAsABoldList() {
            assertThat(Markdown.pairs(List.of(Map.entry("Budget", "£312,190.00"))))
                    .isEqualTo("- **Budget**: £312,190.00");
        }
    }

    @Nested
    class CertificateMarkdown {

        @Test
        void opensWithTheCertificateNumberAndTheDates() {
            String text =
                    Markdown.certificate(service(JobFixtures.commercialJob()).certificateFor(3));
            assertThat(lines(text).get(0)).isEqualTo("# Payment certificate 3");
            assertThat(text).contains("- **Valuation to**: 2025-03-25");
            assertThat(text).contains("- **Bill revision**: Rev A");
        }

        @Test
        void boldsTheAmountDue() {
            String text =
                    Markdown.certificate(service(JobFixtures.commercialJob()).certificateFor(3));
            assertThat(text).contains("| **Amount due this certificate** |");
        }

        @Test
        void leavesTheAdjustmentRowOutOnAFixedPriceContract() {
            assertThat(Markdown.certificate(service(JobFixtures.commercialJob()).certificateFor(3)))
                    .doesNotContain("Price adjustment");
        }

        @Test
        void addsTheAdjustmentRowAndTheWarningWhenThereIsAClause() {
            String text =
                    Markdown.certificate(service(JobFixtures.fluctuatingJob()).certificateFor(3));
            assertThat(text).contains("| Price adjustment at 12.750% |");
            assertThat(text).contains("> The price adjustment rests on a provisional index figure.");
        }
    }

    @Nested
    class ProgressMarkdown {

        @Test
        void opensWithTheCutOffAndTablesThePackages() {
            String text = Markdown.progress(service(JobFixtures.commercialJob()).progressFor(3));
            assertThat(lines(text).get(0)).isEqualTo("# Progress at 2025-03-25");
            assertThat(text).contains("## Packages");
            assertThat(text).contains("| WP-01 |");
        }

        @Test
        void givesOneRowForEveryPackage() {
            String text = Markdown.progress(service(JobFixtures.commercialJob()).progressFor(3));
            List<String> rows = lines(text).stream()
                    .filter(line -> line.startsWith("| WP-"))
                    .toList();
            assertThat(rows).hasSize(3);
        }
    }

    @Nested
    class SubcontractMarkdown {

        @Test
        void tablesTheOrdersAndTheNoticeDiary() {
            String text =
                    Markdown.subcontract(service(JobFixtures.commercialJob()).paymentsFor(3));
            assertThat(lines(text).get(0)).isEqualTo("# Subcontract payments at 2025-03-25");
            assertThat(text).contains("## Notice diary");
            assertThat(text).contains("| SA-01 | Dowsett Plant Hire |");
            assertThat(text).contains("- **Exposed by default**: £40,000.00");
        }

        @Test
        void leavesBothTablesOutOnAJobWithNoOrders() {
            String text = Markdown.subcontract(service(JobFixtures.sampleJob()).paymentsFor(3));
            assertThat(text).doesNotContain("## Notice diary");
            assertThat(text).contains("- **Orders**: 0");
        }
    }
}
