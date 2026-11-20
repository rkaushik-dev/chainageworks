package io.github.rkaushikdev.chainageworks.domain.chainage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Result;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChainageRangeTest {

    private static long at(String text) {
        return Stations.parse(text);
    }

    private static ChainageRange r(String from, String to) {
        return ChainageRange.of(at(from), at(to));
    }

    private static ChainageRange mm(long from, long to) {
        return new ChainageRange(from, to);
    }

    @Nested
    class Of {

        @Test
        void holdsTheTwoStationsItWasGiven() {
            ChainageRange range = r("12+000", "12+340");
            assertThat(range.from()).isEqualTo(12_000_000);
            assertThat(range.to()).isEqualTo(12_340_000);
        }

        @Test
        void allowsAPoint() {
            ChainageRange range = r("12+340", "12+340");
            assertThat(range.isPoint()).isTrue();
            assertThat(range.length()).isZero();
        }

        @Test
        void refusesARangeThatRunsBackwards() {
            assertThatThrownBy(() -> r("12+340", "12+000")).isInstanceOf(BadInputException.class);
            assertThatThrownBy(() -> r("12+340", "12+000")).hasMessageContaining("runs forwards");
        }
    }

    @Nested
    class Parse {

        @Test
        void readsTheWordForm() {
            assertThat(ChainageRange.parse("12+000 to 12+340")).isEqualTo(mm(12_000_000, 12_340_000));
        }

        @Test
        void readsTheDashForms() {
            assertThat(ChainageRange.parse("12+000 -- 12+340").to()).isEqualTo(12_340_000);
            assertThat(ChainageRange.parse("12+000 – 12+340").to()).isEqualTo(12_340_000);
            assertThat(ChainageRange.parse("12+000 → 12+340").to()).isEqualTo(12_340_000);
        }

        @Test
        void treatsABareStationAsAPoint() {
            ChainageRange range = ChainageRange.parse("12+340");
            assertThat(range.from()).isEqualTo(range.to());
            assertThat(range.from()).isEqualTo(12_340_000);
        }

        @Test
        void reportsABadStationOnEitherSide() {
            assertThat(ChainageRange.tryParse("nope to 12+340").isOk()).isFalse();
            assertThat(ChainageRange.tryParse("12+000 to nope").isOk()).isFalse();
        }

        @Test
        void reportsARangeThatRunsBackwardsWithoutThrowing() {
            Result<ChainageRange, CwException> back = ChainageRange.tryParse("12+340 to 12+000");
            assertThat(back.isOk()).isFalse();
            assertThat(back.error().getMessage()).contains("runs forwards");
        }
    }

    @Nested
    class Format {

        @Test
        void writesBothEndsOfAStretch() {
            assertThat(r("12+000", "12+340").format()).isEqualTo("12+000.000 to 12+340.000");
        }

        @Test
        void writesAPointAsASingleStation() {
            assertThat(r("12+340", "12+340").format()).isEqualTo("12+340.000");
        }
    }

    @Nested
    class ContainsAndTouches {

        private final ChainageRange range = r("12+000", "12+340");

        @Test
        void includesTheLowerStationAndExcludesTheUpper() {
            assertThat(range.contains(at("12+000"))).isTrue();
            assertThat(range.contains(at("12+200"))).isTrue();
            assertThat(range.contains(at("12+340"))).isFalse();
            assertThat(range.contains(at("11+999"))).isFalse();
        }

        @Test
        void touchesBothEnds() {
            assertThat(range.touches(at("12+000"))).isTrue();
            assertThat(range.touches(at("12+340"))).isTrue();
            assertThat(range.touches(at("12+341"))).isFalse();
        }
    }

    @Nested
    class Overlaps {

        @Test
        void isFalseForStretchesThatOnlyAbut() {
            assertThat(r("12+000", "12+100").overlaps(r("12+100", "12+200"))).isFalse();
        }

        @Test
        void isTrueWhenTheyShareLength() {
            assertThat(r("12+000", "12+150").overlaps(r("12+100", "12+200"))).isTrue();
        }

        @Test
        void isFalseForAPointOnABoundary() {
            assertThat(r("12+000", "12+100").overlaps(r("12+100", "12+100"))).isFalse();
        }

        @Test
        void isFalseForDisjointStretches() {
            assertThat(r("12+000", "12+100").overlaps(r("13+000", "13+100"))).isFalse();
        }
    }

    @Nested
    class EqualityAndCovers {

        @Test
        void comparesBothEnds() {
            assertThat(r("12+000", "12+100")).isEqualTo(r("12+000", "12+100"));
            assertThat(r("12+000", "12+100")).isNotEqualTo(r("12+000", "12+101"));
        }

        @Test
        void coversAnInnerStretchIncludingTheSharedEnds() {
            assertThat(r("12+000", "12+200").covers(r("12+050", "12+150"))).isTrue();
            assertThat(r("12+000", "12+200").covers(r("12+000", "12+200"))).isTrue();
            assertThat(r("12+000", "12+200").covers(r("12+150", "12+250"))).isFalse();
        }
    }

    @Nested
    class Intersect {

        @Test
        void returnsTheSharedStretch() {
            assertThat(r("12+000", "12+150").intersect(r("12+100", "12+200"))).contains(mm(12_100_000, 12_150_000));
        }

        @Test
        void returnsNothingWhenTheyOnlyAbut() {
            assertThat(r("12+000", "12+100").intersect(r("12+100", "12+200"))).isEmpty();
        }

        @Test
        void returnsNothingWhenTheyAreApart() {
            assertThat(r("12+000", "12+100").intersect(r("14+000", "14+100"))).isEmpty();
        }
    }

    @Nested
    class Subtract {

        @Test
        void leavesTheWholeStretchWhenNothingOverlaps() {
            assertThat(r("12+000", "12+100").subtract(r("13+000", "13+100")))
                    .containsExactly(mm(12_000_000, 12_100_000));
        }

        @Test
        void trimsTheFront() {
            assertThat(r("12+000", "12+100").subtract(r("11+900", "12+040")))
                    .containsExactly(mm(12_040_000, 12_100_000));
        }

        @Test
        void trimsTheBack() {
            assertThat(r("12+000", "12+100").subtract(r("12+060", "12+200")))
                    .containsExactly(mm(12_000_000, 12_060_000));
        }

        @Test
        void punchesAHoleAndGivesTwoPieces() {
            assertThat(r("12+000", "12+100").subtract(r("12+040", "12+060")))
                    .containsExactly(mm(12_000_000, 12_040_000), mm(12_060_000, 12_100_000));
        }

        @Test
        void givesNothingBackWhenTheCutSwallowsTheStretch() {
            assertThat(r("12+000", "12+100").subtract(r("11+000", "13+000"))).isEmpty();
        }

        @Test
        void dropsAPointThatSitsInsideTheCut() {
            assertThat(r("12+050", "12+050").subtract(r("12+000", "12+100"))).isEmpty();
        }
    }

    @Nested
    class Union {

        @Test
        void sortsAndMergesOverlappingStretches() {
            assertThat(ChainageRange.union(List.of(r("12+100", "12+200"), r("12+000", "12+150"))))
                    .containsExactly(mm(12_000_000, 12_200_000));
        }

        @Test
        void mergesStretchesThatOnlyAbut() {
            assertThat(ChainageRange.union(List.of(r("12+000", "12+100"), r("12+100", "12+200"))))
                    .containsExactly(mm(12_000_000, 12_200_000));
        }

        @Test
        void keepsStretchesThatAreGenuinelyApart() {
            assertThat(ChainageRange.union(List.of(r("13+000", "13+100"), r("12+000", "12+100"))))
                    .containsExactly(mm(12_000_000, 12_100_000), mm(13_000_000, 13_100_000));
        }

        @Test
        void absorbsAPointThatLandsInsideAStretch() {
            assertThat(ChainageRange.union(List.of(r("12+000", "12+100"), r("12+050", "12+050"))))
                    .containsExactly(mm(12_000_000, 12_100_000));
        }

        @Test
        void keepsALonePoint() {
            assertThat(ChainageRange.union(List.of(r("12+050", "12+050")))).containsExactly(mm(12_050_000, 12_050_000));
        }

        @Test
        void handlesAnEmptyList() {
            assertThat(ChainageRange.union(List.of())).isEmpty();
        }

        @Test
        void doesNotMutateTheInput() {
            List<ChainageRange> input = new ArrayList<>(List.of(r("13+000", "13+100"), r("12+000", "12+100")));
            ChainageRange.union(input);
            assertThat(input.get(0).from()).isEqualTo(13_000_000);
        }
    }

    @Nested
    class CoveredLength {

        @Test
        void countsOverlapOnce() {
            assertThat(ChainageRange.coveredLength(List.of(r("12+000", "12+150"), r("12+100", "12+200"))))
                    .isEqualTo(200_000);
        }

        @Test
        void addsDisjointStretches() {
            assertThat(ChainageRange.coveredLength(List.of(r("12+000", "12+100"), r("13+000", "13+050"))))
                    .isEqualTo(150_000);
        }

        @Test
        void isZeroForNothingAtAll() {
            assertThat(ChainageRange.coveredLength(List.of())).isZero();
        }
    }

    @Nested
    class SplitAt {

        @Test
        void cutsInTwo() {
            assertThat(r("12+000", "12+100").splitAt(at("12+040")))
                    .containsExactly(mm(12_000_000, 12_040_000), mm(12_040_000, 12_100_000));
        }

        @Test
        void leavesTheStretchWholeWhenTheCutIsAtTheStart() {
            assertThat(r("12+000", "12+100").splitAt(at("12+000"))).hasSize(1);
        }

        @Test
        void leavesTheStretchWholeWhenTheCutIsOutside() {
            assertThat(r("12+000", "12+100").splitAt(at("12+100"))).hasSize(1);
            assertThat(r("12+000", "12+100").splitAt(at("11+000"))).hasSize(1);
        }
    }

    @Nested
    class Compare {

        @Test
        void ordersByStartThenByEnd() {
            assertThat(r("12+000", "12+100").compareTo(r("12+050", "12+060"))).isNegative();
            assertThat(r("12+000", "12+100").compareTo(r("12+000", "12+060"))).isPositive();
            assertThat(r("12+000", "12+100").compareTo(r("12+000", "12+100"))).isZero();
        }
    }
}
