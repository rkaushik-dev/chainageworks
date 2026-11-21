package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.reporting.SubcontractReport.BarredContra;
import io.github.rkaushikdev.chainageworks.reporting.SubcontractReport.NoticeDiaryEntry;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubcontractReportTest {

    /** The commercial job with a third application on the earthworks order, still inside its window. */
    private static Job job() {
        Job job = JobFixtures.commercialJob();
        job.paymentApplications()
                .add(new PaymentApplication(
                        "SA-03",
                        "SC-EARTH",
                        3,
                        d("2025-03-25"),
                        Money.fromMajor(120_000, Currency.GBP),
                        Money.fromMajor(80_750, Currency.GBP)));
        return job;
    }

    private static final Job JOB = job();
    private static final SubcontractReport REPORT = SubcontractReport.build(
            JOB.subcontracts().all(),
            new Subcontract.Inputs(
                    JOB.paymentApplications().all(),
                    JOB.paymentNotices().all(),
                    JOB.payLessNotices().all(),
                    JOB.contraCharges().all(),
                    JOB.settings().contraNotifyDays()),
            d("2025-03-25"));

    @Test
    void rendersTheOrdersAndTheNoticeDiary() {
        assertThat(REPORT.render())
                .isEqualTo(String.join(
                        "\n",
                        "subcontract payments at 2025-03-25",
                        "==================================",
                        "",
                        "orders               2",
                        "notices outstanding  1",
                        "notices missed       1",
                        "over the order       0",
                        "set off barred       1",
                        "exposed by default   £40,000.00",
                        "",
                        "order     contractor                value    certified         paid    set off"
                                + "          net         left",
                        "--------  ------------------  -----------  -----------  -----------  ---------"
                                + "  -----------  -----------",
                        "SC-EARTH  Dowsett Plant Hire  £250,000.00  £114,000.00  £114,000.00  £6,000.00"
                                + "  £108,000.00  £136,000.00",
                        "SC-FENCE  Marchmont Fencing    £31,000.00        £0.00        £0.00      £0.00"
                                + "        £0.00   £31,000.00",
                        "",
                        "notice diary",
                        "------------",
                        "application  contractor          applied     notice by   applied for  state",
                        "-----------  ------------------  ----------  ----------  -----------  -------------",
                        "SA-01        Dowsett Plant Hire  2025-01-25  2025-03-01   £40,000.00  window closed",
                        "SA-03        Dowsett Plant Hire  2025-03-25  2025-04-29  £120,000.00  open"));
    }

    @Test
    void keepsTheDiarySoonestNoticeFirst() {
        assertThat(REPORT.diary()).extracting(NoticeDiaryEntry::applicationId).containsExactly("SA-01", "SA-03");
        assertThat(REPORT.diary()).extracting(NoticeDiaryEntry::missed).containsExactly(true, false);
        assertThat(REPORT.barredContra()).containsExactly(new BarredContra("SC-EARTH", "£950.00"));
    }

    @Test
    void namesOnlyTheWindowsClosingInsideTheHorizon() {
        assertThat(REPORT.noticesThisWeek(7)).isEmpty();
        assertThat(REPORT.noticesThisWeek(45)).containsExactly("SA-03 Dowsett Plant Hire, notice by 2025-04-29");
    }

    @Test
    void reportsNothingExposedOnAJobWithNoOrders() {
        SubcontractReport none = SubcontractReport.build(
                List.of(), Subcontract.Inputs.of(List.of(), List.of(), List.of()), d("2025-03-25"));
        assertThat(none.exposure()).isEqualTo("£0.00");
        assertThat(none.render()).doesNotContain("notice diary").doesNotContain("contractor");
    }
}
