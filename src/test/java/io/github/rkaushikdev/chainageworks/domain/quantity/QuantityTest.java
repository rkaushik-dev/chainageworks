package io.github.rkaushikdev.chainageworks.domain.quantity;

import static io.github.rkaushikdev.chainageworks.domain.quantity.Unit.KG;
import static io.github.rkaushikdev.chainageworks.domain.quantity.Unit.M;
import static io.github.rkaushikdev.chainageworks.domain.quantity.Unit.M2;
import static io.github.rkaushikdev.chainageworks.domain.quantity.Unit.M3;
import static io.github.rkaushikdev.chainageworks.domain.quantity.Unit.NR;
import static io.github.rkaushikdev.chainageworks.domain.quantity.Unit.SUM;
import static io.github.rkaushikdev.chainageworks.domain.quantity.Unit.T;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Result;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QuantityTest {

    private static Quantity q(double amount, Unit unit) {
        return Quantity.of(amount, unit);
    }

    @Nested
    class Making {

        @Test
        void scalesAnAmountIntoThousandths() {
            assertThat(q(12.5, M3).milli()).isEqualTo(12_500);
            assertThat(q(0, NR).milli()).isZero();
        }

        @Test
        void roundsHalfAwayFromZeroOnBothSides() {
            assertThat(q(0.0005, M3).milli()).isEqualTo(1);
            assertThat(q(-0.0005, M3).milli()).isEqualTo(-1);
        }

        @Test
        void refusesANonFiniteAmount() {
            assertThatThrownBy(() -> q(Double.NaN, M)).hasMessageContaining("finite");
            assertThatThrownBy(() -> q(Double.POSITIVE_INFINITY, M)).hasMessageContaining("finite");
        }

        @Test
        void refusesAFractionalThousandthWhenBuiltFromMilli() {
            assertThatThrownBy(() -> Quantity.ofMilli(1.5, M)).hasMessageContaining("whole number of thousandths");
            assertThatThrownBy(() -> Quantity.ofMilli(Double.NaN, M)).hasMessageContaining("finite");
        }

        @Test
        void readsTheAmountBack() {
            assertThat(q(12.345, M3).amount()).isEqualTo(12.345);
        }

        @Test
        void makesAZeroThatKnowsItsUnit() {
            assertThat(Quantity.zero(M2)).isEqualTo(new Quantity(0, M2));
            assertThat(Quantity.zero(M2).isZero()).isTrue();
            assertThat(q(0.001, M2).isZero()).isFalse();
        }

        @Test
        void reportsANegativeAmount() {
            assertThat(q(-1, M).isNegative()).isTrue();
            assertThat(q(0, M).isNegative()).isFalse();
        }
    }

    @Nested
    class Arithmetic {

        @Test
        void addsAndSubtractsInTheSameUnit() {
            assertThat(q(2.5, M3).plus(q(1.25, M3))).isEqualTo(new Quantity(3_750, M3));
            assertThat(q(2.5, M3).minus(q(1.25, M3))).isEqualTo(new Quantity(1_250, M3));
        }

        @Test
        void refusesToMixUnits() {
            assertThatThrownBy(() -> q(1, T).plus(q(1, KG))).hasMessageContaining("different units");
            assertThatThrownBy(() -> q(1, M).minus(q(1, M2))).hasMessageContaining("different units");
            assertThatThrownBy(() -> q(1, M).compareTo(q(1, M2))).hasMessageContaining("different units");
        }

        @Test
        void goesNegativeOnSubtractionBecauseAnOverclaimHasToBeVisible() {
            assertThat(q(1, M3).minus(q(4, M3)).milli()).isEqualTo(-3_000);
        }

        @Test
        void negates() {
            assertThat(q(2, M).negate()).isEqualTo(new Quantity(-2_000, M));
        }

        @Test
        void scalesByAFractionAndRounds() {
            assertThat(q(10, M3).scale(0.25)).isEqualTo(new Quantity(2_500, M3));
            assertThat(q(1, M3).scale(1.0 / 3).milli()).isEqualTo(333);
            assertThat(q(-1, M3).scale(1.0 / 3).milli()).isEqualTo(-333);
        }

        @Test
        void refusesAScaleFactorThatIsNotFinite() {
            assertThatThrownBy(() -> q(1, M).scale(Double.NaN)).hasMessageContaining("finite");
        }

        @Test
        void comparesAndPicksExtremes() {
            Quantity small = q(1, M);
            Quantity large = q(2, M);
            assertThat(small.compareTo(large)).isNegative();
            assertThat(Quantity.min(small, large)).isSameAs(small);
            assertThat(Quantity.max(small, large)).isSameAs(large);
            assertThat(Quantity.min(small, small)).isSameAs(small);
            assertThat(Quantity.max(small, small)).isSameAs(small);
        }

        @Test
        void testsEqualityOnBothTheAmountAndTheUnit() {
            assertThat(q(1, M)).isEqualTo(q(1, M));
            assertThat(q(1, M)).isNotEqualTo(q(1, M2));
            assertThat(q(1, M)).isNotEqualTo(q(2, M));
        }
    }

    @Nested
    class Sum {

        @Test
        void addsAList() {
            assertThat(Quantity.sum(List.of(q(1, M), q(2, M), q(0.5, M)), M)).isEqualTo(new Quantity(3_500, M));
        }

        @Test
        void returnsATypedZeroForAnEmptyList() {
            assertThat(Quantity.sum(List.of(), M3)).isEqualTo(new Quantity(0, M3));
        }

        @Test
        void refusesAPartInTheWrongUnit() {
            assertThatThrownBy(() -> Quantity.sum(List.of(q(1, M), q(1, M2)), M))
                    .hasMessageContaining("different units");
        }
    }

    @Nested
    class RoundedToBillPrecision {

        @Test
        void roundsANumberItemToWholeUnits() {
            assertThat(q(3.6, NR).roundedToBillPrecision()).isEqualTo(new Quantity(4_000, NR));
            assertThat(q(3.4, NR).roundedToBillPrecision()).isEqualTo(new Quantity(3_000, NR));
        }

        @Test
        void roundsAVolumeToTwoPlaces() {
            assertThat(q(12.3456, M3).roundedToBillPrecision()).isEqualTo(new Quantity(12_350, M3));
        }

        @Test
        void leavesATonneAloneSinceThreePlacesIsWhatItIsHeldTo() {
            Quantity tonnes = q(12.345, T);
            assertThat(tonnes.roundedToBillPrecision()).isEqualTo(tonnes);
        }

        @Test
        void roundsANegativeTheSameWay() {
            assertThat(q(-3.6, NR).roundedToBillPrecision()).isEqualTo(new Quantity(-4_000, NR));
        }
    }

    @Nested
    class Format {

        @Test
        void writesTheUnitPrecisionByDefault() {
            assertThat(q(12.5, M3).format()).isEqualTo("12.50 m3");
            assertThat(q(4, NR).format()).isEqualTo("4 nr");
            assertThat(q(1.5, T).format()).isEqualTo("1.500 t");
        }

        @Test
        void honoursAnExplicitPrecision() {
            assertThat(q(12.5, M3).format(3)).isEqualTo("12.500 m3");
        }
    }

    @Nested
    class Parse {

        @Test
        void readsANumberAndAUnit() {
            assertThat(Quantity.parse("12.5 m3")).isEqualTo(new Quantity(12_500, M3));
        }

        @Test
        void readsASiteSpellingOfTheUnit() {
            assertThat(Quantity.parse("12.5 CUM")).isEqualTo(new Quantity(12_500, M3));
            assertThat(Quantity.parse("3 No.")).isEqualTo(new Quantity(3_000, NR));
        }

        @Test
        void readsANegativeAndAMissingSpace() {
            assertThat(Quantity.parse("-4m")).isEqualTo(new Quantity(-4_000, M));
        }

        @Test
        void reportsAMissingUnit() {
            Result<Quantity, CwException> r = Quantity.tryParse("12.5");
            assertThat(r.isOk()).isFalse();
            assertThat(r.error().getMessage()).contains("number and a unit");
        }

        @Test
        void reportsAnUnknownUnit() {
            Result<Quantity, CwException> r = Quantity.tryParse("12.5 furlongs");
            assertThat(r.isOk()).isFalse();
            assertThat(r.error().getMessage()).contains("unknown unit");
        }

        @Test
        void throwsFromTheStrictForm() {
            assertThatThrownBy(() -> Quantity.parse("nonsense")).isInstanceOf(BadInputException.class);
        }
    }

    @Nested
    class AssertBillableLumpSum {

        @Test
        void allowsExactlyOneSum() {
            assertThatCode(() -> q(1, SUM).assertBillableLumpSum()).doesNotThrowAnyException();
        }

        @Test
        void refusesABillThatCarriesALumpItemMoreThanOnce() {
            assertThatThrownBy(() -> q(3, SUM).assertBillableLumpSum()).hasMessageContaining("exactly one sum");
            assertThatThrownBy(() -> q(0, SUM).assertBillableLumpSum()).hasMessageContaining("exactly one sum");
        }

        @Test
        void saysNothingAboutAMeasurableUnit() {
            assertThatCode(() -> q(0.5, M3).assertBillableLumpSum()).doesNotThrowAnyException();
            assertThatCode(() -> q(4000, M2).assertBillableLumpSum()).doesNotThrowAnyException();
        }
    }
}
