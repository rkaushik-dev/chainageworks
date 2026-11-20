package io.github.rkaushikdev.chainageworks.domain.subcontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PaymentTermsTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final PaymentTerms TERMS = PaymentTerms.build(PaymentTerms.DEFAULT);

    private static final PaymentApplication APPLICATION =
            new PaymentApplication("SA-03", "SC-EARTHWORKS", 3, d("2025-03-25"), gbp(120_000), gbp(60_000));

    private static Optional<PaymentNotice> notice(String servedOn) {
        return Optional.of(new PaymentNotice("SA-03", d(servedOn), gbp(100_000)));
    }

    @Nested
    class Build {

        @Test
        void carriesTheUsualStatutoryWindows() {
            assertThat(TERMS).isEqualTo(PaymentTerms.DEFAULT);
            assertThat(TERMS.dueDays()).isEqualTo(30);
            assertThat(TERMS.noticeDays()).isEqualTo(5);
        }

        @Test
        void takesTermsOfItsOwn() {
            assertThat(PaymentTerms.build(PaymentTerms.DEFAULT.withDueDays(21).withRetention(Percent.of(3)))
                            .dueDays())
                    .isEqualTo(21);
        }

        @Test
        void refusesAWindowThatIsNotAWholeNumberOfDays() {
            // Windows are ints, so a fraction of a day cannot be handed in; a negative one is refused.
            assertThatThrownBy(() -> PaymentTerms.build(PaymentTerms.DEFAULT.withDueDays(-1)))
                    .hasMessageContaining("whole number of days");
            assertThatThrownBy(() -> PaymentTerms.build(PaymentTerms.DEFAULT.withNoticeDays(-2)))
                    .hasMessageContaining("whole number of days");
        }

        @Test
        void refusesAPayLessWindowThatOpensBeforeTheDueDate() {
            assertThatThrownBy(() -> PaymentTerms.build(
                            PaymentTerms.DEFAULT.withFinalDays(3).withPayLessDays(7)))
                    .hasMessageContaining("cannot start before the due date");
        }
    }

    @Nested
    class Dates {

        @Test
        void countsEveryWindowOffTheApplication() {
            PaymentTerms.Dates dates = TERMS.datesFor(APPLICATION);
            assertThat(SiteDates.format(dates.dueOn())).isEqualTo("2025-04-24");
            assertThat(SiteDates.format(dates.noticeBy())).isEqualTo("2025-04-29");
            assertThat(SiteDates.format(dates.finalDateForPayment())).isEqualTo("2025-06-08");
            assertThat(SiteDates.format(dates.payLessBy())).isEqualTo("2025-06-01");
        }
    }

    @Nested
    class Position {

        @Test
        void takesThePaymentNoticeWhenItWasServedInTime() {
            PaymentTerms.Position position = TERMS.position(APPLICATION, notice("2025-04-28"), Optional.empty());
            assertThat(position.basis()).isEqualTo(NotifiedBasis.PAYMENT_NOTICE);
            assertThat(position.notifiedGross().major()).isEqualTo(100_000);
            assertThat(position.retention().major()).isEqualTo(5000);
            assertThat(position.payable().major()).isEqualTo(35_000);
            assertThat(position.byDefault()).isFalse();
        }

        @Test
        void fallsBackToTheApplicationWhenNoNoticeWasServedAtAll() {
            PaymentTerms.Position position = TERMS.position(APPLICATION, Optional.empty(), Optional.empty());
            assertThat(position.basis()).isEqualTo(NotifiedBasis.APPLICATION_BY_DEFAULT);
            assertThat(position.byDefault()).isTrue();
            assertThat(position.notifiedGross().major()).isEqualTo(120_000);
            assertThat(position.payable().major()).isEqualTo(54_000);
        }

        @Test
        void fallsBackToTheApplicationWhenTheNoticeWasADayLate() {
            PaymentTerms.Position position = TERMS.position(APPLICATION, notice("2025-04-30"), Optional.empty());
            assertThat(position.byDefault()).isTrue();
            assertThat(position.notifiedGross().major()).isEqualTo(120_000);
        }

        @Test
        void takesANoticeServedOnTheLastDayOfTheWindow() {
            assertThat(TERMS.position(APPLICATION, notice("2025-04-29"), Optional.empty())
                            .basis())
                    .isEqualTo(NotifiedBasis.PAYMENT_NOTICE);
        }

        @Test
        void letsAPayLessNoticeBeatThePaymentNotice() {
            PaymentTerms.Position position = TERMS.position(
                    APPLICATION,
                    notice("2025-04-28"),
                    Optional.of(new PayLessNotice(
                            "SA-03", d("2025-05-30"), gbp(20_000), "defective work at the headwall")));
            assertThat(position.basis()).isEqualTo(NotifiedBasis.PAY_LESS_NOTICE);
            assertThat(position.payable().major()).isEqualTo(20_000);
        }

        @Test
        void ignoresAPayLessNoticeServedTooLate() {
            PaymentTerms.Position position = TERMS.position(
                    APPLICATION,
                    notice("2025-04-28"),
                    Optional.of(new PayLessNotice("SA-03", d("2025-06-05"), gbp(20_000), "too late")));
            assertThat(position.basis()).isEqualTo(NotifiedBasis.PAYMENT_NOTICE);
            assertThat(position.payable().major()).isEqualTo(35_000);
        }

        @Test
        void neverPaysANegativeAmountWhenTheValuationHasGoneBackwards() {
            PaymentTerms.Position position = TERMS.position(
                    APPLICATION.withPreviouslyPaid(gbp(200_000)), notice("2025-04-28"), Optional.empty());
            assertThat(position.payable()).isEqualTo(Money.zero(Currency.GBP));
        }

        @Test
        void holdsNoRetentionWhenTheSubcontractHoldsNone() {
            PaymentTerms.Position position = PaymentTerms.build(PaymentTerms.DEFAULT.withRetention(Percent.of(0)))
                    .position(APPLICATION, Optional.empty(), Optional.empty());
            assertThat(position.retention().minor()).isZero();
            assertThat(position.payable().major()).isEqualTo(60_000);
        }
    }

    @Nested
    class TheNoticeDiary {

        private final List<PaymentApplication> applications =
                List.of(APPLICATION, APPLICATION.withId("SA-04").withPeriod(4, d("2025-04-25")));
        private final List<PaymentNotice> served = List.of(new PaymentNotice("SA-03", d("2025-04-28"), gbp(100_000)));

        @Test
        void listsWhatStillNeedsANotice() {
            assertThat(TERMS.noticesDue(applications, served, d("2025-05-20")).stream()
                            .map(PaymentApplication::id))
                    .containsExactly("SA-04");
        }

        @Test
        void listsNothingOnceEveryWindowHasClosed() {
            assertThat(TERMS.noticesDue(applications, served, d("2025-07-01"))).isEmpty();
        }

        @Test
        void namesTheApplicationsThatDefaultedToTheAppliedForSum() {
            assertThat(TERMS.defaultedApplications(applications, served, d("2025-07-01")).stream()
                            .map(PaymentApplication::id))
                    .containsExactly("SA-04");
            assertThat(TERMS.defaultedApplications(applications, served, d("2025-05-01")))
                    .isEmpty();
        }
    }

    @Nested
    class Describe {

        @Test
        void saysHowTheFigureWasArrivedAt() {
            PaymentTerms.Position byDefault = TERMS.position(APPLICATION, Optional.empty(), Optional.empty());
            assertThat(byDefault.describe())
                    .isEqualTo("payable on the application, no notice served in time, final date 2025-06-08");
        }

        @Test
        void namesTheNoticeWhenThereWasOne() {
            PaymentTerms.Position noticed = TERMS.position(APPLICATION, notice("2025-04-28"), Optional.empty());
            assertThat(noticed.describe()).contains("payment notice");
        }
    }
}
