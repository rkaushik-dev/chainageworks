package io.github.rkaushikdev.chainageworks.valuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CashFlowTest {

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final PeriodSchedule SCHEDULE = PeriodSchedule.build(
            ScheduleInput.of(2025, 1, 6, 25, SiteCalendar.build(CalendarInput.of("C1", "main works"))));

    private static final List<Money> CERTIFIED =
            List.of(gbp(10_000), gbp(35_000), gbp(80_000), gbp(140_000), gbp(180_000), gbp(200_000));

    private static final List<CashPoint> ON_TIME =
            CashFlow.build(CashFlowInput.of(SCHEDULE.periods(), CERTIFIED, Currency.GBP));

    private static final List<CashPoint> LATE = CashFlow.build(
            CashFlowInput.of(SCHEDULE.periods(), CERTIFIED, Currency.GBP).withLatePeriods(1));

    private static List<CashPoint> flowOf(Money... certified) {
        return CashFlow.build(CashFlowInput.of(SCHEDULE.periods(), List.of(certified), Currency.GBP));
    }

    @Nested
    class BuildCashFlow {

        @Test
        void givesAPointForEveryPeriod() {
            assertThat(ON_TIME).hasSize(6);
            assertThat(ON_TIME.get(0).period()).isEqualTo(1);
            assertThat(SiteDates.format(ON_TIME.get(0).cutOff())).isEqualTo("2025-01-24");
        }

        @Test
        void takesTheMovementOffTheCumulativeSeries() {
            assertThat(ON_TIME.get(0).certificate().major()).isEqualTo(10_000);
            assertThat(ON_TIME.get(1).certificate().major()).isEqualTo(25_000);
            assertThat(ON_TIME.get(3).certificate().major()).isEqualTo(60_000);
        }

        @Test
        void owesNothingAtAnyPointWhenEverythingIsPaidOnTime() {
            assertThat(ON_TIME.stream().allMatch(point -> point.outstanding().minor() == 0))
                    .isTrue();
            assertThat(ON_TIME.get(5).received().major()).isEqualTo(200_000);
        }

        @Test
        void carriesAPeriodOfLatePaymentThroughEveryFigure() {
            assertThat(LATE.get(0).received().major()).isZero();
            assertThat(LATE.get(0).outstanding().major()).isEqualTo(10_000);
            assertThat(LATE.get(1).receipt().major()).isEqualTo(10_000);
            assertThat(LATE.get(3).outstanding().major()).isEqualTo(60_000);
        }

        @Test
        void stillCertifiesTheSameFiguresHoweverLateThePayerIs() {
            assertThat(LATE.stream().map(p -> p.certified().major()).toList())
                    .isEqualTo(ON_TIME.stream().map(p -> p.certified().major()).toList());
        }

        @Test
        void refusesACertifiedSeriesThatDoesNotMatchTheSchedule() {
            assertThatThrownBy(() ->
                            CashFlow.build(CashFlowInput.of(SCHEDULE.periods(), CERTIFIED.subList(0, 3), Currency.GBP)))
                    .hasMessageContaining("does not match the schedule");
        }

        @Test
        void refusesHalfAPeriodOfLatePayment() {
            // The late count is an int, so the compiler refuses half a period; a negative count is the
            // other value the same guard turns away, with the same message.
            assertThatThrownBy(() -> CashFlow.build(CashFlowInput.of(SCHEDULE.periods(), CERTIFIED, Currency.GBP)
                            .withLatePeriods(-1)))
                    .hasMessageContaining("whole number of periods");
        }

        @Test
        void handlesASeriesThatGoesBackwardsAfterARemeasure() {
            List<CashPoint> dropped =
                    flowOf(gbp(10_000), gbp(35_000), gbp(30_000), gbp(60_000), gbp(70_000), gbp(80_000));
            assertThat(dropped.get(2).certificate().major()).isEqualTo(-5000);
        }
    }

    @Nested
    class PeakExposure {

        @Test
        void findsThePeriodTheJobIsOwedTheMost() {
            assertThat(CashFlow.peakExposure(LATE).orElseThrow().period()).isEqualTo(4);
            assertThat(CashFlow.peakExposure(LATE).orElseThrow().outstanding().major())
                    .isEqualTo(60_000);
        }

        @Test
        void isNullOnAnEmptyForecast() {
            assertThat(CashFlow.peakExposure(List.of())).isEmpty();
        }

        @Test
        void isNullWhenTheJobIsNeverOwedAnything() {
            assertThat(CashFlow.peakExposure(ON_TIME)).isEmpty();
        }
    }

    @Nested
    class TotalReceipts {

        @Test
        void addsUpEverythingThatComesIn() {
            assertThat(CashFlow.totalReceipts(ON_TIME, Currency.GBP).major()).isEqualTo(200_000);
        }

        @Test
        void leavesOutWhatTheLatePayerHasNotPaidYet() {
            assertThat(CashFlow.totalReceipts(LATE, Currency.GBP).major()).isEqualTo(180_000);
        }
    }

    @Nested
    class ReceiptsBetween {

        @Test
        void addsUpTheReceiptsFallingDueInsideTheWindow() {
            LocalDate from = SiteDates.parse("2025-03-01");
            LocalDate to = SiteDates.parse("2025-05-31");
            assertThat(CashFlow.receiptsBetween(ON_TIME, from, to, Currency.GBP).major())
                    .isEqualTo(130_000);
        }

        @Test
        void isNothingWhenTheWindowCatchesNoDueDates() {
            LocalDate from = SiteDates.parse("2026-01-01");
            LocalDate to = SiteDates.parse("2026-02-01");
            assertThat(CashFlow.receiptsBetween(ON_TIME, from, to, Currency.GBP).minor())
                    .isZero();
        }
    }

    @Nested
    class FirstDryPeriod {

        @Test
        void namesTheFirstPeriodAfterTheFirstWithNothingComingIn() {
            List<CashPoint> flat = flowOf(gbp(10_000), gbp(35_000), gbp(35_000), gbp(60_000), gbp(70_000), gbp(80_000));
            assertThat(CashFlow.firstDryPeriod(flat)).contains(3);
        }

        @Test
        void isNullWhenSomethingComesInEveryPeriod() {
            assertThat(CashFlow.firstDryPeriod(ON_TIME)).isEmpty();
        }
    }

    @Nested
    class DescribeCashPoint {

        @Test
        void saysThePeriodTheDueDateAndWhatIsOwed() {
            assertThat(LATE.get(3).describe()).isEqualTo("period 4, due 2025-05-20, 60000 outstanding");
        }
    }
}
