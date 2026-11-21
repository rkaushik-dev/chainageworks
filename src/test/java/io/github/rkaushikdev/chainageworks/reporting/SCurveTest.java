package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.valuation.PeriodSchedule;
import io.github.rkaushikdev.chainageworks.valuation.ScheduleInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SCurveTest {

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final ProgressContext CONTEXT = sampleContext();
    private static final PeriodSchedule SCHEDULE = PeriodSchedule.build(
            ScheduleInput.of(2025, 1, 6, 25, SiteCalendar.build(CalendarInput.of("C1", "main works"))));

    @Nested
    class LinearBaseline {

        @Test
        void spreadsTheTotalEvenlyAndEndsOnIt() {
            Baseline baseline = Baseline.linear(gbp(1200), 4);
            assertThat(baseline.cumulative().stream().map(Money::major)).containsExactly(300.0, 600.0, 900.0, 1200.0);
        }

        @Test
        void neverLosesAPennyToRounding() {
            Baseline baseline = Baseline.linear(gbp(1000.01), 3);
            assertThat(baseline.cumulative().get(2).minor()).isEqualTo(100_001);
        }

        @Test
        void refusesABaselineWithNoPeriods() {
            assertThatThrownBy(() -> Baseline.linear(gbp(100), 0))
                    .isInstanceOf(BadInputException.class)
                    .hasMessageContaining("at least one period");
            // A period count of 1.5 cannot be passed: the parameter is an int.
        }
    }

    @Nested
    class SCurveBaseline {

        private final Baseline baseline = Baseline.sCurve(gbp(1_000_000), 6);

        @Test
        void endsOnTheTotal() {
            assertThat(baseline.cumulative().get(5).major()).isEqualTo(1_000_000.0);
        }

        @Test
        void risesThroughTheMiddleFasterThanAtEitherEnd() {
            List<Money> cumulative = baseline.cumulative();
            List<Long> shares = new ArrayList<>();
            for (int i = 0; i < cumulative.size(); i++) {
                shares.add(
                        i == 0
                                ? cumulative.get(i).minor()
                                : cumulative.get(i).minor()
                                        - cumulative.get(i - 1).minor());
            }
            assertThat(shares.get(0)).isLessThan(shares.get(2));
            assertThat(shares.get(5)).isLessThan(shares.get(3));
        }

        @Test
        void neverGoesBackwards() {
            List<Money> cumulative = baseline.cumulative();
            for (int i = 1; i < cumulative.size(); i++) {
                assertThat(cumulative.get(i).minor())
                        .isGreaterThanOrEqualTo(cumulative.get(i - 1).minor());
            }
        }

        @Test
        void handlesASinglePeriod() {
            assertThat(Baseline.sCurve(gbp(500), 1).cumulative().get(0).major()).isEqualTo(500.0);
        }

        @Test
        void refusesABaselineWithNoPeriods() {
            assertThatThrownBy(() -> Baseline.sCurve(gbp(100), 0))
                    .isInstanceOf(BadInputException.class)
                    .hasMessageContaining("at least one period");
        }
    }

    @Nested
    class BuildSCurve {

        private final Baseline baseline = Baseline.linear(gbp(120_000), 6);
        private final List<CurvePoint> points = SCurve.build(CONTEXT, SCHEDULE, baseline);

        @Test
        void givesOnePointPerPeriod() {
            assertThat(points.stream().map(CurvePoint::period)).containsExactly(1, 2, 3, 4, 5, 6);
            assertThat(SiteDates.format(points.get(2).cutOff())).isEqualTo("2025-03-25");
        }

        @Test
        void readsThePlannedFigureOffTheBaseline() {
            assertThat(points.stream().map(p -> p.planned().major()))
                    .containsExactly(20_000.0, 40_000.0, 60_000.0, 80_000.0, 100_000.0, 120_000.0);
        }

        @Test
        void earnsNothingBeforeTheWorkStartedAndSomethingOnceItDid() {
            assertThat(points.get(1).earned().minor()).isZero();
            assertThat(points.get(2).earned().major()).isEqualTo(16_152.5);
        }

        @Test
        void reportsTheVarianceAsEarnedLessPlanned() {
            assertThat(points.get(2).variance().major()).isEqualTo(16_152.5 - 60_000);
            assertThat(points.get(2).isBehind()).isTrue();
        }

        @Test
        void reportsEachFigureAsAShareOfTheTotal() {
            assertThat(points.get(2).plannedShare().value()).isEqualTo(50.0);
            assertThat(points.get(2).earnedShare().value()).isCloseTo(13.46, byLessThan(0.05));
        }

        @Test
        void namesTheFirstPeriodTheJobFellBehind() {
            assertThat(SCurve.firstBehindPeriod(points)).contains(1);
        }

        @Test
        void reportsTheClosingVariance() {
            assertThat(SCurve.closingVariance(points).orElseThrow().major()).isEqualTo(16_152.5 - 120_000);
            assertThat(SCurve.closingVariance(List.of())).isEmpty();
        }

        @Test
        void findsNoRegressionWhenTheEarnedFigureOnlyEverRises() {
            assertThat(SCurve.regressions(points)).isEmpty();
        }

        @Test
        void refusesAnEmptyBaseline() {
            assertThatThrownBy(() -> SCurve.build(CONTEXT, SCHEDULE, new Baseline(List.of())))
                    .isInstanceOf(BadInputException.class)
                    .hasMessageContaining("at least one period");
        }

        @Test
        void holdsTheLastPlannedFigureWhenTheScheduleRunsPastTheBaseline() {
            Baseline shortBaseline = Baseline.linear(gbp(120_000), 2);
            List<CurvePoint> longCurve = SCurve.build(CONTEXT, SCHEDULE, shortBaseline);
            assertThat(longCurve.get(5).planned().major()).isEqualTo(120_000.0);
        }
    }
}
