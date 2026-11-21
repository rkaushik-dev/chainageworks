package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.delay.BarReason;
import io.github.rkaushikdev.chainageworks.domain.delay.Entitlement;
import io.github.rkaushikdev.chainageworks.reporting.DelayReport.DelayLine;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DelayReportTest {

    private static final Job JOB = JobFixtures.commercialJob();

    private static DelayReport reportAt(LocalDate asAt) {
        return DelayReport.build(
                JOB.settings().completion().orElseThrow(),
                JOB.delayEvents().all(),
                JOB.calendar(),
                JOB.settings().contractSum(),
                asAt);
    }

    private static final DelayReport REPORT = reportAt(d("2025-03-25"));

    @Test
    void rendersThePositionAndThenTheEvents() {
        assertThat(REPORT.render())
                .isEqualTo(String.join(
                        "\n",
                        "extension of time position at 2025-03-25",
                        "========================================",
                        "",
                        "completion    2025-09-26",
                        "extended to   2025-10-08",
                        "days claimed  20",
                        "days awarded  8",
                        "days barred   10",
                        "days late     0",
                        "damages       £0.00",
                        "",
                        "event        cause      from        to          days  awarded  state",
                        "-----------  ---------  ----------  ----------  ----  -------  ------------",
                        "EW/2025/003  utilities  2025-03-03  2025-03-14    10        8  claimable",
                        "EW/2025/004  design     2025-03-10  2025-03-21    10           not notified"));
    }

    @Test
    void separatesTheBarredEventsFromTheOnesStillToBeAssessed() {
        assertThat(REPORT.claimedDays()).isEqualTo(20);
        List<DelayLine> barred = REPORT.barredLines();
        assertThat(barred).extracting(DelayLine::reference).containsExactly("EW/2025/004");
        assertThat(barred.get(0).bar()).contains(BarReason.NOT_NOTIFIED);
        assertThat(barred.get(0).entitlement()).isEqualTo(Entitlement.TIME_AND_MONEY);
        assertThat(barred.get(0).carriesMoney()).isFalse();
        assertThat(REPORT.unassessedLines()).isEmpty();
        assertThat(DelayReport.zeroDamages(JOB.settings().contractSum()).minor())
                .isZero();
    }

    @Test
    void notesTheCapOnceTheDamagesReachIt() {
        DelayReport late = reportAt(d("2025-12-31"));
        assertThat(late.daysLate()).isEqualTo(84);
        assertThat(late.damagesCapped()).isTrue();
        List<String> lines = Text.split(late.render(), "\n");
        assertThat(lines).contains("days late     84", "damages       £31,219.00");
        assertThat(lines.subList(lines.size() - 2, lines.size()))
                .containsExactly("", "damages have reached the contract cap");
    }
}
