package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.CliHarness.runCommand;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.io.Csv;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The five tables the csv export writes, read back as a spreadsheet would, and the command that writes them. */
class CsvTablesTest {

    private static final LocalDate CUT_OFF = d("2025-03-25");

    private static Service service() {
        return new Service(sampleJob(), Config.DEFAULT);
    }

    private static Run sample(String... argv) {
        return runCommand(List.of(argv), Options.none().withJob(sampleJob()));
    }

    @Nested
    class BillCsv {

        private final String text = CsvExport.bill(service().context(), CUT_OFF);
        private final Csv.Table table = Csv.parse(text);

        @Test
        void writesAHeadingAndARowForEveryItem() {
            assertThat(table.headings())
                    .containsExactly("code", "description", "unit", "quantity", "rate", "kind", "value");
            assertThat(table.rows()).hasSize(8);
        }

        @Test
        void writesMoneyASpreadsheetCanAddUp() {
            assertThat(text).contains("E423.1");
            assertThat(text).doesNotContain("£");
            assertThat(text).doesNotContainPattern("\\d,\\d\\d\\d\\.");
        }
    }

    @Nested
    class RecordsCsv {

        @Test
        void splitsTheExtentBackIntoAFromAndAToColumn() {
            Csv.Table table = Csv.parse(CsvExport.records(service().context(), CUT_OFF));
            assertThat(table.headings()).contains("from");
            assertThat(table.headings()).contains("to");
            assertThat(table.rows()).hasSize(3);
            assertThat(CsvExport.records(service().context(), CUT_OFF)).contains("12+000.000");
        }

        @Test
        void leavesOutAnythingMeasuredAfterTheDate() {
            Csv.Table early = Csv.parse(CsvExport.records(service().context(), d("2025-03-05")));
            assertThat(early.rows()).hasSize(1);
        }
    }

    @Nested
    class CertificateCsv {

        private final Csv.Table table =
                Csv.parse(CsvExport.certificate(service().certificateFor(3)));

        @Test
        void writesOneRowForEveryAllocationOnEveryPackage() {
            assertThat(table.rows().size()).isGreaterThan(3);
            assertThat(table.headings()).contains("capped");
        }

        @Test
        void repeatsThePackageColumnsDownTheFile() {
            List<String> packages =
                    table.rows().stream().map(row -> row.values().get(2)).toList();
            assertThat(new HashSet<>(packages).size()).isLessThan(packages.size());
        }

        @Test
        void carriesTheValuationNumberAndCutOffOnEveryRow() {
            for (Csv.Row row : table.rows()) {
                assertThat(row.values().get(0)).isEqualTo("3");
                assertThat(row.values().get(1)).isEqualTo("2025-03-25");
            }
        }
    }

    @Nested
    class CertificateTotalsCsv {

        @Test
        void writesTheCertificateTheWayItPrints() {
            Csv.Table table = Csv.parse(CsvExport.certificateTotals(service().certificateFor(3)));
            assertThat(table.rows())
                    .extracting(row -> row.values().get(0))
                    .containsExactly(
                            "measured work",
                            "materials on site",
                            "daywork",
                            "gross valuation",
                            "less retention",
                            "net valuation",
                            "less previously certified",
                            "amount due this certificate");
            assertThat(table.rows().getLast().values().get(1)).isEqualTo("15344.87");
        }
    }

    @Nested
    class OutturnCsv {

        @Test
        void writesARowForEveryBillItemWithTheBasisItWasForecastOn() {
            Csv.Table table = Csv.parse(CsvExport.outturn(service().outturnFor(3)));
            assertThat(table.rows()).hasSize(8);
            assertThat(table.headings()).contains("basis");
            assertThat(table.rows())
                    .allMatch(row -> row.values().size() > 7
                            && List.of("bill", "trend").contains(row.values().get(7)));
        }
    }

    @Nested
    class CwCsv {

        @Test
        void writesTheTableItWasAskedFor() {
            Run run = sample("csv", "bill", "3", "out/bill.csv");
            assertThat(run.code()).isZero();
            assertThat(run.out()).isEqualTo("out/bill.csv");
            assertThat(Text.split(run.written().get("out/bill.csv"), "\n").get(0))
                    .isEqualTo("code,description,unit,quantity,rate,kind,value");
        }

        @Test
        void writesEveryTableItKnows() {
            for (String table : List.of("bill", "records", "certificate", "totals", "outturn")) {
                Run run = runCommand(
                        List.of("csv", table, "3", "out/" + table + ".csv"),
                        Options.none().withJob(commercialJob()));
                assertThat(List.of(table, run.code())).isEqualTo(List.of(table, 0));
                assertThat(run.written().get("out/" + table + ".csv")).isNotEmpty();
            }
        }

        @Test
        void reportsTheRowCountAsJson() throws Json.JsonFailure {
            Run run = sample("--json", "csv", "bill", "3", "out/bill.csv");
            assertThat(run.json()).isEqualTo(Json.parse("{\"file\": \"out/bill.csv\", \"rows\": 8}"));
        }

        @Test
        void refusesATableItDoesNotKnowAndNamesTheOnesItDoes() {
            Run run = sample("csv", "insurances", "3", "out/x.csv");
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).contains("no such table");
            assertThat(run.written()).isEmpty();
        }

        @Test
        void savesNothingBackToTheJobFile() {
            assertThat(sample("csv", "bill", "3", "out/bill.csv").saved()).isEmpty();
        }
    }
}
