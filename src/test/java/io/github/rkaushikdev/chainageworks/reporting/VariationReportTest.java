package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.variation.ValuationBasis;
import io.github.rkaushikdev.chainageworks.reporting.VariationReport.VariationLine;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariationReportTest {

    private static final Job JOB = JobFixtures.commercialJob();
    private static final LocalDate AS_AT = d("2025-03-25");
    private static final VariationReport REPORT = VariationReport.build(
            JOB.context().billHistory().billAsAt(AS_AT), JOB.variations().all(), AS_AT);

    @Test
    void rendersTheRegisterValuedOneOrderAtATime() {
        assertThat(REPORT.render())
                .isEqualTo(String.join(
                        "\n",
                        "variations at 2025-03-25",
                        "========================",
                        "",
                        "orders on the register    3",
                        "in force                  2",
                        "value in force            £65,555.00",
                        "not yet agreed            £2,425.00",
                        "proposed, not instructed  1",
                        "",
                        "order        title                               status      where                   "
                                + "  basis            value",
                        "-----------  ----------------------------------  ----------  ------------------------"
                                + "  ----------  ----------",
                        "VO/2025/001  extra rock excavation at the cutt…  instructed  12+300.000 to 12+500.000"
                                + "  bill_rates   £2,425.00",
                        "VO/2025/002  temporary access off the haul road  agreed                              "
                                + "  pro_rata    £63,130.00",
                        "VO/2025/003  additional drainage at the low po…  proposed                            "
                                + "  bill_rates       £0.00"));
    }

    @Test
    void namesEachWayAnOrderIsPricedOnce() {
        assertThat(REPORT.lines())
                .extracting(VariationLine::bases)
                .containsExactly(
                        List.of(ValuationBasis.BILL_RATES),
                        List.of(ValuationBasis.PRO_RATA),
                        List.of(ValuationBasis.BILL_RATES));
    }

    @Test
    void chasesTheOrdersInForceWithNoAgreedPrice() {
        assertThat(REPORT.chaseAgreement())
                .containsExactly("VO/2025/001 extra rock excavation at the cutting, £2,425.00 not agreed");
    }

    @Test
    void listsEveryItemAnOrderInForceTouches() {
        assertThat(REPORT.touchedItems()).containsExactly("E423.1 500.00 m3", "E532.1 200.00 m3");
    }
}
