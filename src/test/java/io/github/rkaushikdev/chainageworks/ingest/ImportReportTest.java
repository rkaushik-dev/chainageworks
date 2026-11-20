package io.github.rkaushikdev.chainageworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.io.Csv;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ImportReportTest {

    private static final ImportReport<String> REPORT = new ImportReport<>(
            List.of("a", "b"),
            List.of(
                    ImportIssue.error("quantity is missing", 4, "quantity"),
                    ImportIssue.warning("the method was not recognised, taking a tape", 6, "method")),
            3);

    @Nested
    class TheReportShape {

        @Test
        void countsWhatWasTakenAndWhatWasLeftOut() {
            assertThat(REPORT.rowsAccepted()).isEqualTo(2);
            assertThat(REPORT.rowsRejected()).isEqualTo(1);
        }

        @Test
        void separatesErrorsFromWarnings() {
            assertThat(REPORT.errors()).hasSize(1);
            assertThat(REPORT.warnings()).hasSize(1);
            assertThat(REPORT.hasErrors()).isTrue();
        }

        @Test
        void startsEmpty() {
            ImportReport<String> blank = ImportReport.empty();
            assertThat(blank.imported()).isEmpty();
            assertThat(blank.rowsRead()).isZero();
            assertThat(blank.hasErrors()).isFalse();
        }

        @Test
        void hasNoErrorsWhenEverythingIsAWarning() {
            assertThat(REPORT.withIssues(REPORT.warnings()).hasErrors()).isFalse();
        }
    }

    @Nested
    class Issue {

        @Test
        void defaultsToAFileLevelError() {
            assertThat(ImportIssue.of("nothing here"))
                    .isEqualTo(
                            new ImportIssue(Optional.empty(), Optional.empty(), "nothing here", IssueSeverity.ERROR));
        }
    }

    @Nested
    class IssueFrom {

        @Test
        void takesTheLineAndColumnOutOfTheErrorDetails() {
            BadInputException error =
                    new BadInputException("line 7: quantity is missing", Details.of("line", 7, "column", "quantity"));
            assertThat(ImportIssue.from(error))
                    .isEqualTo(new ImportIssue(
                            Optional.of(7),
                            Optional.of("quantity"),
                            "line 7: quantity is missing",
                            IssueSeverity.ERROR));
        }

        @Test
        void fallsBackToTheLineItWasGiven() {
            assertThat(ImportIssue.from(new ConflictException("already booked"), 9)
                            .line())
                    .contains(9);
        }

        @Test
        void hasNoLineWhenNothingSuppliesOne() {
            assertThat(ImportIssue.from(new ConflictException("already booked")).line())
                    .isEmpty();
        }

        @Test
        void stringifiesAnythingThatIsNotOneOfOurErrors() {
            assertThat(ImportIssue.from("disk went away", 3))
                    .isEqualTo(
                            new ImportIssue(Optional.of(3), Optional.empty(), "disk went away", IssueSeverity.ERROR));
        }
    }

    @Nested
    class CheckColumns {

        private final Csv.Table table = Csv.parse("record,package\nSR-01,WP-01");

        @Test
        void passesWhenEveryColumnIsThere() {
            assertThat(ImportReport.<String>checkColumns(table, List.of("record", "PACKAGE")))
                    .isEmpty();
        }

        @Test
        void givesOneIssuePerMissingColumnAndNothingImported() {
            ImportReport<String> failed = ImportReport.<String>checkColumns(
                            table, List.of("record", "code", "quantity"))
                    .orElseThrow();
            assertThat(failed.imported()).isEmpty();
            assertThat(failed.issues().stream().map(i -> i.column().orElseThrow()))
                    .containsExactly("code", "quantity");
            assertThat(failed.issues().get(0).message()).isEqualTo("the file has no code column");
        }

        @Test
        void stillReportsHowManyRowsTheFileHad() {
            assertThat(ImportReport.<String>checkColumns(table, List.of("code"))
                            .orElseThrow()
                            .rowsRead())
                    .isEqualTo(1);
        }
    }

    @Nested
    class Merge {

        @Test
        void addsTheImportsTheIssuesAndTheCounts() {
            ImportReport<String> merged = ImportReport.merge(List.of(REPORT, REPORT));
            assertThat(merged.imported()).hasSize(4);
            assertThat(merged.issues()).hasSize(4);
            assertThat(merged.rowsRead()).isEqualTo(6);
        }

        @Test
        void mergesNothingIntoAnEmptyReport() {
            assertThat(ImportReport.<String>merge(List.of())).isEqualTo(ImportReport.<String>empty());
        }
    }

    @Nested
    class Format {

        @Test
        void summarisesTheCountsAndListsEveryIssue() {
            assertThat(REPORT.format("records"))
                    .isEqualTo(String.join(
                            "\n",
                            "read 3 records, took 2, left out 1",
                            "  error: line 4 (quantity): quantity is missing",
                            "  warning: line 6 (method): the method was not recognised, taking a tape"));
        }

        @Test
        void saysFileForAnIssueWithNoLine() {
            ImportReport<String> fileLevel = new ImportReport<>(
                    List.of(), List.of(ImportIssue.error("the file has no code column", null, "code")), 2);
            assertThat(fileLevel.format()).contains("error: file (code):");
        }

        @Test
        void isOneLineWhenNothingWentWrong() {
            assertThat(new ImportReport<>(List.of("a"), List.of(), 1).format())
                    .isEqualTo("read 1 rows, took 1, left out 0");
        }
    }
}
