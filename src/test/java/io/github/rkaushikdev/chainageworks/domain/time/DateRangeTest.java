package io.github.rkaushikdev.chainageworks.domain.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DateRangeTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static DateRange r(String from, String to) {
        return DateRange.of(d(from), d(to));
    }

    private static List<String> show(List<DateRange> ranges) {
        return ranges.stream().map(DateRange::format).toList();
    }

    @Nested
    class Of {

        @Test
        void holdsBothEnds() {
            assertThat(r("2025-03-01", "2025-03-31").format()).isEqualTo("2025-03-01 to 2025-03-31");
        }

        @Test
        void allowsASingleDay() {
            assertThat(DateRange.singleDay(d("2025-03-14")).days()).isEqualTo(1);
        }

        @Test
        void refusesARangeThatEndsBeforeItStarts() {
            assertThatThrownBy(() -> r("2025-03-31", "2025-03-01")).isInstanceOf(BadInputException.class);
            assertThatThrownBy(() -> r("2025-03-31", "2025-03-01")).hasMessageContaining("ends on or after it starts");
        }
    }

    @Nested
    class Days {

        @Test
        void countsBothEndsSoMarchIsThirtyOneDays() {
            assertThat(r("2025-03-01", "2025-03-31").days()).isEqualTo(31);
        }

        @Test
        void countsALeapFebruaryAsTwentyNine() {
            assertThat(DateRange.month(2024, 2).days()).isEqualTo(29);
            assertThat(DateRange.month(2025, 2).days()).isEqualTo(28);
        }
    }

    @Nested
    class Month {

        @Test
        void buildsAWholeCalendarMonth() {
            assertThat(DateRange.month(2025, 3).format()).isEqualTo("2025-03-01 to 2025-03-31");
            assertThat(DateRange.month(2025, 4).format()).isEqualTo("2025-04-01 to 2025-04-30");
        }

        @Test
        void buildsTheMonthADateFallsIn() {
            assertThat(DateRange.monthOf(d("2025-03-14")).format()).isEqualTo("2025-03-01 to 2025-03-31");
        }

        @Test
        void namesTheMonthForAReportHeading() {
            assertThat(DateRange.month(2025, 3).describeMonth()).isEqualTo("2025-03");
            assertThat(DateRange.month(2025, 11).describeMonth()).isEqualTo("2025-11");
        }
    }

    @Nested
    class Format {

        @Test
        void writesASingleDayOnce() {
            assertThat(DateRange.singleDay(d("2025-03-14")).format()).isEqualTo("2025-03-14");
        }
    }

    @Nested
    class Parse {

        @Test
        void readsTheWordFormAndTheDashForms() {
            assertThat(DateRange.parse("2025-03-01 to 2025-03-31").format()).isEqualTo("2025-03-01 to 2025-03-31");
            assertThat(DateRange.parse("2025-03-01 .. 2025-03-31").format()).isEqualTo("2025-03-01 to 2025-03-31");
            assertThat(DateRange.parse("2025-03-01 -- 2025-03-31").format()).isEqualTo("2025-03-01 to 2025-03-31");
        }

        @Test
        void readsABareDateAsOneDay() {
            assertThat(DateRange.parse("2025-03-14").days()).isEqualTo(1);
        }

        @Test
        void throwsOnEitherEndBeingUnreadable() {
            assertThatThrownBy(() -> DateRange.parse("nope to 2025-03-31")).isInstanceOf(BadInputException.class);
            assertThatThrownBy(() -> DateRange.parse("2025-03-01 to nope")).isInstanceOf(BadInputException.class);
            assertThatThrownBy(() -> DateRange.parse("nonsense")).isInstanceOf(BadInputException.class);
        }

        @Test
        void throwsWhenTheRangeRunsBackwards() {
            assertThatThrownBy(() -> DateRange.parse("2025-03-31 to 2025-03-01"))
                    .hasMessageContaining("ends on or after");
        }
    }

    @Nested
    class Contains {

        @Test
        void includesBothEnds() {
            DateRange march = DateRange.month(2025, 3);
            assertThat(march.contains(d("2025-03-01"))).isTrue();
            assertThat(march.contains(d("2025-03-31"))).isTrue();
            assertThat(march.contains(d("2025-02-28"))).isFalse();
            assertThat(march.contains(d("2025-04-01"))).isFalse();
        }
    }

    @Nested
    class OverlapAndCover {

        @Test
        void countsASharedSingleDayAsAnOverlap() {
            assertThat(r("2025-03-01", "2025-03-15").overlaps(r("2025-03-15", "2025-03-31")))
                    .isTrue();
        }

        @Test
        void isFalseForConsecutiveRangesThatDoNotShareADay() {
            assertThat(DateRange.month(2025, 3).overlaps(DateRange.month(2025, 4)))
                    .isFalse();
        }

        @Test
        void comparesEqualityOnBothEnds() {
            assertThat(DateRange.month(2025, 3)).isEqualTo(r("2025-03-01", "2025-03-31"));
            assertThat(DateRange.month(2025, 3)).isNotEqualTo(DateRange.month(2025, 4));
        }

        @Test
        void coversAnInnerRangeIncludingTheSharedEnds() {
            assertThat(DateRange.month(2025, 3).covers(r("2025-03-10", "2025-03-20")))
                    .isTrue();
            assertThat(DateRange.month(2025, 3).covers(r("2025-03-20", "2025-04-02")))
                    .isFalse();
        }
    }

    @Nested
    class Intersect {

        @Test
        void returnsTheSharedDays() {
            assertThat(r("2025-03-01", "2025-03-20")
                            .intersect(DateRange.month(2025, 3))
                            .orElseThrow()
                            .format())
                    .isEqualTo("2025-03-01 to 2025-03-20");
        }

        @Test
        void returnsASingleSharedDay() {
            DateRange shared = r("2025-03-01", "2025-03-15")
                    .intersect(r("2025-03-15", "2025-03-31"))
                    .orElseThrow();
            assertThat(SiteDates.format(shared.from())).isEqualTo("2025-03-15");
            assertThat(shared.from()).isEqualTo(shared.to());
        }

        @Test
        void returnsNothingWhenNothingIsShared() {
            assertThat(DateRange.month(2025, 3).intersect(DateRange.month(2025, 5)))
                    .isEmpty();
        }
    }

    @Nested
    class Union {

        @Test
        void mergesOverlappingRanges() {
            List<DateRange> merged =
                    DateRange.union(List.of(r("2025-03-10", "2025-03-20"), r("2025-03-01", "2025-03-15")));
            assertThat(show(merged)).containsExactly("2025-03-01 to 2025-03-20");
        }

        @Test
        void mergesRangesThatAreSimplyConsecutiveDays() {
            List<DateRange> merged = DateRange.union(List.of(DateRange.month(2025, 3), DateRange.month(2025, 4)));
            assertThat(show(merged)).containsExactly("2025-03-01 to 2025-04-30");
        }

        @Test
        void keepsRangesWithARealGapBetweenThem() {
            assertThat(DateRange.union(List.of(DateRange.month(2025, 5), DateRange.month(2025, 3))))
                    .hasSize(2);
        }

        @Test
        void handlesAnEmptyListAndLeavesTheInputAlone() {
            List<DateRange> input = new ArrayList<>(List.of(DateRange.month(2025, 5), DateRange.month(2025, 3)));
            assertThat(DateRange.union(List.of())).isEmpty();
            DateRange.union(input);
            assertThat(input.get(0).describeMonth()).isEqualTo("2025-05");
        }
    }

    @Nested
    class Dates {

        @Test
        void listsEveryDayInclusive() {
            assertThat(r("2025-03-01", "2025-03-03").dates().stream().map(SiteDates::format))
                    .containsExactly("2025-03-01", "2025-03-02", "2025-03-03");
        }

        @Test
        void listsOneDayForASingleDayRange() {
            assertThat(DateRange.singleDay(d("2025-03-14")).dates()).hasSize(1);
        }

        @Test
        void listsAWholeMonth() {
            assertThat(DateRange.month(2025, 3).dates()).hasSize(31);
        }
    }

    @Nested
    class SplitByMonth {

        @Test
        void leavesARangeInsideOneMonthAlone() {
            assertThat(show(r("2025-03-05", "2025-03-20").splitByMonth())).containsExactly("2025-03-05 to 2025-03-20");
        }

        @Test
        void cutsAtEveryMonthBoundary() {
            assertThat(show(r("2025-02-20", "2025-04-05").splitByMonth()))
                    .containsExactly(
                            "2025-02-20 to 2025-02-28", "2025-03-01 to 2025-03-31", "2025-04-01 to 2025-04-05");
        }

        @Test
        void cutsAcrossAYearBoundary() {
            assertThat(show(r("2024-12-28", "2025-01-03").splitByMonth()))
                    .containsExactly("2024-12-28 to 2024-12-31", "2025-01-01 to 2025-01-03");
        }
    }

    @Nested
    class Compare {

        @Test
        void ordersByStartThenByEnd() {
            assertThat(DateRange.month(2025, 3).compareTo(DateRange.month(2025, 4)))
                    .isNegative();
            assertThat(r("2025-03-01", "2025-03-31").compareTo(r("2025-03-01", "2025-03-15")))
                    .isPositive();
            assertThat(DateRange.month(2025, 3).compareTo(DateRange.month(2025, 3)))
                    .isZero();
        }
    }
}
