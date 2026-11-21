package io.github.rkaushikdev.chainageworks.performance;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.ingest.RecordImport;
import io.github.rkaushikdev.chainageworks.progress.PackageEarning;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.BigJob;
import io.github.rkaushikdev.chainageworks.support.JobDocuments;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Timing rather than correctness, on a job the size of a real contract.
 *
 * <p>The thresholds are deliberately loose, several times what the run takes on a quiet machine,
 * because a tight one on a shared runner is a test that fails for reasons nobody can act on. What they
 * catch is the change that makes something quadratic, which shows up as an order of magnitude and not
 * as a few percent. Tagged {@code performance}, so it runs only in the {@code perf} build profile.
 */
@Tag("performance")
class EnginePerformanceTest {

    private static final Job JOB = BigJob.bigJob();

    private static final ProgressContext CONTEXT = JOB.context();

    /** How long {@code work} took, in milliseconds. */
    private static double timed(Runnable work) {
        long started = System.nanoTime();
        work.run();
        return (System.nanoTime() - started) / 1e6;
    }

    @Nested
    class TheSizeOfTheFixture {

        @Test
        void isTheSizeOfARealContract() {
            assertThat(JOB.packages().count()).isEqualTo(200);
            assertThat(JOB.records().count()).isEqualTo(8000);
            assertThat(JOB.signOffs().count()).isEqualTo(200);
        }

        @Test
        void carriesACommercialSideToMatch() {
            assertThat(JOB.subcontracts().count()).isEqualTo(30);
            assertThat(JOB.paymentApplications().count()).isEqualTo(360);
            assertThat(JOB.contraCharges().count()).isEqualTo(90);
            assertThat(JOB.resourceReturns().count()).isEqualTo(2000);
        }
    }

    @Nested
    class EarningTheWholeJob {

        @Test
        void valuesTwoHundredPackagesInUnderFourSeconds() {
            double elapsed = timed(() -> PackageEarning.earnAll(CONTEXT, d("2025-06-25")));
            assertThat(elapsed).isLessThan(4000);
        }

        @Test
        void costsAboutTheSameAtALaterCutOffNotMore() {
            double early = timed(() -> PackageEarning.earnAll(CONTEXT, d("2025-02-25")));
            double late = timed(() -> PackageEarning.earnAll(CONTEXT, d("2026-02-25")));
            assertThat(late).isLessThan(Math.max(early * 6, 4000));
        }
    }

    @Nested
    class TheReports {

        private final Service service = new Service(JOB, Config.DEFAULT);

        @Test
        void buildsAProgressReportInUnderSixSeconds() {
            double elapsed = timed(() -> service.progressFor(6));
            assertThat(elapsed).isLessThan(6000);
        }

        @Test
        void buildsACertificateAtPeriodSixInUnderTwentySeconds() {
            double elapsed = timed(() -> service.certificateFor(6));
            assertThat(elapsed).isLessThan(20_000);
        }
    }

    @Nested
    class TheCodec {

        @Test
        void writesAndReadsTheWholeJobInUnderSixSeconds() {
            double elapsed = timed(() -> JobDocuments.throughText(JOB));
            assertThat(elapsed).isLessThan(6000);
        }
    }

    @Nested
    class TheImporter {

        @Test
        void readsFiveThousandRowsInUnderFourSeconds() {
            String header = "record,package,code,step,from,to,quantity,unit,measured on,booked by";
            List<String> lines = new ArrayList<>();
            lines.add(header);
            for (int i = 0; i < 5000; i++) {
                lines.add("SR-" + i + ",WP-0001,E423.1,excavate,12+000,12+100,120,m3,2025-03-06,J Foreman");
            }
            String text = String.join("\n", lines);
            AtomicInteger read = new AtomicInteger();
            double elapsed =
                    timed(() -> read.set(RecordImport.importSurveyRecords(text).rowsRead()));
            assertThat(read.get()).isEqualTo(5000);
            assertThat(elapsed).isLessThan(4000);
        }
    }

    @Nested
    class TheCommercialReports {

        private final Service service = new Service(JOB, Config.DEFAULT);

        @Test
        void valuesThirtyOrdersAndTheirSetOffInUnderSixSeconds() {
            AtomicInteger orders = new AtomicInteger();
            double elapsed =
                    timed(() -> orders.set(service.paymentsFor(6).positions().size()));
            assertThat(orders.get()).isEqualTo(30);
            assertThat(elapsed).isLessThan(6000);
        }

        @Test
        void setsTwoThousandReturnsAgainstTheWorkInUnderFifteenSeconds() {
            AtomicInteger packages = new AtomicInteger();
            double elapsed =
                    timed(() -> packages.set(service.outputFor(6).packages().size()));
            assertThat(packages.get()).isEqualTo(200);
            assertThat(elapsed).isLessThan(15_000);
        }

        @Test
        void rollsTheWholeAccountUpInUnderTwentySeconds() {
            double elapsed = timed(() -> service.outturnFor(6));
            assertThat(elapsed).isLessThan(20_000);
        }

        @Test
        void plansFourWeeksAcrossTwoHundredPackagesInUnderTenSeconds() {
            AtomicInteger steps = new AtomicInteger();
            double elapsed = timed(() ->
                    steps.set(service.lookaheadFrom(d("2025-06-25")).steps().size()));
            assertThat(steps.get()).isGreaterThan(0);
            assertThat(elapsed).isLessThan(10_000);
        }
    }
}
