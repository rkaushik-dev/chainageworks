package io.github.rkaushikdev.chainageworks.valuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PeriodScheduleTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static final SiteCalendar WEEKDAYS = SiteCalendar.build(CalendarInput.of("C1", "main works"));
    private static final SiteCalendar EASTER = SiteCalendar.build(
            CalendarInput.of("C2", "main works with holidays").withHolidays(List.of(d("2025-04-18"), d("2025-04-21"))));

    private static final PeriodSchedule SCHEDULE = PeriodSchedule.build(ScheduleInput.of(2025, 1, 6, 25, WEEKDAYS));

    @Nested
    class ResolveCutOff {

        @Test
        void takesTheDayAskedForWhenItIsAWorkingDay() {
            assertThat(SiteDates.format(PeriodSchedule.resolveCutOff(WEEKDAYS, 2025, 3, 25)))
                    .isEqualTo("2025-03-25");
        }

        @Test
        void pullsBackOffAWeekend() {
            assertThat(SiteDates.format(PeriodSchedule.resolveCutOff(WEEKDAYS, 2025, 5, 25)))
                    .isEqualTo("2025-05-23");
        }

        @Test
        void pullsBackOffAHolidayAsWell() {
            assertThat(SiteDates.format(PeriodSchedule.resolveCutOff(EASTER, 2025, 4, 21)))
                    .isEqualTo("2025-04-17");
        }

        @Test
        void clampsADayPastTheEndOfAShortMonth() {
            assertThat(SiteDates.format(PeriodSchedule.resolveCutOff(WEEKDAYS, 2025, 2, 31)))
                    .isEqualTo("2025-02-28");
        }
    }

    @Nested
    class BuildSchedule {

        @Test
        void numbersThePeriodsFromOne() {
            assertThat(SCHEDULE.periods().stream().map(ValuationPeriod::number)).containsExactly(1, 2, 3, 4, 5, 6);
            assertThat(SCHEDULE.count()).isEqualTo(6);
        }

        @Test
        void runsTheFirstPeriodFromTheStartOfTheMonth() {
            assertThat(SCHEDULE.byNumber(1).range().format()).isEqualTo("2025-01-01 to 2025-01-24");
        }

        @Test
        void startsEachLaterPeriodTheDayAfterTheLastCutOff() {
            assertThat(SCHEDULE.byNumber(2).range().format()).isEqualTo("2025-01-25 to 2025-02-25");
            assertThat(SCHEDULE.byNumber(3).range().format()).isEqualTo("2025-02-26 to 2025-03-25");
        }

        @Test
        void leavesNoGapAndNoOverlapBetweenPeriods() {
            for (int i = 1; i < SCHEDULE.periods().size(); i++) {
                ValuationPeriod before = SCHEDULE.periods().get(i - 1);
                ValuationPeriod after = SCHEDULE.periods().get(i);
                assertThat(SiteDates.diffDays(before.range().to(), after.range().from()))
                        .isEqualTo(1);
            }
        }

        @Test
        void issuesAWeekOfWorkingDaysAfterTheCutOff() {
            assertThat(SiteDates.format(SCHEDULE.byNumber(3).issuedOn())).isEqualTo("2025-04-03");
        }

        @Test
        void fallsDueAFortnightAfterIssue() {
            assertThat(SiteDates.format(SCHEDULE.byNumber(3).dueOn())).isEqualTo("2025-04-17");
        }

        @Test
        void honoursADifferentIssueAndPaymentLag() {
            PeriodSchedule quick = PeriodSchedule.build(
                    ScheduleInput.of(2025, 3, 1, 25, WEEKDAYS).withIssueDays(2).withPaymentDays(30));
            ValuationPeriod only = quick.byNumber(1);
            assertThat(SiteDates.format(only.issuedOn())).isEqualTo("2025-03-27");
            assertThat(SiteDates.format(only.dueOn())).isEqualTo("2025-04-26");
        }

        @Test
        void refusesAScheduleWithNoPeriods() {
            assertThatThrownBy(() -> PeriodSchedule.build(ScheduleInput.of(2025, 1, 0, 25, WEEKDAYS)))
                    .hasMessageContaining("at least one period");
        }

        @Test
        void refusesACutOffDayThatIsNotADayOfTheMonth() {
            // A fractional day cannot be passed: the cut-off day is an int.
            for (int cutOffDay : new int[] {0, 32}) {
                assertThatThrownBy(() -> PeriodSchedule.build(ScheduleInput.of(2025, 1, 1, cutOffDay, WEEKDAYS)))
                        .hasMessageContaining("day of the month");
            }
        }

        @Test
        void rollsIntoTheNextYear() {
            PeriodSchedule spanning = PeriodSchedule.build(ScheduleInput.of(2024, 11, 4, 25, WEEKDAYS));
            assertThat(SiteDates.format(spanning.byNumber(4).cutOff())).isEqualTo("2025-02-25");
        }
    }

    @Nested
    class FindingAPeriod {

        @Test
        void findsByNumber() {
            assertThat(SCHEDULE.byNumber(4).number()).isEqualTo(4);
            assertThat(SCHEDULE.findByNumber(99)).isEmpty();
            assertThatThrownBy(() -> SCHEDULE.byNumber(99)).isInstanceOf(NotFoundException.class);
        }

        @Test
        void findsThePeriodADateFallsInside() {
            assertThat(SCHEDULE.periodFor(d("2025-03-10")).orElseThrow().number())
                    .isEqualTo(3);
            assertThat(SCHEDULE.periodFor(d("2025-03-25")).orElseThrow().number())
                    .isEqualTo(3);
            assertThat(SCHEDULE.periodFor(d("2025-03-26")).orElseThrow().number())
                    .isEqualTo(4);
            assertThat(SCHEDULE.periodFor(d("2024-12-31"))).isEmpty();
        }

        @Test
        void findsTheLastPeriodWhoseCutOffHasPassed() {
            assertThat(SCHEDULE.asAt(d("2025-03-24")).orElseThrow().number()).isEqualTo(2);
            assertThat(SCHEDULE.asAt(d("2025-03-25")).orElseThrow().number()).isEqualTo(3);
            assertThat(SCHEDULE.asAt(d("2024-12-01"))).isEmpty();
            assertThat(SCHEDULE.asAt(d("2026-01-01")).orElseThrow().number()).isEqualTo(6);
        }

        @Test
        void walksBackAPeriod() {
            assertThat(SCHEDULE.previous(SCHEDULE.byNumber(3)).orElseThrow().number())
                    .isEqualTo(2);
            assertThat(SCHEDULE.previous(SCHEDULE.byNumber(1))).isEmpty();
        }
    }

    @Nested
    class DescribePeriod {

        @Test
        void readsLikeACertificateHeading() {
            assertThat(SCHEDULE.byNumber(3).describe())
                    .isEqualTo("valuation 3 to 2025-03-25, issued 2025-04-03, due 2025-04-17");
        }
    }
}
