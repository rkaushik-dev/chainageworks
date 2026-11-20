package io.github.rkaushikdev.chainageworks.domain.money;

import static io.github.rkaushikdev.chainageworks.domain.money.Currency.EUR;
import static io.github.rkaushikdev.chainageworks.domain.money.Currency.GBP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ApportionTest {

    private static Money gbp(long minor) {
        return Money.of(minor, GBP);
    }

    private static long total(List<Long> shares) {
        return shares.stream().mapToLong(Long::longValue).sum();
    }

    @Nested
    class Units {

        @Test
        void splitsEvenlyWhenItDivides() {
            assertThat(Apportion.units(100, List.of(1, 1, 1, 1))).containsExactly(25L, 25L, 25L, 25L);
        }

        @Test
        void givesTheLeftoverToTheLargestRemaindersNotToTheLastShare() {
            assertThat(Apportion.units(100, List.of(1, 1, 1))).containsExactly(34L, 33L, 33L);
        }

        @Test
        void alwaysAddsBackToTheTotal() {
            for (long whole : List.of(1L, 7L, 99L, 100L, 1234L, 100_000L)) {
                for (List<Integer> weights :
                        List.of(List.of(1, 1, 1), List.of(2, 3, 5), List.of(1), List.of(7, 11, 13, 17))) {
                    assertThat(total(Apportion.units(whole, weights))).isEqualTo(whole);
                }
            }
        }

        @Test
        void followsTheWeights() {
            assertThat(Apportion.units(1000, List.of(1, 4))).containsExactly(200L, 800L);
            assertThat(Apportion.units(1000, List.of(0, 1))).containsExactly(0L, 1000L);
        }

        @Test
        void breaksATieInFavourOfTheEarlierShare() {
            assertThat(Apportion.units(1, List.of(1, 1))).containsExactly(1L, 0L);
            assertThat(Apportion.units(2, List.of(1, 1, 1))).containsExactly(1L, 1L, 0L);
        }

        @Test
        void handlesANegativeTotalAndStillBalances() {
            assertThat(total(Apportion.units(-100, List.of(1, 1, 1)))).isEqualTo(-100);
        }

        @Test
        void splitsNothingIntoNothing() {
            assertThat(Apportion.units(0, List.of(1, 2, 3))).containsExactly(0L, 0L, 0L);
            assertThat(Apportion.units(0, List.of())).isEmpty();
            assertThat(Apportion.units(0, List.of(0, 0))).containsExactly(0L, 0L);
        }

        @Test
        void refusesAFractionalTotal() {
            assertThatThrownBy(() -> Apportion.units(1.5, List.of(1))).hasMessageContaining("whole number");
        }

        @Test
        void refusesToLoseATotalWithNowhereToPutIt() {
            assertThatThrownBy(() -> Apportion.units(100, List.of())).hasMessageContaining("nothing to apportion");
            assertThatThrownBy(() -> Apportion.units(100, List.of(0, 0)))
                    .hasMessageContaining("weights that are all zero");
        }

        @Test
        void refusesANegativeOrNonFiniteWeight() {
            assertThatThrownBy(() -> Apportion.units(100, List.of(1, -1))).hasMessageContaining("not negative");
            assertThatThrownBy(() -> Apportion.units(100, List.of(1.0, Double.NaN)))
                    .hasMessageContaining("finite");
        }

        @Test
        void acceptsFractionalWeights() {
            assertThat(Apportion.units(100, List.of(0.5, 1.5))).containsExactly(25L, 75L);
        }
    }

    @Nested
    class MoneyShares {

        @Test
        void keepsTheCurrencyOnEveryShare() {
            assertThat(Apportion.money(Money.of(100, EUR), List.of(1, 1, 1)))
                    .containsExactly(Money.of(34, EUR), Money.of(33, EUR), Money.of(33, EUR));
        }

        @Test
        void neverLosesAPennyOnAnAwkwardSplit() {
            Money whole = gbp(1_000_01);
            assertThat(Apportion.sharesBalance(whole, Apportion.money(whole, List.of(3, 3, 3, 1))))
                    .isTrue();
        }
    }

    @Nested
    class QuantityShares {

        @Test
        void splitsAMeasuredQuantityAndKeepsTheUnit() {
            assertThat(Apportion.quantity(Quantity.of(10, Unit.M3), List.of(1, 1)))
                    .containsExactly(new Quantity(5000, Unit.M3), new Quantity(5000, Unit.M3));
        }
    }

    @Nested
    class MoneyByQuantity {

        @Test
        void weightsTheSplitByWhatWasMeasured() {
            List<Money> shares =
                    Apportion.moneyByQuantity(gbp(10_000), List.of(Quantity.of(30, Unit.M3), Quantity.of(70, Unit.M3)));
            assertThat(shares.stream().map(Money::minor)).containsExactly(3000L, 7000L);
        }

        @Test
        void leavesNothingForAnItemThatMeasuredNothing() {
            List<Money> shares =
                    Apportion.moneyByQuantity(gbp(100), List.of(Quantity.of(0, Unit.M3), Quantity.of(1, Unit.M3)));
            assertThat(shares.stream().map(Money::minor)).containsExactly(0L, 100L);
        }
    }

    @Nested
    class SplitEvenly {

        @Test
        void dividesIntoEqualShares() {
            assertThat(Apportion.splitEvenly(gbp(1000), 4).stream().map(Money::minor))
                    .containsExactly(250L, 250L, 250L, 250L);
        }

        @Test
        void putsTheOddPenniesOnTheEarliestShares() {
            assertThat(Apportion.splitEvenly(gbp(1001), 4).stream().map(Money::minor))
                    .containsExactly(251L, 250L, 250L, 250L);
        }

        @Test
        void refusesAShareCountThatIsNotAPositiveWholeNumber() {
            // The count is an int, so a fraction of a share cannot be asked for at all.
            assertThatThrownBy(() -> Apportion.splitEvenly(gbp(100), 0)).hasMessageContaining("at least one share");
            assertThatThrownBy(() -> Apportion.splitEvenly(gbp(100), -2)).hasMessageContaining("at least one share");
        }
    }

    @Nested
    class SharesBalance {

        @Test
        void isTrueWhenTheSharesAddUp() {
            assertThat(Apportion.sharesBalance(gbp(100), List.of(gbp(60), gbp(40))))
                    .isTrue();
        }

        @Test
        void isFalseWhenAPennyIsMissing() {
            assertThat(Apportion.sharesBalance(gbp(100), List.of(gbp(60), gbp(39))))
                    .isFalse();
        }

        @Test
        void isFalseWhenAShareIsInTheWrongCurrency() {
            assertThat(Apportion.sharesBalance(gbp(100), List.of(gbp(60), Money.of(40, EUR))))
                    .isFalse();
        }

        @Test
        void isTrueForNothingSplitIntoNothing() {
            assertThat(Apportion.sharesBalance(gbp(0), List.of())).isTrue();
        }
    }
}
