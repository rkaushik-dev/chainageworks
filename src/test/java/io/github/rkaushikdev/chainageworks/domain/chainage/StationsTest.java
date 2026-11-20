package io.github.rkaushikdev.chainageworks.domain.chainage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Result;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StationsTest {

    @Nested
    class Of {

        @Test
        void acceptsWholeMillimetres() {
            assertThat(Stations.of(12_340_500.0)).isEqualTo(12_340_500);
            assertThat(Stations.of(0.0)).isZero();
            assertThat(Stations.of(-50_000.0)).isEqualTo(-50_000);
        }

        @Test
        void refusesAFractionalMillimetre() {
            assertThatThrownBy(() -> Stations.of(12.5)).isInstanceOf(BadInputException.class);
            assertThatThrownBy(() -> Stations.of(12.5)).hasMessageContaining("whole number of millimetres");
        }

        @Test
        void refusesAnythingThatIsNotFinite() {
            assertThatThrownBy(() -> Stations.of(Double.NaN)).hasMessageContaining("finite");
            assertThatThrownBy(() -> Stations.of(Double.POSITIVE_INFINITY)).hasMessageContaining("finite");
        }
    }

    @Nested
    class FromMetres {

        @Test
        void scalesMetresToMillimetres() {
            assertThat(Stations.fromMetres(12_340.5)).isEqualTo(12_340_500);
        }

        @Test
        void roundsHalfAwayFromZeroOnBothSides() {
            assertThat(Stations.fromMetres(0.0005)).isEqualTo(1);
            assertThat(Stations.fromMetres(-0.0005)).isEqualTo(-1);
            assertThat(Stations.fromMetres(0.00049)).isZero();
        }

        @Test
        void refusesANonFiniteInput() {
            assertThatThrownBy(() -> Stations.fromMetres(Double.NaN)).hasMessageContaining("finite");
        }
    }

    @Nested
    class ToMetres {

        @Test
        void isTheInverseForValuesThatFit() {
            assertThat(Stations.toMetres(12_340_500)).isEqualTo(12_340.5);
            assertThat(Stations.toMetres(0)).isZero();
        }
    }

    @Nested
    class Parse {

        @Test
        void readsTheFullForm() {
            assertThat(Stations.parse("12+340.500")).isEqualTo(12_340_500);
        }

        @Test
        void readsAShortFractionAndPadsIt() {
            assertThat(Stations.parse("12+340.5")).isEqualTo(12_340_500);
            assertThat(Stations.parse("12+340.05")).isEqualTo(12_340_050);
        }

        @Test
        void readsAWholeMetreWithNoFraction() {
            assertThat(Stations.parse("12+340")).isEqualTo(12_340_000);
        }

        @Test
        void readsTheOrigin() {
            assertThat(Stations.parse("0+000")).isZero();
        }

        @Test
        void acceptsTheDrawingPrefixInEitherCaseAndWithADot() {
            assertThat(Stations.parse("CH 12+340")).isEqualTo(12_340_000);
            assertThat(Stations.parse("ch. 12+340")).isEqualTo(12_340_000);
            assertThat(Stations.parse("CH12+340")).isEqualTo(12_340_000);
        }

        @Test
        void acceptsChainageBeforeTheOrigin() {
            assertThat(Stations.parse("-0+050")).isEqualTo(-50_000);
            assertThat(Stations.parse("-1+000")).isEqualTo(-1_000_000);
        }

        @Test
        void ignoresSurroundingWhitespace() {
            assertThat(Stations.parse("  12+340  ")).isEqualTo(12_340_000);
        }

        @Test
        void throwsOnABlankString() {
            assertThatThrownBy(() -> Stations.parse("   ")).hasMessageContaining("blank");
        }

        @Test
        void throwsWhenTheShapeIsWrong() {
            for (String text : List.of("12340", "12+", "+340", "twelve+340")) {
                assertThatThrownBy(() -> Stations.parse(text)).hasMessageContaining("km+metres");
            }
        }

        @Test
        void rejectsAMetresPartOf1000OrMoreWhichIsNearlyAlwaysATypo() {
            Result<Long, CwException> r = Stations.tryParse("12+1340");
            assertThat(r.isOk()).isFalse();
            assertThat(r.error().getMessage()).contains("under 1000");
        }

        @Test
        void rejectsMoreThanThreeDecimalPlaces() {
            assertThatThrownBy(() -> Stations.parse("12+340.5005")).hasMessageContaining("km+metres");
        }
    }

    @Nested
    class Format {

        @Test
        void writesTheCanonicalThreeDecimalForm() {
            assertThat(Stations.format(12_340_500)).isEqualTo("12+340.500");
        }

        @Test
        void padsTheMetresPartToThreeDigits() {
            assertThat(Stations.format(12_005_000)).isEqualTo("12+005.000");
            assertThat(Stations.format(500)).isEqualTo("0+000.500");
        }

        @Test
        void honoursAShorterDecimalRequest() {
            assertThat(Stations.format(12_340_500, 0, false)).isEqualTo("12+340");
            assertThat(Stations.format(12_340_500, 1, false)).isEqualTo("12+340.5");
            assertThat(Stations.format(12_340_050, 2, false)).isEqualTo("12+340.05");
        }

        @Test
        void addsTheDrawingPrefixOnRequest() {
            assertThat(Stations.format(0, 3, true)).isEqualTo("CH 0+000.000");
        }

        @Test
        void keepsTheSignOnNegativeChainage() {
            assertThat(Stations.format(-50_000)).isEqualTo("-0+050.000");
        }

        @Test
        void roundTripsThroughTheParser() {
            for (long mm : List.of(0L, 500L, 12_340_500L, 999_999L, 1_000_000L, -50_000L)) {
                assertThat(Stations.parse(Stations.format(mm))).isEqualTo(mm);
            }
        }
    }

    @Nested
    class StationArithmetic {

        @Test
        void comparesByPosition() {
            assertThat(Stations.compare(10, 20)).isNegative();
            assertThat(Stations.compare(20, 10)).isPositive();
            assertThat(Stations.compare(10, 10)).isZero();
        }

        @Test
        void addsAndSubtractsMillimetres() {
            assertThat(Stations.plus(1000, 500)).isEqualTo(1500);
            assertThat(Stations.minus(1000, 500)).isEqualTo(500);
            assertThat(Stations.minus(0, 500)).isEqualTo(-500);
        }

        @Test
        void refusesAFractionalStep() throws NoSuchMethodException {
            // A step is a long, so the compiler refuses half a millimetre before it reaches the method.
            assertThat(Stations.class.getMethod("plus", long.class, long.class)).isNotNull();
        }

        @Test
        void measuresAnUnsignedSpanInEitherDirection() {
            assertThat(Stations.span(1000, 1500)).isEqualTo(500);
            assertThat(Stations.span(1500, 1000)).isEqualTo(500);
        }

        @Test
        void picksTheLowerAndTheHigherOfAPair() {
            assertThat(Stations.min(10, 20)).isEqualTo(10);
            assertThat(Stations.max(10, 20)).isEqualTo(20);
            assertThat(Stations.min(10, 10)).isEqualTo(10);
        }
    }

    @Nested
    class Clamp {

        @Test
        void pullsAPointInsideTheBounds() {
            assertThat(Stations.clamp(50, 100, 200)).isEqualTo(100);
            assertThat(Stations.clamp(150, 100, 200)).isEqualTo(150);
            assertThat(Stations.clamp(250, 100, 200)).isEqualTo(200);
        }

        @Test
        void refusesBoundsThatAreTheWrongWayRound() {
            assertThatThrownBy(() -> Stations.clamp(150, 200, 100)).hasMessageContaining("wrong way round");
        }
    }
}
