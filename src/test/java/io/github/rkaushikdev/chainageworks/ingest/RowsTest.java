package io.github.rkaushikdev.chainageworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.io.Csv;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RowsTest {

    private record Gang(String id, double size) {}

    private static Gang readGang(Csv.RowReader reader) {
        return new Gang(Fields.need(reader, "gang"), Fields.needNumber(reader, "size"));
    }

    private static final Rows.RowImport<Gang> SPEC = Rows.RowImport.<Gang>of(
                    List.of("gang", "size"), (reader, issues) -> readGang(reader))
            .keyedBy(Gang::id)
            .duplicateSays(id -> "gang " + id + " appears twice")
            .keyColumn("gang");

    private static final String CSV = String.join("\n", "gang,size", "G1,6", "G2,4");

    @Nested
    class ReadTable {

        @Test
        void readsEveryRowOfACleanFile() {
            ImportReport<Gang> report = Rows.readTable(CSV, SPEC);
            assertThat(report.rowsRead()).isEqualTo(2);
            assertThat(report.imported()).containsExactly(new Gang("G1", 6), new Gang("G2", 4));
            assertThat(report.issues()).isEmpty();
        }

        @Test
        void reportsTheMissingColumnOnceRatherThanOnceARow() {
            ImportReport<Gang> report = Rows.readTable("gang\nG1\nG2\nG3", SPEC);
            assertThat(report.errors()).hasSize(1);
            assertThat(report.errors().get(0).message()).contains("no size column");
            assertThat(report.imported()).isEmpty();
        }

        @Test
        void leavesOutTheRowItCouldNotReadAndCarriesOn() {
            ImportReport<Gang> report = Rows.readTable(String.join("\n", "gang,size", "G1,6", "G2,lots", "G3,3"), SPEC);
            assertThat(report.rowsAccepted()).isEqualTo(2);
            assertThat(report.rowsRejected()).isEqualTo(1);
            assertThat(report.errors().get(0).line()).contains(3);
        }

        @Test
        void leavesOutTheRowThatRepeatsANameAndSaysWhichColumn() {
            ImportReport<Gang> report = Rows.readTable(String.join("\n", "gang,size", "G1,6", "G1,4"), SPEC);
            assertThat(report.rowsAccepted()).isEqualTo(1);
            assertThat(report.imported().get(0).size()).isEqualTo(6);
            assertThat(report.errors().get(0).message()).isEqualTo("gang G1 appears twice");
            assertThat(report.errors().get(0).column()).contains("gang");
        }

        @Test
        void filesTheDuplicateAgainstTheFirstColumnWhenNoneIsNamed() {
            ImportReport<Gang> report = Rows.readTable(
                    String.join("\n", "gang,size", "G1,6", "G1,4"),
                    Rows.RowImport.<Gang>of(List.of("gang", "size"), SPEC.read())
                            .keyedBy(Gang::id));
            assertThat(report.errors().get(0).message()).isEqualTo("G1 appears twice");
            assertThat(report.errors().get(0).column()).contains("gang");
        }

        @Test
        void keepsEveryRowWhenNothingNamesAKey() {
            ImportReport<Gang> report = Rows.readTable(
                    String.join("\n", "gang,size", "G1,6", "G1,4"),
                    Rows.RowImport.of(List.of("gang", "size"), SPEC.read()));
            assertThat(report.rowsAccepted()).isEqualTo(2);
            assertThat(report.issues()).isEmpty();
        }

        @Test
        void letsAReaderFileAWarningOfItsOwnWithoutLosingTheRow() {
            ImportReport<Gang> report =
                    Rows.readTable(CSV, Rows.RowImport.of(List.of("gang", "size"), (reader, issues) -> {
                        issues.add(ImportIssue.warning("taking the standard shift", reader.line(), "shift"));
                        return readGang(reader);
                    }));
            assertThat(report.rowsAccepted()).isEqualTo(2);
            assertThat(report.warnings()).hasSize(2);
            assertThat(report.errors()).isEmpty();
        }

        @Test
        void turnsWhateverTheReaderThrewIntoAnIssueOnThatLine() {
            ImportReport<Gang> report =
                    Rows.readTable(CSV, Rows.RowImport.of(List.of("gang", "size"), (reader, issues) -> {
                        if (reader.line() == 3) {
                            throw new BadInputException("that gang was stood down");
                        }
                        return readGang(reader);
                    }));
            assertThat(report.rowsAccepted()).isEqualTo(1);
            assertThat(report.errors().get(0).message()).isEqualTo("that gang was stood down");
            assertThat(report.errors().get(0).line()).contains(3);
        }

        @Test
        void readsAFileWithHeadingsAndNoRowsAtAll() {
            ImportReport<Gang> report = Rows.readTable("gang,size", SPEC);
            assertThat(report.rowsRead()).isZero();
            assertThat(report.imported()).isEmpty();
            assertThat(report.issues()).isEmpty();
        }
    }

    private record LoadRow(String lorry, double tonnes, String tip) {}

    private record Haul(String lorry, String tip, int loads, double tonnes) {}

    private static final Rows.GroupedImport<LoadRow, Haul> HAUL_SPEC = Rows.GroupedImport.<LoadRow, Haul>of(
                    List.of("lorry", "tonnes", "tip"),
                    (reader, issues) -> new LoadRow(
                            Fields.need(reader, "lorry"),
                            Fields.needNumber(reader, "tonnes"),
                            Fields.need(reader, "tip")),
                    LoadRow::lorry,
                    (lorry, head, rows) -> new Haul(
                            lorry,
                            head.tip(),
                            rows.size(),
                            rows.stream().mapToDouble(LoadRow::tonnes).sum()))
            .keyColumn("lorry")
            .agreeing((head, row) -> head.tip().equals(row.tip())
                    ? Optional.empty()
                    : Optional.of(
                            row.lorry() + " tipped at " + head.tip() + " on one row and " + row.tip() + " on another"));

    private static final String HAUL_CSV =
            String.join("\n", "lorry,tonnes,tip", "LR-1,18,borrow pit", "LR-2,20,borrow pit", "LR-1,17,borrow pit");

    @Nested
    class ReadGrouped {

        @Test
        void groupsTheRowsIntoOneRecordEach() {
            ImportReport<Haul> report = Rows.readGrouped(HAUL_CSV, HAUL_SPEC);
            assertThat(report.rowsRead()).isEqualTo(2);
            assertThat(report.imported())
                    .containsExactly(new Haul("LR-1", "borrow pit", 2, 35), new Haul("LR-2", "borrow pit", 1, 20));
        }

        @Test
        void keepsTheGroupsInTheOrderTheirFirstRowAppeared() {
            ImportReport<Haul> report = Rows.readGrouped(
                    String.join("\n", "lorry,tonnes,tip", "LR-9,1,a", "LR-2,1,a"),
                    HAUL_SPEC.agreeing((head, row) -> Optional.empty()));
            assertThat(report.imported().stream().map(Haul::lorry)).containsExactly("LR-9", "LR-2");
        }

        @Test
        void rejectsTheRowThatDisagreesWithTheFirstOfItsGroup() {
            ImportReport<Haul> report = Rows.readGrouped(
                    String.join("\n", "lorry,tonnes,tip", "LR-1,18,borrow pit", "LR-1,17,stockpile"), HAUL_SPEC);
            assertThat(report.imported().get(0).loads()).isEqualTo(1);
            assertThat(report.errors().get(0).message()).contains("on one row and");
            assertThat(report.errors().get(0).column()).contains("lorry");
        }

        @Test
        void keepsEveryRowOfAGroupWhenNothingChecksAgreement() {
            ImportReport<Haul> report = Rows.readGrouped(
                    String.join("\n", "lorry,tonnes,tip", "LR-1,18,borrow pit", "LR-1,17,stockpile"),
                    Rows.GroupedImport.of(HAUL_SPEC.columns(), HAUL_SPEC.read(), HAUL_SPEC.keyOf(), HAUL_SPEC.build()));
            assertThat(report.imported().get(0).loads()).isEqualTo(2);
            assertThat(report.issues()).isEmpty();
        }

        @Test
        void leavesOutTheRowItCouldNotReadAndKeepsTheGroup() {
            ImportReport<Haul> report = Rows.readGrouped(
                    String.join("\n", "lorry,tonnes,tip", "LR-1,18,borrow pit", "LR-1,lots,borrow pit"), HAUL_SPEC);
            assertThat(report.imported().get(0).loads()).isEqualTo(1);
            assertThat(report.errors().get(0).line()).contains(3);
        }

        @Test
        void turnsARefusalFromTheBuilderIntoAnIssueAgainstTheGroup() {
            ImportReport<Haul> report = Rows.readGrouped(HAUL_CSV, HAUL_SPEC.buildingWith((lorry, head, rows) -> {
                if (lorry.equals("LR-2")) {
                    throw new BadInputException("LR-2 was off the road that week");
                }
                return new Haul(lorry, "borrow pit", 0, 0);
            }));
            assertThat(report.imported()).hasSize(1);
            assertThat(report.errors().get(0).message()).isEqualTo("LR-2 was off the road that week");
        }

        @Test
        void reportsTheMissingColumnOnceAndReadsNothing() {
            ImportReport<Haul> report = Rows.readGrouped("lorry,tonnes\nLR-1,18", HAUL_SPEC);
            assertThat(report.rowsRead()).isZero();
            assertThat(report.errors().get(0).message()).contains("no tip column");
        }

        @Test
        void readsAFileWithHeadingsAndNoRowsAtAll() {
            ImportReport<Haul> report = Rows.readGrouped("lorry,tonnes,tip", HAUL_SPEC);
            assertThat(report.rowsRead()).isZero();
            assertThat(report.imported()).isEmpty();
            assertThat(report.issues()).isEmpty();
        }
    }
}
