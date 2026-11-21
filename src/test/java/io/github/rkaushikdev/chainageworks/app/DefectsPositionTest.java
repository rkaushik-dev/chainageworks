package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.range;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.defects.Attribution;
import io.github.rkaushikdev.chainageworks.domain.defects.CertificateBlock;
import io.github.rkaushikdev.chainageworks.domain.defects.Defect;
import io.github.rkaushikdev.chainageworks.domain.defects.DefectInput;
import io.github.rkaushikdev.chainageworks.domain.defects.DefectSeverity;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.reporting.DefectsReport;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobDocuments;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The defects list read through the service at a valuation cut-off, and kept through the job file. */
class DefectsPositionTest {

    private static Job jobWithDefects() {
        Job job = sampleJob();
        job.setSettings(job.settings().withDefectsPeriodEnds(d("2025-06-30")));
        job.defects()
                .add(Defect.build(DefectInput.of(
                                "DF-01",
                                "DEF/2025/001",
                                "WP-03",
                                "surface course ravelling at the joint",
                                d("2025-03-03"),
                                d("2025-03-17"))
                        .withExtent(range("12+400", "12+600"))));
        job.defects()
                .add(Defect.build(DefectInput.of(
                                "DF-02",
                                "DEF/2025/002",
                                "WP-01",
                                "missing barrier at the retaining wall",
                                d("2025-03-20"),
                                d("2025-03-21"))
                        .withSeverity(DefectSeverity.SAFETY)));
        job.defects()
                .add(Defect.build(DefectInput.of(
                                "DF-03",
                                "DEF/2025/003",
                                "WP-02",
                                "damage by a third party",
                                d("2025-03-10"),
                                d("2025-03-24"))
                        .withAttribution(Attribution.EMPLOYER)
                        .withRectifiedOn(d("2025-03-18"))));
        return job;
    }

    private static DefectsReport defectsAt(Job job, int valuation) {
        return new Service(job, Config.DEFAULT).defectsFor(valuation).orElseThrow();
    }

    @Nested
    class DefectsFor {

        @Test
        void isNullWhenTheContractHasFixedNoDefectsPeriod() {
            assertThat(new Service(sampleJob(), Config.DEFAULT).defectsFor(3)).isEmpty();
        }

        @Test
        void summarisesThePositionAtTheCutOff() {
            DefectsReport report = defectsAt(jobWithDefects(), 3);
            assertThat(SiteDates.format(report.asAt())).isEqualTo("2025-03-25");
            assertThat(report.summary().outstanding()).isEqualTo(2);
            assertThat(report.summary().bySeverity())
                    .isEqualTo(Map.of(DefectSeverity.MINOR, 1, DefectSeverity.MAJOR, 0, DefectSeverity.SAFETY, 1));
            assertThat(report.summary().overdue()).isEqualTo(2);
        }

        @Test
        void leavesOutADefectThatHasBeenPutRight() {
            DefectsReport report = defectsAt(jobWithDefects(), 3);
            assertThat(report.outstanding()).extracting(Defect::id).doesNotContain("DF-03");
        }

        @Test
        void saysThePeriodHasNotEndedYet() {
            DefectsReport report = defectsAt(jobWithDefects(), 3);
            assertThat(report.block()).contains(CertificateBlock.PERIOD_NOT_ENDED);
        }

        @Test
        void namesTheSafetyDefectOnceThePeriodHasRun() {
            DefectsReport report = defectsAt(jobWithDefects(), 8);
            assertThat(report.block()).contains(CertificateBlock.SAFETY_DEFECT_OPEN);
        }

        @Test
        void listsTheChaseOrderOverdueFirst() {
            DefectsReport report = defectsAt(jobWithDefects(), 3);
            assertThat(report.chaseList().get(0)).contains("DEF/2025/001");
            assertThat(report.chaseList()).hasSize(2);
        }
    }

    @Nested
    class RenderDefectsReport {

        @Test
        void opensWithThePositionAndTablesWhatIsOpen() {
            String text = defectsAt(jobWithDefects(), 3).render();
            assertThat(Text.split(text, "\n").get(0)).isEqualTo("defects at 2025-03-25");
            assertThat(text).containsPattern("outstanding +2");
            assertThat(text).containsPattern("certificate +period not ended");
            assertThat(text).contains("12+400.000 to 12+600.000");
        }

        @Test
        void leavesTheTableOutWhenNothingIsOpen() {
            Job clean = sampleJob();
            clean.setSettings(clean.settings().withDefectsPeriodEnds(d("2025-06-30")));
            String text = defectsAt(clean, 3).render();
            assertThat(text).containsPattern("outstanding +0");
            assertThat(text).doesNotContain("severity");
        }
    }

    @Nested
    class TheDefectsListThroughTheJobFile {

        @Test
        void roundTripsThePeriodAndTheDefects() {
            Job again = JobDocuments.throughText(jobWithDefects());
            assertThat(SiteDates.format(again.settings().defectsPeriodEnds().orElseThrow()))
                    .isEqualTo("2025-06-30");
            assertThat(again.defects().count()).isEqualTo(3);
            assertThat(again.defects().require("DF-02").severity()).isEqualTo(DefectSeverity.SAFETY);
            assertThat(again.defects().require("DF-03").attribution()).isEqualTo(Attribution.EMPLOYER);
            assertThat(again.defects().require("DF-01").extent()).isPresent();
        }

        @Test
        void carriesAJobWithNoDefectsPeriodThroughAsNull() {
            Job again = JobDocuments.throughText(sampleJob());
            assertThat(again.settings().defectsPeriodEnds()).isEmpty();
            assertThat(again.defects().count()).isZero();
        }
    }
}
