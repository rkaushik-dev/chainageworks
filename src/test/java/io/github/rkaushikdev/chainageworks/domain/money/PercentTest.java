package io.github.rkaushikdev.chainageworks.domain.money;

import static io.github.rkaushikdev.chainageworks.domain.money.Currency.EUR;
import static io.github.rkaushikdev.chainageworks.domain.money.Currency.GBP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Result;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PercentTest {

    private static Percent p(double value) {
        return Percent.of(value);
    }

    private static Money gbp(long minor) {
        return Money.of(minor, GBP);
    }

    @Nested
    class Making {

        @Test
        void holdsBasisPoints() {
            assertThat(p(5).basisPoints()).isEqualTo(500);
            assertThat(p(3.75).basisPoints()).isEqualTo(375);
            assertThat(p(12.5).basisPoints()).isEqualTo(1250);
            assertThat(p(100).basisPoints()).isEqualTo(10_000);
        }

        @Test
        void refusesAFractionOfABasisPointAndANonNumber() {
            assertThatThrownBy(() -> Percent.ofBasisPoints(1.5)).hasMessageContaining("whole number of basis points");
            assertThatThrownBy(() -> Percent.ofBasisPoints(Double.NaN)).hasMessageContaining("finite");
            assertThatThrownBy(() -> p(Double.NaN)).hasMessageContaining("finite");
        }

        @Test
        void readsBackAsAPercentageAndAsAFraction() {
            assertThat(p(3.75).value()).isEqualTo(3.75);
            assertThat(p(5).fraction()).isEqualTo(0.05);
        }

        @Test
        void carriesTheTwoConstantsEverythingComparesAgainst() {
            assertThat(Percent.ZERO.basisPoints()).isZero();
            assertThat(Percent.FULL.basisPoints()).isEqualTo(10_000);
            assertThat(Percent.ZERO.isZero()).isTrue();
            assertThat(p(0.01).isZero()).isFalse();
        }
    }

    @Nested
    class ComparisonAndArithmetic {

        @Test
        void comparesAndTestsEquality() {
            assertThat(p(5)).isEqualTo(p(5));
            assertThat(p(5)).isNotEqualTo(p(5.01));
            assertThat(p(3).compareTo(p(5))).isNegative();
            assertThat(p(5).compareTo(p(5))).isZero();
        }

        @Test
        void addsAndSubtracts() {
            assertThat(p(3).plus(p(2))).isEqualTo(new Percent(500));
            assertThat(p(3).minus(p(5))).isEqualTo(new Percent(-200));
        }

        @Test
        void takesTheComplementWhichIsWhatRetentionLeavesBehind() {
            assertThat(p(5).complement()).isEqualTo(new Percent(9500));
            assertThat(Percent.ZERO.complement()).isEqualTo(Percent.FULL);
        }

        @Test
        void clampsIntoABand() {
            assertThat(p(-1).clamp(Percent.ZERO, Percent.FULL)).isEqualTo(Percent.ZERO);
            assertThat(p(150).clamp(Percent.ZERO, Percent.FULL)).isEqualTo(Percent.FULL);
            assertThat(p(50).clamp(Percent.ZERO, Percent.FULL)).isEqualTo(p(50));
        }

        @Test
        void refusesClampBoundsTheWrongWayRound() {
            assertThatThrownBy(() -> p(5).clamp(Percent.FULL, Percent.ZERO)).hasMessageContaining("wrong way round");
        }
    }

    @Nested
    class ApplyToMoney {

        @Test
        void takesRetentionOffAValuation() {
            assertThat(p(5).of(gbp(100_000)).minor()).isEqualTo(5000);
        }

        @Test
        void handlesAnAwkwardPercentageExactly() {
            assertThat(p(3.75).of(gbp(100_000)).minor()).isEqualTo(3750);
        }

        @Test
        void roundsHalfAwayFromZero() {
            assertThat(p(50).of(gbp(101)).minor()).isEqualTo(51);
            assertThat(p(50).of(gbp(-101)).minor()).isEqualTo(-51);
        }

        @Test
        void keepsTheCurrency() {
            assertThat(p(10).of(Money.of(100, EUR)).currency()).isEqualTo(EUR);
        }

        @Test
        void givesNothingForNothing() {
            assertThat(Percent.ZERO.of(gbp(100_000)).minor()).isZero();
            assertThat(p(5).of(gbp(0)).minor()).isZero();
        }
    }

    @Nested
    class ApplyToQuantity {

        @Test
        void takesAShareOfAMeasuredQuantity() {
            assertThat(p(25).of(Quantity.of(100, Unit.M3))).isEqualTo(new Quantity(25_000, Unit.M3));
        }

        @Test
        void roundsToTheThousandth() {
            assertThat(p(0.05).of(Quantity.of(1, Unit.M3)).milli()).isEqualTo(1);
        }
    }

    @Nested
    class Share {

        @Test
        void worksOutTheShareOnePartIsOfAWhole() {
            assertThat(Percent.share(gbp(2500), gbp(10_000))).isEqualTo(new Percent(2500));
        }

        @Test
        void roundsToTheNearestBasisPoint() {
            assertThat(Percent.share(gbp(1), gbp(3)).value()).isEqualTo(33.33);
        }

        @Test
        void refusesToDivideByNothing() {
            assertThatThrownBy(() -> Percent.share(gbp(1), gbp(0))).hasMessageContaining("percentage of nothing");
        }
    }

    @Nested
    class Format {

        @Test
        void dropsTheDecimalsWhenThereAreNone() {
            assertThat(p(5).format()).isEqualTo("5%");
        }

        @Test
        void showsTwoPlacesWhenThePercentageNeedsThem() {
            assertThat(p(3.75).format()).isEqualTo("3.75%");
        }

        @Test
        void honoursAnExplicitPrecision() {
            assertThat(p(5).format(2)).isEqualTo("5.00%");
            assertThat(p(3.75).format(1)).isEqualTo("3.8%");
        }
    }

    @Nested
    class Parse {

        @Test
        void readsANumberWithOrWithoutTheSign() {
            assertThat(Percent.parse("5%")).isEqualTo(new Percent(500));
            assertThat(Percent.parse("5")).isEqualTo(new Percent(500));
            assertThat(Percent.parse(" 3.75 % ")).isEqualTo(new Percent(375));
        }

        @Test
        void readsANegative() {
            assertThat(Percent.parse("-2.5%").basisPoints()).isEqualTo(-250);
        }

        @Test
        void reportsAnythingElse() {
            Result<Percent, CwException> r = Percent.tryParse("a bit");
            assertThat(r.isOk()).isFalse();
            assertThat(r.error().getMessage()).contains("must be a number");
            assertThatThrownBy(() -> Percent.parse("a bit")).isInstanceOf(BadInputException.class);
        }
    }
}
