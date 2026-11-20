package io.github.rkaushikdev.chainageworks.domain.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Result;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SiteDatesTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static String f(LocalDate date) {
        return SiteDates.format(date);
    }

    @Nested
    class Of {

        @Test
        void countsDaysFromTheEpoch() {
            assertThat(SiteDates.epochDay(SiteDates.of(1970, 1, 1))).isZero();
            assertThat(SiteDates.epochDay(SiteDates.of(1970, 1, 2))).isEqualTo(1);
            assertThat(SiteDates.epochDay(SiteDates.of(1969, 12, 31))).isEqualTo(-1);
        }

        @Test
        void splitsBackIntoParts() {
            assertThat(SiteDates.parts(SiteDates.of(2025, 3, 14))).isEqualTo(new SiteDates.DateParts(2025, 3, 14));
        }

        @Test
        void acceptsTheTwentyNinthOfFebruaryInALeapYear() {
            assertThat(SiteDates.parts(SiteDates.of(2024, 2, 29)).day()).isEqualTo(29);
        }

        @Test
        void refusesTheTwentyNinthOfFebruaryInACommonYear() {
            assertThatThrownBy(() -> SiteDates.of(2025, 2, 29)).hasMessageContaining("not in that month");
        }

        @Test
        void knowsTheCenturyRuleForLeapYears() {
            assertThatThrownBy(() -> SiteDates.of(1900, 2, 29)).hasMessageContaining("not in that month");
            assertThat(SiteDates.parts(SiteDates.of(2000, 2, 29)).day()).isEqualTo(29);
        }

        @Test
        void refusesAMonthOutsideOneToTwelve() {
            assertThatThrownBy(() -> SiteDates.of(2025, 0, 1)).hasMessageContaining("month must be 1 to 12");
            assertThatThrownBy(() -> SiteDates.of(2025, 13, 1)).hasMessageContaining("month must be 1 to 12");
        }

        @Test
        void refusesADayOutsideTheMonth() {
            assertThatThrownBy(() -> SiteDates.of(2025, 4, 31)).hasMessageContaining("not in that month");
            assertThatThrownBy(() -> SiteDates.of(2025, 4, 0)).hasMessageContaining("not in that month");
        }

        @Test
        void refusesAYearNoContractCouldRunIn() {
            assertThatThrownBy(() -> SiteDates.of(1899, 1, 1)).hasMessageContaining("outside the range");
            assertThatThrownBy(() -> SiteDates.of(2201, 1, 1)).hasMessageContaining("outside the range");
        }
    }

    @Nested
    class Parse {

        @Test
        void readsTheIsoForm() {
            assertThat(SiteDates.parts(d("2025-03-14"))).isEqualTo(new SiteDates.DateParts(2025, 3, 14));
        }

        @Test
        void ignoresSurroundingWhitespace() {
            assertThat(d("  2025-03-14 ")).isEqualTo(d("2025-03-14"));
        }

        @Test
        void refusesAnythingElse() {
            for (String text : List.of("14/03/2025", "2025-3-14", "2025-03", "yesterday", "")) {
                assertThat(SiteDates.tryParse(text).isOk()).isFalse();
            }
            assertThatThrownBy(() -> SiteDates.parse("14/03/2025")).isInstanceOf(BadInputException.class);
        }

        @Test
        void refusesAWellShapedDateThatDoesNotExist() {
            Result<LocalDate, CwException> r = SiteDates.tryParse("2025-02-30");
            assertThat(r.isOk()).isFalse();
            assertThat(r.error().getMessage()).contains("not in that month");
        }
    }

    @Nested
    class Format {

        @Test
        void padsMonthAndDay() {
            assertThat(f(SiteDates.of(2025, 3, 4))).isEqualTo("2025-03-04");
        }

        @Test
        void roundTripsThroughTheParser() {
            for (String text : List.of("1970-01-01", "2024-02-29", "2025-12-31", "2199-06-15")) {
                assertThat(f(d(text))).isEqualTo(text);
            }
        }
    }

    @Nested
    class DayArithmetic {

        @Test
        void stepsForwardsAndBackwards() {
            assertThat(f(SiteDates.addDays(d("2025-03-14"), 1))).isEqualTo("2025-03-15");
            assertThat(f(SiteDates.addDays(d("2025-03-01"), -1))).isEqualTo("2025-02-28");
        }

        @Test
        void crossesALeapDay() {
            assertThat(f(SiteDates.addDays(d("2024-02-28"), 1))).isEqualTo("2024-02-29");
            assertThat(f(SiteDates.addDays(d("2024-02-28"), 2))).isEqualTo("2024-03-01");
        }

        @Test
        void refusesAFractionalStep() throws NoSuchMethodException {
            // A step is an int, so the compiler refuses half a day before it reaches the method.
            assertThat(SiteDates.class.getMethod("addDays", LocalDate.class, int.class))
                    .isNotNull();
        }

        @Test
        void measuresASignedDifference() {
            assertThat(SiteDates.diffDays(d("2025-03-01"), d("2025-03-14"))).isEqualTo(13);
            assertThat(SiteDates.diffDays(d("2025-03-14"), d("2025-03-01"))).isEqualTo(-13);
            assertThat(SiteDates.diffDays(d("2025-03-14"), d("2025-03-14"))).isZero();
        }

        @Test
        void comparesAndPicksExtremes() {
            LocalDate early = d("2025-03-01");
            LocalDate late = d("2025-03-14");
            assertThat(SiteDates.compare(early, late)).isNegative();
            assertThat(early).isEqualTo(early);
            assertThat(early).isNotEqualTo(late);
            assertThat(SiteDates.min(early, late)).isSameAs(early);
            assertThat(SiteDates.max(early, late)).isSameAs(late);
        }
    }

    @Nested
    class DayOfWeek {

        @Test
        void numbersMondayOneThroughSundaySeven() {
            assertThat(SiteDates.dayOfWeek(d("2025-03-10"))).isEqualTo(1);
            assertThat(SiteDates.dayOfWeek(d("2025-03-14"))).isEqualTo(5);
            assertThat(SiteDates.dayOfWeek(d("2025-03-15"))).isEqualTo(6);
            assertThat(SiteDates.dayOfWeek(d("2025-03-16"))).isEqualTo(7);
        }

        @Test
        void callsSaturdayAndSundayTheWeekend() {
            assertThat(SiteDates.isWeekend(d("2025-03-14"))).isFalse();
            assertThat(SiteDates.isWeekend(d("2025-03-15"))).isTrue();
            assertThat(SiteDates.isWeekend(d("2025-03-16"))).isTrue();
            assertThat(SiteDates.isWeekend(d("2025-03-17"))).isFalse();
        }
    }

    @Nested
    class MonthBoundaries {

        @Test
        void findsTheFirstAndLastDay() {
            assertThat(f(SiteDates.startOfMonth(d("2025-03-14")))).isEqualTo("2025-03-01");
            assertThat(f(SiteDates.endOfMonth(d("2025-03-14")))).isEqualTo("2025-03-31");
            assertThat(f(SiteDates.endOfMonth(d("2025-02-14")))).isEqualTo("2025-02-28");
            assertThat(f(SiteDates.endOfMonth(d("2024-02-14")))).isEqualTo("2024-02-29");
        }

        @Test
        void reportsTheLastDayOfTheMonth() {
            assertThat(SiteDates.isLastDayOfMonth(d("2025-03-31"))).isTrue();
            assertThat(SiteDates.isLastDayOfMonth(d("2025-03-30"))).isFalse();
            assertThat(SiteDates.isLastDayOfMonth(d("2024-02-29"))).isTrue();
        }
    }

    @Nested
    class AddMonths {

        @Test
        void stepsWholeMonths() {
            assertThat(f(SiteDates.addMonths(d("2025-03-14"), 1))).isEqualTo("2025-04-14");
            assertThat(f(SiteDates.addMonths(d("2025-03-14"), -3))).isEqualTo("2024-12-14");
        }

        @Test
        void clampsOntoTheLastDayWhenTheTargetMonthIsShort() {
            assertThat(f(SiteDates.addMonths(d("2025-01-31"), 1))).isEqualTo("2025-02-28");
            assertThat(f(SiteDates.addMonths(d("2024-01-31"), 1))).isEqualTo("2024-02-29");
            assertThat(f(SiteDates.addMonths(d("2025-03-31"), -1))).isEqualTo("2025-02-28");
        }

        @Test
        void crossesAYearBoundaryInBothDirections() {
            assertThat(f(SiteDates.addMonths(d("2025-11-15"), 3))).isEqualTo("2026-02-15");
            assertThat(f(SiteDates.addMonths(d("2025-02-15"), -3))).isEqualTo("2024-11-15");
        }

        @Test
        void refusesAFractionalStep() throws NoSuchMethodException {
            // A step is an int, so the compiler refuses half a month before it reaches the method.
            assertThat(SiteDates.class.getMethod("addMonths", LocalDate.class, int.class))
                    .isNotNull();
        }
    }
}
