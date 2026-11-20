package io.github.rkaushikdev.chainageworks.domain.earthworks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HaulRunTest {

    private static CrossSection section(String station, double cut, double fill) {
        return CrossSection.fromMetres(Stations.parse(station), cut, fill);
    }

    /** A hundred metres of cut and a hundred of matching fill, so it closes out. */
    private static final List<CrossSection> BALANCED = List.of(
            section("12+000", 40, 0), section("12+100", 40, 0), section("12+200", 0, 40), section("12+300", 0, 40));

    /** The same run with a deeper embankment, so it finishes short of fill. */
    private static final List<CrossSection> DEFICIT = List.of(
            section("12+000", 40, 0), section("12+100", 40, 0), section("12+200", 0, 60), section("12+300", 0, 60));

    private static List<String> points(HaulRun run) {
        return run.balancePoints().stream()
                .map(s -> Stations.format(s, 0, false))
                .toList();
    }

    @Nested
    class Build {

        @Test
        void startsAtNothingAndAddsEachSpan() {
            HaulRun run = HaulRun.build(BALANCED);
            assertThat(run.ordinates()).hasSize(4);
            assertThat(run.ordinates().get(0).ordinate()).isZero();
            assertThat(run.ordinates().stream().map(o -> o.ordinate() / 1000.0))
                    .containsExactly(0.0, 4000.0, 4000.0, 0.0);
        }

        @Test
        void isEmptyForNoSections() {
            HaulRun run = HaulRun.build(List.of());
            assertThat(run.ordinates()).isEmpty();
            assertThat(run.closingOrdinate().milli()).isZero();
        }

        @Test
        void appliesTheBulkingFactorToTheCut() {
            assertThat(HaulRun.build(BALANCED, 1.2).ordinates().get(1).ordinate())
                    .isEqualTo(4_800_000);
        }

        @Test
        void refusesABulkingFactorThatIsNotPositive() {
            assertThatThrownBy(() -> HaulRun.build(BALANCED, 0)).hasMessageContaining("positive number");
            assertThatThrownBy(() -> HaulRun.build(BALANCED, Double.NaN)).hasMessageContaining("positive number");
        }
    }

    @Nested
    class TheClosingOrdinate {

        @Test
        void isADeficitWhenTheFillOutrunsTheCut() {
            HaulRun run = HaulRun.build(DEFICIT);
            assertThat(run.closingOrdinate().milli()).isEqualTo(-3_000_000);
            assertThat(run.isSurplus()).isFalse();
        }

        @Test
        void isASurplusWhenTheCutOutrunsTheFill() {
            HaulRun run = HaulRun.build(List.of(section("12+000", 40, 0), section("12+100", 40, 0)));
            assertThat(run.isSurplus()).isTrue();
            assertThat(run.closingOrdinate().milli()).isEqualTo(4_000_000);
        }

        @Test
        void isNothingWhenTheTwoBalanceExactly() {
            assertThat(HaulRun.build(BALANCED).closingOrdinate().milli()).isZero();
        }

        @Test
        void turnsADeficitIntoASurplusWithEnoughBulking() {
            assertThat(HaulRun.build(DEFICIT, 4).isSurplus()).isTrue();
        }
    }

    @Nested
    class BalancePoints {

        @Test
        void findsTheChainageTheCurveCrossesBackThrough() {
            assertThat(points(HaulRun.build(DEFICIT))).containsExactly("12+250");
        }

        @Test
        void findsNothingOnACurveThatNeverCrosses() {
            assertThat(HaulRun.build(List.of(section("12+000", 40, 0), section("12+100", 40, 0)))
                            .balancePoints())
                    .isEmpty();
        }

        @Test
        void findsAStretchThatClosesOutExactly() {
            assertThat(points(HaulRun.build(BALANCED))).containsExactly("12+300");
        }

        @Test
        void doesNotReportTheStartWhichIsOnlyWhereTheCountBegan() {
            HaulRun run =
                    HaulRun.build(List.of(section("12+000", 0, 0), section("12+100", 40, 0), section("12+200", 40, 0)));
            assertThat(run.balancePoints()).isEmpty();
        }
    }

    @Nested
    class HaulMoment {

        @Test
        void isNothingOnARunWithNoMaterialToMove() {
            HaulRun flat = HaulRun.build(List.of(section("12+000", 0, 0), section("12+100", 0, 0)));
            assertThat(flat.haulMoment()).isZero();
        }

        @Test
        void growsWithTheDistanceTheMaterialHasToTravel() {
            HaulRun near = HaulRun.build(
                    List.of(section("12+000", 40, 0), section("12+100", 40, 0), section("12+200", 0, 40)));
            HaulRun far = HaulRun.build(
                    List.of(section("12+000", 40, 0), section("12+100", 40, 0), section("12+600", 0, 40)));
            assertThat(far.haulMoment()).isGreaterThan(near.haulMoment());
        }

        @Test
        void growsWithTheVolumeBeingMoved() {
            HaulRun light = HaulRun.build(List.of(section("12+000", 10, 0), section("12+200", 10, 0)));
            HaulRun heavy = HaulRun.build(List.of(section("12+000", 40, 0), section("12+200", 40, 0)));
            assertThat(heavy.haulMoment()).isGreaterThan(light.haulMoment());
        }
    }

    @Nested
    class Overhaul {

        private final HaulRun run =
                HaulRun.build(List.of(section("12+000", 40, 0), section("12+100", 40, 0), section("13+000", 0, 40)));

        @Test
        void givesEverythingAwayInsideTheFreeHaul() {
            HaulRun.Overhaul split = run.overhaul(10_000);
            assertThat(split.overhaulMoment()).isZero();
            assertThat(split.freeMoment()).isEqualTo(run.haulMoment());
        }

        @Test
        void paysForEverythingWhenNothingIsFree() {
            HaulRun.Overhaul split = run.overhaul(0);
            assertThat(split.freeMoment()).isZero();
            assertThat(split.overhaulMoment()).isEqualTo(run.haulMoment());
        }

        @Test
        void splitsTheMomentAtTheFreeHaulDistance() {
            HaulRun.Overhaul split = run.overhaul();
            assertThat(split.freeHaulMetres()).isEqualTo(HaulRun.DEFAULT_FREE_HAUL_METRES);
            assertThat(split.freeMoment()).isPositive();
            assertThat(split.overhaulMoment()).isPositive();
            assertThat(split.freeMoment() + split.overhaulMoment()).isCloseTo(run.haulMoment(), byLessThan(0.05));
        }

        @Test
        void refusesANegativeFreeHaulDistance() {
            assertThatThrownBy(() -> run.overhaul(-1)).hasMessageContaining("not negative");
        }
    }

    @Nested
    class Describe {

        @Test
        void saysWhichWayTheRunIsOutAndWhereItBalances() {
            assertThat(HaulRun.build(DEFICIT).describe()).isEqualTo("deficit of 3000 m3, 1 balance points at 12+250");
        }

        @Test
        void saysSoWhenThereIsNothingToMove() {
            HaulRun flat = HaulRun.build(List.of(section("12+000", 0, 0), section("12+100", 0, 0)));
            assertThat(flat.describe()).contains("balanced");
        }
    }
}
