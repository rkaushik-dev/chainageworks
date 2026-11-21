package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarning;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskReportTest {

    private static final Job JOB = JobFixtures.commercialJob();
    private static final EarlyWarning.Position POSITION = EarlyWarning.position(
            JOB.earlyWarnings().all(),
            d("2025-03-25"),
            Currency.GBP,
            JOB.settings().riskMeetingDays());

    @Test
    void rendersTheRegisterDearestExpectedCostFirst() {
        assertThat(RiskReport.render(POSITION))
                .isEqualTo(String.join(
                        "\n",
                        "early warnings at 2025-03-25",
                        "============================",
                        "",
                        "open                       2",
                        "expected cost              £39,000.00",
                        "worst case                 £80,000.00",
                        "carried by the contractor  £15,000.00",
                        "working days at risk       17",
                        "meetings overdue           1",
                        "owner not agreed           0",
                        "",
                        "warning      description                             owner       where                   "
                                + "  if it happens  likely    expected  days",
                        "-----------  --------------------------------------  ----------  ------------------------"
                                + "  -------------  ------  ----------  ----",
                        "EW/2025/001  unrecorded services under the roundab…  employer    12+400.000 to 12+600.000"
                                + "     £60,000.00     40%  £24,000.00    12",
                        "EW/2025/002  soft spots in the formation south of …  contractor                          "
                                + "     £20,000.00     75%  £15,000.00     5"));
    }

    @Test
    void listsTheMeetingsToArrangeOldestFirst() {
        assertThat(RiskReport.meetingsToArrange(POSITION))
                .containsExactly("EW/2025/002 raised 2025-02-20, no meeting held");
    }

    @Test
    void leavesTheTableOffAnEmptyRegister() {
        EarlyWarning.Position empty = EarlyWarning.position(List.of(), d("2025-03-25"), Currency.GBP);
        assertThat(RiskReport.render(empty)).doesNotContain("warning ").contains("open                       0");
        assertThat(RiskReport.meetingsToArrange(empty)).isEmpty();
    }
}
