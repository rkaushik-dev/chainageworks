package io.github.rkaushikdev.chainageworks.domain.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SiteCalendarTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static DateRange r(String from, String to) {
        return DateRange.of(d(from), d(to));
    }

    private static String f(LocalDate date) {
        return SiteDates.format(date);
    }

    private static final SiteCalendar WEEKDAYS = SiteCalendar.build(CalendarInput.of("C1", "main works"));

    private static final SiteCalendar WITH_HOLIDAYS = SiteCalendar.build(
            CalendarInput.of("C2", "main works with holidays").withHolidays(List.of(d("2025-04-18"), d("2025-04-21"))));

    private static final SiteCalendar SIX_DAY =
            SiteCalendar.build(CalendarInput.of("C3", "six day working").withWorkingDays(List.of(1, 2, 3, 4, 5, 6)));

    private static final SiteCalendar WITH_STANDDOWNS = SiteCalendar.build(CalendarInput.of("C4", "wet march")
            .withStanddowns(List.of(
                    new Standdown(d("2025-03-05"), StanddownReason.WEATHER, "rain, no concrete"),
                    new Standdown(d("2025-03-06"), StanddownReason.GROUND_CONDITIONS),
                    new Standdown(d("2025-03-16"), StanddownReason.WEATHER))));

    @Nested
    class Build {

        @Test
        void defaultsToMondayThroughFriday() {
            assertThat(WEEKDAYS.workingDays()).isEqualTo(SiteCalendar.DEFAULT_WORKING_DAYS);
        }

        @Test
        void sortsAndDedupesTheWorkingDays() {
            SiteCalendar cal = SiteCalendar.build(CalendarInput.of("x", "x").withWorkingDays(List.of(5, 1, 1, 3)));
            assertThat(cal.workingDays()).containsExactly(1, 3, 5);
        }

        @Test
        void refusesAWeekdayOutsideOneToSeven() {
            // Weekdays are whole numbers by type, so half a weekday cannot be asked for.
            assertThatThrownBy(
                            () -> SiteCalendar.build(CalendarInput.of("x", "x").withWorkingDays(List.of(0))))
                    .hasMessageContaining("iso weekday");
            assertThatThrownBy(
                            () -> SiteCalendar.build(CalendarInput.of("x", "x").withWorkingDays(List.of(8))))
                    .hasMessageContaining("iso weekday");
        }

        @Test
        void refusesACalendarThatNeverWorks() {
            assertThatThrownBy(
                            () -> SiteCalendar.build(CalendarInput.of("x", "x").withWorkingDays(List.of())))
                    .hasMessageContaining("at least one day a week");
        }

        @Test
        void sortsAndDedupesHolidays() {
            SiteCalendar cal = SiteCalendar.build(CalendarInput.of("x", "x")
                    .withHolidays(List.of(d("2025-04-21"), d("2025-04-18"), d("2025-04-21"))));
            assertThat(cal.holidays().stream().map(SiteDates::format)).containsExactly("2025-04-18", "2025-04-21");
        }

        @Test
        void keepsOneStanddownPerDayTheLastOneEntered() {
            SiteCalendar cal = SiteCalendar.build(CalendarInput.of("x", "x")
                    .withStanddowns(List.of(
                            new Standdown(d("2025-03-05"), StanddownReason.WEATHER),
                            new Standdown(d("2025-03-05"), StanddownReason.ACCESS))));
            assertThat(cal.standdowns()).hasSize(1);
            assertThat(cal.standdowns().get(0).reason()).isEqualTo(StanddownReason.ACCESS);
        }
    }

    @Nested
    class IsWorkingDay {

        @Test
        void worksWeekdaysAndNotWeekends() {
            assertThat(WEEKDAYS.isWorkingDay(d("2025-03-14"))).isTrue();
            assertThat(WEEKDAYS.isWorkingDay(d("2025-03-15"))).isFalse();
            assertThat(WEEKDAYS.isWorkingDay(d("2025-03-16"))).isFalse();
        }

        @Test
        void worksSaturdayOnASixDayCalendar() {
            assertThat(SIX_DAY.isWorkingDay(d("2025-03-15"))).isTrue();
            assertThat(SIX_DAY.isWorkingDay(d("2025-03-16"))).isFalse();
        }

        @Test
        void doesNotWorkAHolidayThatFallsOnAWeekday() {
            assertThat(WITH_HOLIDAYS.isHoliday(d("2025-04-18"))).isTrue();
            assertThat(WITH_HOLIDAYS.isWorkingDay(d("2025-04-18"))).isFalse();
            assertThat(WITH_HOLIDAYS.isWorkingDay(d("2025-04-17"))).isTrue();
        }
    }

    @Nested
    class Standdowns {

        @Test
        void findsTheStanddownOnADay() {
            assertThat(WITH_STANDDOWNS.standdownOn(d("2025-03-05")).map(Standdown::reason))
                    .contains(StanddownReason.WEATHER);
            assertThat(WITH_STANDDOWNS.standdownOn(d("2025-03-07"))).isEmpty();
        }

        @Test
        void makesAWorkingDayUnproductiveWithoutMakingItANonWorkingDay() {
            assertThat(WITH_STANDDOWNS.isWorkingDay(d("2025-03-05"))).isTrue();
            assertThat(WITH_STANDDOWNS.isProductiveDay(d("2025-03-05"))).isFalse();
            assertThat(WITH_STANDDOWNS.isProductiveDay(d("2025-03-07"))).isTrue();
        }

        @Test
        void ignoresAStanddownBookedOnADayNobodyWasWorking() {
            assertThat(WITH_STANDDOWNS.standdownsIn(DateRange.month(2025, 3)).stream()
                            .map(s -> f(s.date())))
                    .containsExactly("2025-03-05", "2025-03-06");
            assertThat(WITH_STANDDOWNS.standdownDaysIn(DateRange.month(2025, 3)))
                    .isEqualTo(2);
        }

        @Test
        void countsNothingOutsideTheRangeAskedAbout() {
            assertThat(WITH_STANDDOWNS.standdownDaysIn(DateRange.month(2025, 4)))
                    .isZero();
        }
    }

    @Nested
    class CountingDaysInARange {

        @Test
        void countsWorkingDaysInAMonth() {
            assertThat(WEEKDAYS.workingDaysIn(DateRange.month(2025, 3))).isEqualTo(21);
            assertThat(SIX_DAY.workingDaysIn(DateRange.month(2025, 3))).isEqualTo(26);
        }

        @Test
        void takesHolidaysOffTheCount() {
            assertThat(WEEKDAYS.workingDaysIn(DateRange.month(2025, 4))).isEqualTo(22);
            assertThat(WITH_HOLIDAYS.workingDaysIn(DateRange.month(2025, 4))).isEqualTo(20);
        }

        @Test
        void takesStanddownsOffTheProductiveCountButNotTheWorkingCount() {
            assertThat(WITH_STANDDOWNS.workingDaysIn(DateRange.month(2025, 3))).isEqualTo(21);
            assertThat(WITH_STANDDOWNS.productiveDaysIn(DateRange.month(2025, 3)))
                    .isEqualTo(19);
        }

        @Test
        void countsASingleDayRange() {
            assertThat(WEEKDAYS.workingDaysIn(r("2025-03-14", "2025-03-14"))).isEqualTo(1);
            assertThat(WEEKDAYS.workingDaysIn(r("2025-03-15", "2025-03-15"))).isZero();
        }
    }

    @Nested
    class SteppingWorkingDays {

        @Test
        void findsTheNextAndPreviousWorkingDay() {
            assertThat(f(WEEKDAYS.nextWorkingDay(d("2025-03-14")))).isEqualTo("2025-03-17");
            assertThat(f(WEEKDAYS.previousWorkingDay(d("2025-03-17")))).isEqualTo("2025-03-14");
        }

        @Test
        void stepsOverAHoliday() {
            assertThat(f(WITH_HOLIDAYS.nextWorkingDay(d("2025-04-17")))).isEqualTo("2025-04-22");
        }

        @Test
        void stepsOverAWeekendOnASixDayCalendarByOneDayOnly() {
            assertThat(f(SIX_DAY.nextWorkingDay(d("2025-03-14")))).isEqualTo("2025-03-15");
        }

        @Test
        void doesNotStepOverAStanddownBecauseTheDayIsStillAWorkingDay() {
            assertThat(f(WITH_STANDDOWNS.nextWorkingDay(d("2025-03-04")))).isEqualTo("2025-03-05");
        }

        @Test
        void addsAndSubtractsWholeWorkingDays() {
            assertThat(f(WEEKDAYS.addWorkingDays(d("2025-03-14"), 1))).isEqualTo("2025-03-17");
            assertThat(f(WEEKDAYS.addWorkingDays(d("2025-03-14"), 5))).isEqualTo("2025-03-21");
            assertThat(f(WEEKDAYS.addWorkingDays(d("2025-03-17"), -1))).isEqualTo("2025-03-14");
        }

        @Test
        void returnsTheDayItselfForAStepOfNothing() {
            assertThat(WEEKDAYS.addWorkingDays(d("2025-03-15"), 0)).isEqualTo(d("2025-03-15"));
        }

        @Test
        void refusesAFractionalStep() throws NoSuchMethodException {
            // A step is an int, so the compiler refuses half a working day before it reaches the method.
            assertThat(SiteCalendar.class.getMethod("addWorkingDays", LocalDate.class, int.class))
                    .isNotNull();
        }

        @Test
        void givesUpRatherThanLoopingForeverOnAnImpossibleCalendar() {
            SiteCalendar never = SiteCalendar.build(CalendarInput.of("never", "shut")
                    .withWorkingDays(List.of(1))
                    .withHolidays(IntStream.range(0, 60)
                            .mapToObj(i -> d("2025-01-06").plusDays(i * 7L))
                            .toList()));
            assertThatThrownBy(() -> never.nextWorkingDay(d("2025-01-01"))).hasMessageContaining("within a year");
            assertThatThrownBy(() -> never.previousWorkingDay(d("2026-03-01"))).hasMessageContaining("within a year");
        }
    }

    @Nested
    class WorkingDaysBetween {

        @Test
        void countsNeitherEnd() {
            assertThat(WEEKDAYS.workingDaysBetween(d("2025-03-14"), d("2025-03-17")))
                    .isZero();
            assertThat(WEEKDAYS.workingDaysBetween(d("2025-03-14"), d("2025-03-21")))
                    .isEqualTo(4);
        }

        @Test
        void isZeroForTheSameDay() {
            assertThat(WEEKDAYS.workingDaysBetween(d("2025-03-14"), d("2025-03-14")))
                    .isZero();
        }

        @Test
        void isNegativeTheOtherWayRound() {
            assertThat(WEEKDAYS.workingDaysBetween(d("2025-03-21"), d("2025-03-14")))
                    .isEqualTo(-4);
        }
    }
}
