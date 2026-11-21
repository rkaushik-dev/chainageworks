package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.defects.CertificateBlock;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
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
    void chasesTheOverdueDefectsFirst() {
        assertThat(REPORT.chaseList())
                .containsExactly(
                        "DEF/2025/001 minor, due 2025-03-17: surface course ravelling at the joint",
                        "DEF/2025/002 safety, due 2025-03-21: missing barrier at the retaining wall");
    }
}
