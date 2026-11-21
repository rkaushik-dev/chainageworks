package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.resource.HourKind;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceLine;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturn;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturnInput;
import io.github.rkaushikdev.chainageworks.reporting.OutputReport.PackageStep;
import io.github.rkaushikdev.chainageworks.reporting.OutputReport.UnallocatedHours;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutputReportTest {

    /** The commercial job with a day's labour booked to a step nothing has been built on. */
    private static Job job() {
        Job job = JobFixtures.commercialJob();
        job.resourceReturns()
                .add(ResourceReturn.build(ResourceReturnInput.of("RR-05", "WP-01", d("2025-03-12"), "A Kerrigan")
                        .withStep("trim")
                        .withLabour(
                                List.of(new ResourceLine("GANG-EARTH", "earthworks gang", 2, 8, HourKind.WORKING)))));
        return job;
    }

    private static final Job JOB = job();
    private static final OutputReport REPORT = OutputReport.build(
            JOB.context(),
            JOB.packages().all(),
            JOB.resourceReturns().all(),
            JOB.settings().outputAllowances(),
            d("2025-03-25"));

    @Test
    void rendersTheTotalsTheStepsAndTheHoursWithNothingToShow() {
        assertThat(REPORT.render())
                .isEqualTo(String.join(
                        "\n",
                        "output at 2025-03-25",
                        "====================",
                        "",
                        "returns                     5",
                        "days worked                 5",
                        "labour hours                168",
                        "plant hours                 12",
                        "standing hours              4",
                        "idle hours                  0",
                        "hours that bought nothing   2.2 percent",
                        "steps behind the allowance  1",
                        "",
                        "package  step      metres  labour hr  plant hr  m per hr  allowed  against",
                        "-------  --------  ------  ---------  --------  --------  -------  -------",
                        "WP-01    strip        800         96         8      8.33    10.00      83%",
                        "WP-01    excavate     600         40         4     15.00    12.00     125%",
                        "WP-01    place        400          0         0",
                        "WP-01    trim           0         16         0      0.00",
                        "",
                        "hours with nothing measured against them",
                        "----------------------------------------",
                        "  WP-01 trim, 16 labour hours"));
    }

    @Test
    void listsTheStepsBehindTheAllowanceAndTheOnesWithNothingBuilt() {
        assertThat(REPORT.behind()).extracting(e -> e.step().stepId()).containsExactly("strip");
        assertThat(REPORT.barren()).extracting(PackageStep::packageId).containsExactly("WP-01");
        assertThat(REPORT.barren()).extracting(e -> e.step().stepId()).containsExactly("trim");
    }

    @Test
    void reportsTheHoursBookedWithoutAStep() {
        assertThat(REPORT.unallocatedHours()).containsExactly(new UnallocatedHours("WP-01", 16));
    }
}
