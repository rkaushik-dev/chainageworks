package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.defects.CertificateBlock;
import io.github.rkaushikdev.chainageworks.domain.defects.Defect;
import io.github.rkaushikdev.chainageworks.domain.defects.DefectInput;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefectsReportTest {

    private static final DefectsReport REPORT =
            DefectsReport.build(JobFixtures.commercialJob().defects().all(), d("2025-06-30"), d("2025-03-25"));

    @Test
    void rendersTheCountsAndThenTheOpenDefects() {
        assertThat(REPORT.render())
                .isEqualTo(String.join(
                        "\n",
                        "defects at 2025-03-25",
                        "=====================",
                        "",
                        "period ends       2025-06-30",
                        "outstanding       2",
                        "overdue           2",
                        "safety            1",
                        "major             0",
                        "minor             1",
                        "cause not agreed  0",
                        "certificate       period not ended",
                        "",
                        "defect        package  where                     severity  due         cause       description",
                        "------------  -------  ------------------------  --------  ----------  ----------"
                                + "  -------------------------------------",
                        "DEF/2025/001  WP-03    12+400.000 to 12+600.000  minor     2025-03-17  contractor"
                                + "  surface course ravelling at the joint",
                        "DEF/2025/002  WP-01                              safety    2025-03-21  contractor"
                                + "  missing barrier at the retaining wall"));
    }

    @Test
    void namesWhatIsStoppingTheCertificate() {
        assertThat(REPORT.block()).contains(CertificateBlock.PERIOD_NOT_ENDED);
        DefectsReport after =
                DefectsReport.build(JobFixtures.commercialJob().defects().all(), d("2025-06-30"), d("2025-07-01"));
        assertThat(after.render()).contains("certificate       safety defect open");
    }

    @Test
    void showsThePeriodRunOnForDefectsThatShutTheWorks() {
        List<Defect> defects =
                new ArrayList<>(JobFixtures.commercialJob().defects().all());
        defects.add(Defect.build(DefectInput.of(
                        "DF-90", "DEF/2025/090", "WP-03", "joint failed, lane closed", d("2025-04-07"), d("2025-04-21"))
                .withRectifiedOn(d("2025-04-16"))
                .withPreventsUse(true)));
        DefectsReport report = DefectsReport.build(defects, d("2025-06-30"), d("2025-06-30"));
        assertThat(report.extendedBy()).isEqualTo(10);
        assertThat(report.periodEnds()).isEqualTo(d("2025-07-10"));
        assertThat(report.render()).contains("extended by       10 days out of use");
        assertThat(report.block()).contains(CertificateBlock.PERIOD_NOT_ENDED);
        assertThat(REPORT.extendedBy()).isZero();
        assertThat(REPORT.render()).doesNotContain("extended by");
    }

    @Test
    void chasesTheOverdueDefectsFirst() {
        assertThat(REPORT.chaseList())
                .containsExactly(
                        "DEF/2025/001 minor, due 2025-03-17: surface course ravelling at the joint",
                        "DEF/2025/002 safety, due 2025-03-21: missing barrier at the retaining wall");
    }
}
