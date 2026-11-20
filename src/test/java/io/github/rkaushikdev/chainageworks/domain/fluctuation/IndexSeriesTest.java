package io.github.rkaushikdev.chainageworks.domain.fluctuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IndexSeriesTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static IndexReading reading(int year, int month, double value) {
        return new IndexReading(year, month, value, false);
    }

    private static IndexReading provisional(int year, int month, double value) {
        return new IndexReading(year, month, value, true);
    }

    private static final IndexSeries LABOUR = IndexSeries.build(
            "IDX-LAB",
            "civil engineering labour",
            List.of(
                    reading(2024, 10, 100),
                    reading(2024, 11, 101.4),
                    reading(2024, 12, 102),
                    reading(2025, 1, 103.6),
                    provisional(2025, 2, 104.2),
                    provisional(2025, 3, 105)));

    @Nested
    class Build {

        @Test
        void sortsTheReadingsIntoMonthOrder() {
            IndexSeries jumbled = IndexSeries.build(
                    "IDX-X", null, List.of(reading(2025, 2, 104), reading(2024, 12, 102), reading(2025, 1, 103)));
            assertThat(jumbled.readings().stream().map(IndexReading::value)).containsExactly(102.0, 103.0, 104.0);
        }

        @Test
        void takesTheIdAsTheNameWhenNoneIsGiven() {
            assertThat(IndexSeries.build("IDX-X", null, List.of()).name()).isEqualTo("IDX-X");
        }

        @Test
        void refusesTwoFiguresForOneMonth() {
            assertThatThrownBy(() ->
                            IndexSeries.build("IDX-X", null, List.of(reading(2025, 1, 103), reading(2025, 1, 104))))
                    .hasMessageContaining("two figures for one month");
        }

        @Test
        void refusesAMonthThatIsNotAMonth() {
            assertThatThrownBy(() -> IndexSeries.build("IDX-X", null, List.of(reading(2025, 13, 103))))
                    .hasMessageContaining("month of the year");
        }

        @Test
        void refusesAnIndexFigureOfNothing() {
            assertThatThrownBy(() -> IndexSeries.build("IDX-X", null, List.of(reading(2025, 1, 0))))
                    .hasMessageContaining("more than nothing");
        }
    }

    @Nested
    class ReadingAt {

        @Test
        void takesTheFigureForTheMonthItself() {
            assertThat(LABOUR.readingAt(d("2025-01-25")).orElseThrow().value()).isEqualTo(103.6);
        }

        @Test
        void carriesTheLastPublishedFigureForward() {
            assertThat(LABOUR.readingAt(d("2025-08-25")).orElseThrow().value()).isEqualTo(105);
        }

        @Test
        void isEmptyBeforeTheSeriesBegins() {
            assertThat(LABOUR.readingAt(d("2024-08-25"))).isEmpty();
        }

        @Test
        void readsTheSameFigureFromAnyDayInsideTheMonth() {
            assertThat(LABOUR.readingAt(d("2024-12-01")).orElseThrow().value()).isEqualTo(102);
            assertThat(LABOUR.readingAt(d("2024-12-31")).orElseThrow().value()).isEqualTo(102);
        }
    }

    @Nested
    class ValueAt {

        @Test
        void givesTheFigure() {
            assertThat(LABOUR.valueAt(d("2024-12-25"))).isEqualTo(102);
        }

        @Test
        void refusesADateTheSeriesDoesNotReachBackTo() {
            assertThatThrownBy(() -> LABOUR.valueAt(d("2024-01-01"))).hasMessageContaining("index reading");
        }
    }

    @Nested
    class IsProvisionalAt {

        @Test
        void saysSoWhileTheFigureIsStillProvisional() {
            assertThat(LABOUR.isProvisionalAt(d("2025-03-25"))).isTrue();
        }

        @Test
        void saysNoOnceTheFigureIsFirm() {
            assertThat(LABOUR.isProvisionalAt(d("2025-01-25"))).isFalse();
        }

        @Test
        void saysNoRatherThanThrowingBeforeTheSeriesBegins() {
            assertThat(LABOUR.isProvisionalAt(d("2024-01-01"))).isFalse();
        }
    }

    @Nested
    class ProvisionalMonths {

        @Test
        void listsTheMonthsStillToBeFirmedUp() {
            assertThat(LABOUR.provisionalMonths().stream().map(IndexReading::month))
                    .containsExactly(2, 3);
        }

        @Test
        void listsNothingOnAFirmSeries() {
            assertThat(IndexSeries.build("IDX-F", null, List.of(reading(2025, 1, 100)))
                            .provisionalMonths())
                    .isEmpty();
        }
    }

    @Nested
    class LatestReading {

        @Test
        void isTheLastMonthPublished() {
            assertThat(LABOUR.latestReading().orElseThrow().month()).isEqualTo(3);
        }

        @Test
        void isEmptyOnASeriesWithNothingPublished() {
            assertThat(IndexSeries.build("IDX-E", null, List.of()).latestReading())
                    .isEmpty();
        }
    }

    @Nested
    class MovementSince {

        @Test
        void isTheFractionTheIndexHasMoved() {
            assertThat(LABOUR.movementSince(d("2024-10-25"), d("2024-12-25"))).isCloseTo(0.02, byLessThan(5e-11));
        }

        @Test
        void isNothingAtTheBaseDateItself() {
            assertThat(LABOUR.movementSince(d("2024-10-25"), d("2024-10-25"))).isZero();
        }

        @Test
        void goesNegativeWhenTheIndexFallsBack() {
            IndexSeries falling =
                    IndexSeries.build("IDX-FALL", null, List.of(reading(2025, 1, 110), reading(2025, 2, 99)));
            assertThat(falling.movementSince(d("2025-01-25"), d("2025-02-25"))).isCloseTo(-0.1, byLessThan(5e-11));
        }

        @Test
        void refusesToReadTheIndexBeforeTheBaseDate() {
            assertThatThrownBy(() -> LABOUR.movementSince(d("2025-01-25"), d("2024-12-25")))
                    .hasMessageContaining("before the base date");
        }
    }

    @Nested
    class FindingASeries {

        private final List<IndexSeries> all = List.of(LABOUR);

        @Test
        void givesNothingForOneNobodyLoaded() {
            assertThat(IndexSeries.find(all, "IDX-NONE")).isEmpty();
        }

        @Test
        void throwsWhenTheCallerNeedsIt() {
            assertThatThrownBy(() -> IndexSeries.require(all, "IDX-NONE")).hasMessageContaining("index series");
            assertThat(IndexSeries.require(all, "IDX-LAB").name()).isEqualTo("civil engineering labour");
        }
    }

    @Nested
    class Describe {

        @Test
        void namesTheLatestFigureAndWhetherItIsFirm() {
            assertThat(LABOUR.describe())
                    .isEqualTo("IDX-LAB civil engineering labour, latest 105 for 2025-03, provisional");
        }

        @Test
        void saysSoWhenNothingHasBeenPublished() {
            assertThat(IndexSeries.build("IDX-E", null, List.of()).describe()).contains("no figures published");
        }
    }
}
