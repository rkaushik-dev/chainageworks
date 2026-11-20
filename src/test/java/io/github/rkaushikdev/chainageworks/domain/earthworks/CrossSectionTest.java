package io.github.rkaushikdev.chainageworks.domain.earthworks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.earthworks.CrossSection.Volumes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CrossSectionTest {

    private static long at(String text) {
        return Stations.parse(text);
    }

    private static CrossSection section(String station, double cut, double fill) {
        return CrossSection.fromMetres(at(station), cut, fill);
    }

    private static ChainageRange r(String from, String to) {
        return ChainageRange.of(at(from), at(to));
    }

    @Nested
    class Of {

        @Test
        void holdsAreasAsWholeThousandths() {
            assertThat(section("12+000", 40.5, 0).cutArea()).isEqualTo(40_500);
            assertThat(CrossSection.of(at("12+000"), 40_500, 0).cutArea()).isEqualTo(40_500);
        }

        @Test
        void roundsASurveyAreaToTheThousandth() {
            assertThat(section("12+000", 40.5004, 0).cutArea()).isEqualTo(40_500);
        }

        @Test
        void refusesANegativeOrFractionalArea() {
            assertThatThrownBy(() -> CrossSection.of(at("12+000"), -1, 0)).hasMessageContaining("not negative");
            assertThatThrownBy(() -> CrossSection.of(at("12+000"), 1.5, 0)).hasMessageContaining("whole number");
        }
    }

    @Nested
    class Sort {

        @Test
        void putsThemInChainageOrder() {
            List<CrossSection> sorted = CrossSection.sort(List.of(section("12+040", 1, 0), section("12+000", 2, 0)));
            assertThat(sorted.stream().map(s -> Stations.format(s.station())))
                    .containsExactly("12+000.000", "12+040.000");
        }

        @Test
        void refusesTwoSectionsAtTheSameChainage() {
            assertThatThrownBy(() -> CrossSection.sort(List.of(section("12+000", 1, 0), section("12+000", 2, 0))))
                    .hasMessageContaining("same chainage");
        }
    }

    @Nested
    class EndAreaVolume {

        @Test
        void averagesTheTwoEndAreasOverTheLength() {
            Volumes volume = CrossSection.endAreaVolume(section("12+000", 40, 0), section("12+020", 60, 0));
            assertThat(volume.cut().format()).isEqualTo("1000.00 m3");
            assertThat(volume.fill().milli()).isZero();
        }

        @Test
        void measuresCutAndFillSeparately() {
            Volumes volume = CrossSection.endAreaVolume(section("12+000", 40, 10), section("12+020", 0, 30));
            assertThat(volume.cut().format()).isEqualTo("400.00 m3");
            assertThat(volume.fill().format()).isEqualTo("400.00 m3");
        }

        @Test
        void isNothingBetweenTwoSectionsWithNoArea() {
            assertThat(CrossSection.endAreaVolume(section("12+000", 0, 0), section("12+020", 0, 0))
                            .cut()
                            .milli())
                    .isZero();
        }

        @Test
        void isNothingOverNoDistanceAtAll() {
            assertThat(CrossSection.endAreaVolume(section("12+000", 40, 0), section("12+000", 60, 0))
                            .cut()
                            .milli())
                    .isZero();
        }

        @Test
        void doesNotCareWhichWayRoundTheSectionsCome() {
            Volumes forward = CrossSection.endAreaVolume(section("12+000", 40, 0), section("12+020", 60, 0));
            Volumes back = CrossSection.endAreaVolume(section("12+020", 60, 0), section("12+000", 40, 0));
            assertThat(back.cut()).isEqualTo(forward.cut());
        }
    }

    @Nested
    class PrismoidalVolume {

        private final CrossSection a = section("12+000", 40, 0);
        private final CrossSection b = section("12+020", 60, 0);

        @Test
        void matchesEndAreaWhenTheSectionChangesEvenly() {
            CrossSection middle = section("12+010", 50, 0);
            assertThat(CrossSection.prismoidalVolume(a, middle, b).cut())
                    .isEqualTo(CrossSection.endAreaVolume(a, b).cut());
        }

        @Test
        void readsLessThanEndAreaWhenTheMiddlePinches() {
            CrossSection pinched = section("12+010", 30, 0);
            assertThat(CrossSection.prismoidalVolume(a, pinched, b).cut().milli())
                    .isLessThan(CrossSection.endAreaVolume(a, b).cut().milli());
        }

        @Test
        void readsMoreThanEndAreaWhenTheMiddleSwells() {
            CrossSection swollen = section("12+010", 70, 0);
            assertThat(CrossSection.prismoidalVolume(a, swollen, b).cut().milli())
                    .isGreaterThan(CrossSection.endAreaVolume(a, b).cut().milli());
        }
    }

    @Nested
    class TotalVolumes {

        private final List<CrossSection> sections = List.of(
                section("12+000", 0, 0), section("12+020", 40, 0), section("12+040", 60, 0), section("12+060", 0, 20));

        @Test
        void addsEverySpanBetweenConsecutiveSections() {
            Volumes volume = CrossSection.totalVolumes(sections);
            assertThat(volume.cut().format()).isEqualTo("2000.00 m3");
            assertThat(volume.fill().format()).isEqualTo("200.00 m3");
        }

        @Test
        void isNothingForASingleSectionOrNoneAtAll() {
            assertThat(CrossSection.totalVolumes(List.of(section("12+000", 40, 0)))
                            .cut()
                            .milli())
                    .isZero();
            assertThat(CrossSection.totalVolumes(List.of()).cut().milli()).isZero();
        }

        @Test
        void doesNotCareWhatOrderTheyArriveIn() {
            List<CrossSection> reversed = new ArrayList<>(sections);
            Collections.reverse(reversed);
            assertThat(CrossSection.totalVolumes(reversed)).isEqualTo(CrossSection.totalVolumes(sections));
        }
    }

    @Nested
    class Interpolate {

        @Test
        void takesTheAreasStraightBetweenTwoSections() {
            CrossSection middle =
                    CrossSection.interpolate(section("12+000", 40, 0), section("12+020", 60, 0), at("12+010"));
            assertThat(middle.cutArea()).isEqualTo(50_000);
            assertThat(Stations.format(middle.station())).isEqualTo("12+010.000");
        }

        @Test
        void landsOnTheEndAreasAtTheEnds() {
            CrossSection a = section("12+000", 40, 5);
            CrossSection b = section("12+020", 60, 15);
            assertThat(CrossSection.interpolate(a, b, at("12+000")).cutArea()).isEqualTo(a.cutArea());
            assertThat(CrossSection.interpolate(a, b, at("12+020")).fillArea()).isEqualTo(b.fillArea());
        }
    }

    @Nested
    class VolumesOver {

        private final List<CrossSection> sections = List.of(
                section("12+000", 0, 0), section("12+020", 40, 0), section("12+040", 40, 0), section("12+060", 0, 0));

        @Test
        void measuresAStretchThatLandsOnTheSurveyedSections() {
            assertThat(CrossSection.volumesOver(sections, r("12+020", "12+040"))
                            .cut()
                            .format())
                    .isEqualTo("800.00 m3");
        }

        @Test
        void interpolatesASectionAtABoundaryThatFallsBetweenTwo() {
            assertThat(CrossSection.volumesOver(sections, r("12+010", "12+050"))
                            .cut()
                            .format())
                    .isEqualTo("1400.00 m3");
        }

        @Test
        void measuresTheLotWhenTheExtentCoversEverything() {
            assertThat(CrossSection.volumesOver(sections, r("11+000", "13+000")))
                    .isEqualTo(CrossSection.totalVolumes(sections));
        }

        @Test
        void measuresNothingOverAStretchOutsideTheSurvey() {
            assertThat(CrossSection.volumesOver(sections, r("13+000", "13+100"))
                            .cut()
                            .milli())
                    .isZero();
        }

        @Test
        void addsBackToTheWholeWhenAStretchIsCutInTwo() {
            Volumes whole = CrossSection.volumesOver(sections, r("12+000", "12+060"));
            Volumes first = CrossSection.volumesOver(sections, r("12+000", "12+030"));
            Volumes second = CrossSection.volumesOver(sections, r("12+030", "12+060"));
            assertThat(first.cut().milli() + second.cut().milli())
                    .isEqualTo(whole.cut().milli());
        }
    }

    @Nested
    class BalanceAfterBulking {

        private final Volumes volumes =
                CrossSection.totalVolumes(List.of(section("12+000", 40, 10), section("12+020", 40, 10)));

        @Test
        void isTheCutLessTheFillWhenNothingBulks() {
            assertThat(volumes.cut().format()).isEqualTo("800.00 m3");
            assertThat(volumes.fill().format()).isEqualTo("200.00 m3");
            assertThat(CrossSection.balanceAfterBulking(volumes, 1).format()).isEqualTo("600.00 m3");
        }

        @Test
        void goesFurtherWhenTheMaterialBulks() {
            assertThat(CrossSection.balanceAfterBulking(volumes, 1.2).milli())
                    .isGreaterThan(CrossSection.balanceAfterBulking(volumes, 1).milli());
        }

        @Test
        void runsShortWhenTheMaterialShrinksOnCompaction() {
            assertThat(CrossSection.balanceAfterBulking(volumes, 0.2).milli()).isNegative();
            assertThat(CrossSection.isBorrowNeeded(volumes, 0.2)).isTrue();
            assertThat(CrossSection.isBorrowNeeded(volumes, 1)).isFalse();
        }

        @Test
        void refusesAFactorThatIsNotPositive() {
            assertThatThrownBy(() -> CrossSection.balanceAfterBulking(volumes, 0))
                    .hasMessageContaining("positive number");
            assertThatThrownBy(() -> CrossSection.balanceAfterBulking(volumes, Double.NaN))
                    .hasMessageContaining("positive number");
        }
    }
}
