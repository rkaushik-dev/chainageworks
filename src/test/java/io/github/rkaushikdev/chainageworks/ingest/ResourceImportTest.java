package io.github.rkaushikdev.chainageworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.resource.HourKind;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturn;
import io.github.rkaushikdev.chainageworks.domain.resource.Shift;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import org.junit.jupiter.api.Test;

class ResourceImportTest {

    private static final String HEADER =
            "return,package,step,date,shift,kind,resource,description,count,hours,hour kind,returned by,note";

    private static final String CSV = String.join(
            "\n",
            HEADER,
            "RR-01,WP-01,strip,2025-03-03,day,labour,GANG-EARTH,earthworks gang,6,8,,A Kerrigan,",
            "RR-01,WP-01,strip,2025-03-03,day,plant,PL-D6,dozer,1,8,,A Kerrigan,",
            "RR-01,WP-01,strip,2025-03-03,day,plant,PL-360,22t excavator,1,3,standing,A Kerrigan,",
            "RR-02,WP-01,excavate,2025-03-04,night,labour,GANG-EARTH,earthworks gang,4,10,,A Kerrigan,water main");

    private static ImportReport<ResourceReturn> read(String text) {
        return ResourceImport.importResourceReturns(text);
    }

    @Test
    void groupsTheRowsBackIntoReturns() {
        ImportReport<ResourceReturn> report = read(CSV);
        assertThat(report.rowsRead()).isEqualTo(2);
        assertThat(report.imported()).hasSize(2);
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void putsTheLabourAndThePlantLinesOnTheRightSide() {
        ResourceReturn first = read(CSV).imported().get(0);
        assertThat(first.labour()).hasSize(1);
        assertThat(first.plant()).hasSize(2);
        assertThat(ResourceReturn.totalHours(first.plant())).isEqualTo(11);
    }

    @Test
    void takesTheGroupingFieldsOffTheFirstRowOfEachReturn() {
        ResourceReturn second = read(CSV).imported().get(1);
        assertThat(second.id()).isEqualTo("RR-02");
        assertThat(second.packageId()).isEqualTo("WP-01");
        assertThat(second.stepId()).contains("excavate");
        assertThat(SiteDates.format(second.date())).isEqualTo("2025-03-04");
        assertThat(second.shift()).isEqualTo(Shift.NIGHT);
        assertThat(second.note()).isEqualTo("water main");
    }

    @Test
    void readsAnEmptyHourKindAsHoursThatWereWorked() {
        ResourceReturn first = read(CSV).imported().get(0);
        assertThat(first.labour().get(0).kind()).isEqualTo(HourKind.WORKING);
        assertThat(first.plant().get(1).kind()).isEqualTo(HourKind.STANDING);
    }

    @Test
    void readsAnEmptyStepAsNoStepNamed() {
        String text =
                String.join("\n", HEADER, "RR-09,WP-01,,2025-03-03,day,labour,GANG-EARTH,gang,2,8,,A Kerrigan,tidy");
        assertThat(read(text).imported().get(0).stepId()).isEmpty();
    }

    @Test
    void refusesAFileMissingAColumnItNeeds() {
        ImportReport<ResourceReturn> report = read("return,package\nRR-01,WP-01");
        assertThat(report.imported()).isEmpty();
        assertThat(report.errors().stream().map(e -> e.column().orElseThrow())).contains("hours");
    }

    @Test
    void rejectsTheRowWhoseGroupingFieldsDisagreeWithTheFirst() {
        String text = String.join(
                "\n",
                HEADER,
                "RR-01,WP-01,strip,2025-03-03,day,labour,GANG-EARTH,gang,6,8,,A Kerrigan,",
                "RR-01,WP-02,strip,2025-03-03,day,labour,GANG-EARTH,gang,6,8,,A Kerrigan,");
        ImportReport<ResourceReturn> report = read(text);
        assertThat(report.imported()).hasSize(1);
        assertThat(report.imported().get(0).labour()).hasSize(1);
        assertThat(report.errors().get(0).message()).contains("on one row and");
    }

    @Test
    void leavesOutTheRowItCouldNotReadAndKeepsTheReturn() {
        String text = String.join(
                "\n",
                HEADER,
                "RR-01,WP-01,strip,2025-03-03,day,labour,GANG-EARTH,gang,6,8,,A Kerrigan,",
                "RR-01,WP-01,strip,2025-03-03,day,plant,PL-D6,dozer,one,8,,A Kerrigan,");
        ImportReport<ResourceReturn> report = read(text);
        assertThat(report.imported()).hasSize(1);
        assertThat(report.imported().get(0).plant()).isEmpty();
        assertThat(report.errors().get(0).line()).contains(3);
    }

    @Test
    void rejectsAnHourKindNobodyUses() {
        String text = String.join(
                "\n", HEADER, "RR-01,WP-01,strip,2025-03-03,day,labour,GANG-EARTH,gang,6,8,resting,A Kerrigan,");
        assertThat(read(text).rowsAccepted()).isZero();
    }

    @Test
    void readsAFileWithHeadingsAndNoRowsAtAll() {
        ImportReport<ResourceReturn> report = read(HEADER);
        assertThat(report.rowsRead()).isZero();
        assertThat(report.imported()).isEmpty();
        assertThat(report.issues()).isEmpty();
    }
}
