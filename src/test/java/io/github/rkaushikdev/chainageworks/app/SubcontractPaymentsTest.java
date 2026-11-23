package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentNotice;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentTerms;
import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.domain.subcontract.SubcontractInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.reporting.SubcontractReport;
import io.github.rkaushikdev.chainageworks.reporting.SubcontractReport.NoticeDiaryEntry;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobDocuments;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The subcontract payment position read through the service at a valuation cut-off, and kept through the job file. */
class SubcontractPaymentsTest {

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static Job jobWithSubcontracts() {
        Job job = sampleJob();

        job.subcontracts()
                .add(Subcontract.build(SubcontractInput.of(
                                "SC-EARTH",
                                "bulk earthworks and capping",
                                "Dowsett Plant Hire",
                                gbp(250_000),
                                d("2024-11-04"))
                        .withPackages(List.of("WP-01", "WP-02"))
                        .withCodes(List.of(ItemCode.parse("E423.1")))));
        job.subcontracts()
                .add(Subcontract.build(SubcontractInput.of(
                                "SC-FENCE", "permanent fencing", "Marchmont Fencing", gbp(31_000), d("2025-01-13"))
                        .withPackages(List.of("WP-03"))
                        .withTerms(PaymentTerms.build(
                                PaymentTerms.DEFAULT.withDueDays(21).withRetention(Percent.of(3))))));

        job.paymentApplications()
                .add(new PaymentApplication("SA-01", "SC-EARTH", 1, d("2025-01-25"), gbp(40_000), gbp(0)));
        job.paymentApplications()
                .add(new PaymentApplication("SA-02", "SC-EARTH", 2, d("2025-02-25"), gbp(90_000), gbp(38_000)));
        job.paymentApplications()
                .add(new PaymentApplication("SA-03", "SC-EARTH", 3, d("2025-03-25"), gbp(120_000), gbp(80_750)));

        job.paymentNotices().add(new PaymentNotice("SA-02", d("2025-03-28"), gbp(85_000)));
        return job;
    }

    private static SubcontractReport paymentsAt(Job job, int valuation) {
        return new Service(job, Config.DEFAULT).paymentsFor(valuation);
    }

    @Nested
    class PaymentsFor {

        @Test
        void runsAtTheSameCutOffAsTheCertificate() {
            SubcontractReport report = paymentsAt(jobWithSubcontracts(), 3);
            assertThat(SiteDates.format(report.asAt())).isEqualTo("2025-03-25");
            assertThat(report.positions()).hasSize(2);
        }

        @Test
        void valuesTheOrderThePayerAnsweredAndTheOneItDidNot() {
            Subcontract.Position earth =
                    paymentsAt(jobWithSubcontracts(), 3).positions().get(0);
            assertThat(earth.subcontract().id()).isEqualTo("SC-EARTH");
            assertThat(earth.applications()).isEqualTo(3);
            assertThat(earth.certifiedToDate().major()).isEqualTo(114_000);
            assertThat(earth.paidToDate().major()).isEqualTo(114_000);
            assertThat(earth.remaining().major()).isEqualTo(136_000);
            assertThat(earth.defaulted()).containsExactly("SA-01", "SA-03");
        }

        @Test
        void leavesAnOrderNobodyHasAppliedAgainstAtItsFullValue() {
            Subcontract.Position fence =
                    paymentsAt(jobWithSubcontracts(), 3).positions().get(1);
            assertThat(fence.applications()).isZero();
            assertThat(fence.remaining().major()).isEqualTo(31_000);
        }

        @Test
        void putsTheClosedWindowAtTheTopOfTheDiary() {
            SubcontractReport report = paymentsAt(jobWithSubcontracts(), 3);
            assertThat(report.diary())
                    .extracting(NoticeDiaryEntry::applicationId)
                    .containsExactly("SA-01", "SA-03");
            assertThat(report.diary().get(0).missed()).isTrue();
            assertThat(SiteDates.format(report.diary().get(0).noticeBy())).isEqualTo("2025-03-01");
            assertThat(report.diary().get(1).missed()).isFalse();
        }

        @Test
        void pricesWhatTheMissedWindowHasAlreadyCost() {
            assertThat(paymentsAt(jobWithSubcontracts(), 3).exposure()).isEqualTo("£40,000.00");
        }

        @Test
        void keepsTheAnsweredApplicationOutOfTheDiary() {
            assertThat(paymentsAt(jobWithSubcontracts(), 3).diary())
                    .extracting(NoticeDiaryEntry::applicationId)
                    .doesNotContain("SA-02");
        }

        @Test
        void reportsNothingOverTheOrder() {
            assertThat(paymentsAt(jobWithSubcontracts(), 3).overOrder()).isEmpty();
        }

        @Test
        void namesTheOrderThatHasRunPastItsValue() {
            Job job = jobWithSubcontracts();
            job.subcontracts()
                    .put(Subcontract.build(SubcontractInput.of(
                            "SC-EARTH",
                            "bulk earthworks and capping",
                            "Dowsett Plant Hire",
                            gbp(90_000),
                            d("2024-11-04"))));
            SubcontractReport report = paymentsAt(job, 3);
            assertThat(report.overOrder())
                    .extracting(position -> position.subcontract().id())
                    .containsExactly("SC-EARTH");
            assertThat(report.overOrder().get(0).remaining().major()).isEqualTo(-24_000);
        }

        @Test
        void isEmptyOnAJobWithNoOrdersPlaced() {
            SubcontractReport report = paymentsAt(sampleJob(), 3);
            assertThat(report.positions()).isEmpty();
            assertThat(report.diary()).isEmpty();
            assertThat(report.exposure()).isEqualTo("£0.00");
        }
    }

    @Nested
    class NoticesThisWeek {

        @Test
        void namesOnlyTheWindowsClosingInsideTheHorizon() {
            SubcontractReport report = paymentsAt(jobWithSubcontracts(), 3);
            assertThat(report.noticesThisWeek(7)).isEmpty();
            assertThat(report.noticesThisWeek(45)).containsExactly("SA-03 Dowsett Plant Hire, notice by 2025-04-29");
        }
    }

    @Nested
    class RenderSubcontractReport {

        @Test
        void tablesTheOrdersAndThenTheDiary() {
            String text = paymentsAt(jobWithSubcontracts(), 3).render();
            assertThat(Text.split(text, "\n").get(0)).isEqualTo("subcontract payments at 2025-03-25");
            assertThat(text).containsPattern("notices missed +1");
            assertThat(text).contains("notice diary");
            assertThat(text).contains("window closed");
            assertThat(text).contains("Marchmont Fencing");
        }

        @Test
        void leavesBothTablesOutWhenThereIsNothingToSay() {
            String text = paymentsAt(sampleJob(), 3).render();
            assertThat(text).doesNotContain("notice diary");
            assertThat(text).doesNotContain("contractor");
        }
    }

    @Nested
    class TheSubcontractRegistersThroughTheJobFile {

        @Test
        void roundTripsTheOrdersTheApplicationsAndTheNotices() {
            Job again = JobDocuments.throughText(jobWithSubcontracts());
            assertThat(again.subcontracts().count()).isEqualTo(2);
            assertThat(again.paymentApplications().count()).isEqualTo(3);
            assertThat(again.paymentNotices().count()).isEqualTo(1);
            assertThat(again.subcontracts().require("SC-FENCE").terms().dueDays())
                    .isEqualTo(21);
            assertThat(again.subcontracts().require("SC-EARTH").codes()).hasSize(1);
            assertThat(again.paymentApplications().require("SA-03").gross().major())
                    .isEqualTo(120_000);
        }

        @Test
        void readsTheSamePositionBackOffTheFile() {
            Job again = JobDocuments.throughText(jobWithSubcontracts());
            SubcontractReport report = paymentsAt(again, 3);
            assertThat(report.positions().get(0).certifiedToDate().major()).isEqualTo(114_000);
            assertThat(report.exposure()).isEqualTo("£40,000.00");
        }

        @Test
        void keepsARetentionLimitAndCertifiesAgainstIt() {
            Job job = jobWithSubcontracts();
            Subcontract earth = job.subcontracts().require("SC-EARTH");
            job.subcontracts()
                    .put(Subcontract.build(SubcontractInput.of(
                                    earth.id(), earth.name(), earth.contractor(), earth.orderValue(), earth.placedOn())
                            .withPackages(earth.packageIds())
                            .withCodes(earth.codes())
                            .withTerms(PaymentTerms.build(earth.terms().withRetentionLimit(Percent.of(1))))));
            Job again = JobDocuments.throughText(job);
            assertThat(again.subcontracts().require("SC-EARTH").terms().retentionLimit())
                    .contains(Percent.of(1));
            assertThat(again.subcontracts().require("SC-FENCE").terms().retentionLimit())
                    .isEmpty();
            assertThat(paymentsAt(again, 3).positions().get(0).certifiedToDate().major())
                    .isEqualTo(117_500);
        }

        @Test
        void carriesAJobWithNoSubcontractsThroughAsEmptyRegisters() {
            Job again = JobDocuments.throughText(sampleJob());
            assertThat(again.subcontracts().count()).isZero();
            assertThat(again.payLessNotices().count()).isZero();
        }
    }
}
