package io.github.rkaushikdev.chainageworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.survey.MeasurementMethod;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordImportTest {

    private static final String HEADER = String.join(",", RecordImport.RECORD_COLUMNS);
    private static final String GOOD = "SR-01,WP-01,E423.1,excavate,12+000,12+200,1800,m3,2025-03-06,J Foreman";

    private static String sheet(String... rows) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.addAll(List.of(rows));
        return String.join("\n", lines);
    }

    private static ImportReport<SurveyRecord> read(String text) {
        return RecordImport.importSurveyRecords(text);
    }

    @Test
    void readsACleanSheet() {
        ImportReport<SurveyRecord> report = read(sheet(GOOD));
        assertThat(report.hasErrors()).isFalse();
        assertThat(report.rowsRead()).isEqualTo(1);
        SurveyRecord record = report.imported().get(0);
        assertThat(record.id()).isEqualTo("SR-01");
        assertThat(record.packageId()).isEqualTo("WP-01");
        assertThat(record.code().format()).isEqualTo("E423.1");
        assertThat(record.stepId()).isEqualTo("excavate");
        assertThat(record.extent().format()).isEqualTo("12+000.000 to 12+200.000");
        assertThat(record.quantity().format()).isEqualTo("1800.00 m3");
        assertThat(SiteDates.format(record.measuredOn())).isEqualTo("2025-03-06");
        assertThat(record.bookedBy()).isEqualTo("J Foreman");
        assertThat(record.method()).isEqualTo(MeasurementMethod.TAPE);
    }

    @Test
    void readsSeveralRowsAndKeepsGoingPastABadOne() {
        ImportReport<SurveyRecord> report = read(sheet(
                GOOD,
                "SR-02,WP-01,nonsense,excavate,12+200,12+400,1750,m3,2025-03-13,J Foreman",
                "SR-03,WP-01,E423.1,excavate,12+400,12+600,1600,m3,2025-03-20,J Foreman"));
        assertThat(report.rowsRead()).isEqualTo(3);
        assertThat(report.imported().stream().map(SurveyRecord::id)).containsExactly("SR-01", "SR-03");
        assertThat(report.errors()).hasSize(1);
        assertThat(report.errors().get(0).line()).contains(3);
        assertThat(report.errors().get(0).column()).contains("code");
    }

    @Test
    void readsTheOptionalColumnsWhenTheyAreThere() {
        SurveyRecord record = read(String.join(
                        "\n", HEADER + ",method,supersedes,note", GOOD + ",total station,SR-00,retaken after the rain"))
                .imported()
                .get(0);
        assertThat(record.method()).isEqualTo(MeasurementMethod.TOTAL_STATION);
        assertThat(record.supersedes()).contains("SR-00");
        assertThat(record.note()).isEqualTo("retaken after the rain");
    }

    @Test
    void warnsAboutAMethodItDoesNotKnowAndTakesATape() {
        ImportReport<SurveyRecord> report = read(String.join("\n", HEADER + ",method", GOOD + ",laser guesswork"));
        assertThat(report.hasErrors()).isFalse();
        assertThat(report.warnings()).hasSize(1);
        assertThat(report.warnings().get(0).message()).contains("was not recognised");
        assertThat(report.imported().get(0).method()).isEqualTo(MeasurementMethod.TAPE);
    }

    @Test
    void namesTheMissingFieldAndTheLine() {
        ImportReport<SurveyRecord> report =
                read(sheet("SR-01,WP-01,E423.1,excavate,12+000,12+200,,m3,2025-03-06,J Foreman"));
        assertThat(report.imported()).isEmpty();
        assertThat(report.errors().get(0).message()).isEqualTo("line 2: quantity is missing");
        assertThat(report.errors().get(0).column()).contains("quantity");
    }

    @Test
    void rejectsAQuantityThatIsNotANumber() {
        ImportReport<SurveyRecord> report =
                read(sheet("SR-01,WP-01,E423.1,excavate,12+000,12+200,lots,m3,2025-03-06,J Foreman"));
        assertThat(report.errors().get(0).message()).isEqualTo("the quantity is not a number");
    }

    @Test
    void readsAQuantityWithThousandsSeparators() {
        String withCommas = String.join(
                "\n", HEADER, "SR-01,WP-01,E423.1,excavate,12+000,12+200,\"12,400\",m3,2025-03-06,J Foreman");
        assertThat(read(withCommas).imported().get(0).quantity().format()).isEqualTo("12400.00 m3");
    }

    @Test
    void rejectsAChainageThatRunsBackwards() {
        ImportReport<SurveyRecord> report =
                read(sheet("SR-01,WP-01,E423.1,excavate,12+400,12+200,10,m3,2025-03-06,J Foreman"));
        assertThat(report.errors().get(0).message()).contains("runs forwards");
    }

    @Test
    void rejectsADateThatIsNotARealDay() {
        ImportReport<SurveyRecord> report =
                read(sheet("SR-01,WP-01,E423.1,excavate,12+000,12+200,10,m3,2025-02-30,J Foreman"));
        assertThat(report.errors().get(0).column()).contains("measured on");
    }

    @Test
    void rejectsAUnitItDoesNotKnow() {
        ImportReport<SurveyRecord> report =
                read(sheet("SR-01,WP-01,E423.1,excavate,12+000,12+200,10,furlongs,2025-03-06,J Foreman"));
        assertThat(report.errors().get(0).message()).contains("unknown unit");
    }

    @Test
    void rejectsTheSecondRowThatReusesAnId() {
        ImportReport<SurveyRecord> report = read(sheet(GOOD, GOOD));
        assertThat(report.imported()).hasSize(1);
        assertThat(report.errors().get(0).message()).isEqualTo("record SR-01 appears twice");
        assertThat(report.errors().get(0).line()).contains(3);
    }

    @Test
    void reportsOneIssuePerMissingColumnRatherThanOnePerRow() {
        ImportReport<SurveyRecord> report = read("record,package\nSR-01,WP-01\nSR-02,WP-01");
        assertThat(report.imported()).isEmpty();
        assertThat(report.issues()).hasSize(8);
        assertThat(report.issues()).allMatch(i -> i.line().isEmpty());
    }

    @Test
    void readsAnEmptySheetAsNothing() {
        ImportReport<SurveyRecord> report = read(HEADER);
        assertThat(report.imported()).isEmpty();
        assertThat(report.rowsRead()).isZero();
        assertThat(report.hasErrors()).isFalse();
    }

    @Test
    void readsColumnsInAnyOrderAndIgnoresTheirCase() {
        String shuffled = String.join(
                "\n",
                "CODE,record,PACKAGE,step,to,from,unit,quantity,booked by,measured on",
                "E423.1,SR-01,WP-01,excavate,12+200,12+000,m3,1800,J Foreman,2025-03-06");
        assertThat(read(shuffled).imported().get(0).id()).isEqualTo("SR-01");
    }
}
