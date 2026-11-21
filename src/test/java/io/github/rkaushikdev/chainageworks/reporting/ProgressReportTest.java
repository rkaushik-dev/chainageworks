package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.domain.time.Standdown;
import io.github.rkaushikdev.chainageworks.domain.time.StanddownReason;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport.PackageLine;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport.StepLine;
import io.github.rkaushikdev.chainageworks.support.ContextFixtures.Overrides;
import io.github.rkaushikdev.chainageworks.valuation.PeriodSchedule;
import io.github.rkaushikdev.chainageworks.valuation.ScheduleInput;
import io.github.rkaushikdev.chainageworks.valuation.ValuationPeriod;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProgressReportTest {

    private static final ProgressContext CONTEXT = sampleContext();
    private static final PeriodSchedule SCHEDULE = PeriodSchedule.build(
            ScheduleInput.of(2025, 1, 6, 25, SiteCalendar.build(CalendarInput.of("C1", "main works"))));
    private static final ValuationPeriod MARCH = SCHEDULE.byNumber(3);
    private static final ProgressReport REPORT = ProgressReport.build(CONTEXT, MARCH);

    @Nested
    class BuildProgressReport {

        @Test
        void namesThePeriodAndTheBillRevisionItRead() {
            assertThat(REPORT.period().number()).isEqualTo(3);
            assertThat(REPORT.billRevision()).isEqualTo("Rev A");
        }

        @Test
        void givesOneLinePerPackageInRegisterOrder() {
            assertThat(REPORT.lines().stream().map(PackageLine::packageId)).containsExactly("WP-01", "WP-03", "WP-02");
        }

        @Test
        void carriesThePackageNameSectionAndExtent() {
            PackageLine line = REPORT.lines().get(0);
            assertThat(line.name()).containsPattern("earthworks");
            assertThat(line.section()).isEqualTo("section 1");
            assertThat(line.extent().format()).isEqualTo("12+000.000 to 12+800.000");
        }

        @Test
        void totalsTheBudgetAndWhatHasBeenEarned() {
            assertThat(REPORT.budget().major()).isEqualTo((double) (49_700 + 120_600 + 56_650));
            assertThat(REPORT.earned().major()).isEqualTo(16_152.5);
        }

        @Test
        void reportsTheEarnedFractionOnEachLine() {
            assertThat(REPORT.lines().get(0).fraction().value()).isEqualTo(32.5);
            assertThat(REPORT.lines().get(1).fraction().value()).isZero();
        }
    }

    @Nested
    class TheValueBeingHeldBack {

        @Test
        void pricesTheWorkThatIsBuiltAndCannotBePaidFor() {
            PackageLine line = REPORT.lines().get(0);
            assertThat(line.earnedIfReleased().major())
                    .isGreaterThan(line.earned().major());
            assertThat(line.heldValue().minor()).isPositive();
        }

        @Test
        void addsTheHeldValueAcrossTheJob() {
            assertThat(REPORT.heldValue().minor())
                    .isEqualTo(REPORT.lines().get(0).heldValue().minor());
        }

        @Test
        void listsThePackagesHoldingValueWorstFirst() {
            assertThat(REPORT.heldLines().stream().map(PackageLine::packageId)).containsExactly("WP-01");
        }

        @Test
        void fallsToNothingOnceEverythingIsReleased() {
            ProgressContext released = sampleContext(
                    Overrides.NONE.withSignOffs(List.of()).withNcrs(List.of()).withRecords(List.of()));
            ProgressReport clean = ProgressReport.build(released, MARCH);
            assertThat(clean.heldValue().minor()).isZero();
        }
    }

    @Nested
    class TheStepLines {

        private final List<StepLine> steps = REPORT.lines().get(0).steps();

        @Test
        void givesOnePerStepInTheRuleOfCredit() {
            assertThat(steps.stream().map(StepLine::stepId)).containsExactly("strip", "excavate", "place", "trim");
        }

        @Test
        void showsBuiltAgainstCredited() {
            StepLine excavate = steps.get(1);
            assertThat(excavate.builtFraction()).isEqualTo(0.75);
            assertThat(excavate.creditedFraction()).isEqualTo(0.5);
            assertThat(excavate.gate().wire()).isEqualTo("part_open");
        }

        @Test
        void namesTheChainageThatIsHeldBack() {
            assertThat(steps.get(1).heldBack().stream().map(ChainageRange::format))
                    .containsExactly("12+400.000 to 12+600.000");
        }

        @Test
        void reportsTheHoldPointsNobodyHasSigned() {
            ProgressReport nothingSigned =
                    ProgressReport.build(sampleContext(Overrides.NONE.withSignOffs(List.of())), MARCH);
            assertThat(nothingSigned.outstandingHoldPoints()).containsExactly("IP-E1", "IP-E2", "IP-P1");
        }

        @Test
        void stillNamesThePointsOnPackagesNobodyHasStarted() {
            assertThat(REPORT.outstandingHoldPoints()).containsExactly("IP-E1", "IP-E2", "IP-P1");
        }
    }

    @Nested
    class TheCalendarFigures {

        @Test
        void countsTheWorkingDaysInThePeriodWhichRunsCutOffToCutOff() {
            assertThat(REPORT.workingDays()).isEqualTo(20);
        }

        @Test
        void countsTheProductiveDaysTheSameWhenNothingWasStoodDown() {
            assertThat(REPORT.productiveDays()).isEqualTo(20);
            assertThat(REPORT.standdowns()).isEmpty();
        }

        @Test
        void takesStanddownsOffTheProductiveCount() {
            ProgressContext wet = sampleContext();
            ProgressReport withWeather = ProgressReport.build(
                    wet.withCalendar(SiteCalendar.build(CalendarInput.of("C2", "wet march")
                            .withStanddowns(List.of(
                                    new Standdown(d("2025-03-05"), StanddownReason.WEATHER),
                                    new Standdown(d("2025-03-06"), StanddownReason.WEATHER))))),
                    MARCH);
            assertThat(withWeather.workingDays()).isEqualTo(20);
            assertThat(withWeather.productiveDays()).isEqualTo(18);
            assertThat(withWeather.standdowns().stream().map(s -> SiteDates.format(s.date())))
                    .containsExactly("2025-03-05", "2025-03-06");
        }
    }

    @Nested
    class TheProblemsSection {

        @Test
        void carriesTheReconciliationAgainstTheBill() {
            assertThat(REPORT.reconciliation().stream().map(r -> r.code().format()))
                    .containsExactly("A213.1", "E423.1", "F624.1");
            assertThat(REPORT.hasProblems()).isTrue();
        }

        @Test
        void carriesNothingForOverMeasureWhenTheSurveyIsInsideTheAllowance() {
            assertThat(REPORT.overMeasures()).isEmpty();
        }
    }
}
