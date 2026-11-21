package io.github.rkaushikdev.chainageworks.valuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RetentionBondTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final RetentionBond BOND = RetentionBond.build(
            RetentionBondInput.of("RB-01", "Northgate Surety", gbp(9365), d("2024-11-01"), d("2026-11-01"))
                    .withRate(Percent.of(1.2))
                    .withReducesTo(gbp(4682)));

    private static RetentionBondInput plain(String id) {
        return RetentionBondInput.of(id, "Northgate Surety", gbp(10_000), d("2025-01-01"), d("2026-01-01"));
    }

    @Nested
    class BuildRetentionBond {

        @Test
        void takesTheStandardRateWhenNoneIsGiven() {
            RetentionBond bare = RetentionBond.build(plain("RB-02"));
            assertThat(bare.rate()).isEqualTo(RetentionBond.DEFAULT_RATE);
            assertThat(bare.reducesTo()).isEmpty();
        }

        @Test
        void refusesABondForNothing() {
            assertThatThrownBy(() -> RetentionBond.build(RetentionBondInput.of(
                            "RB-03", "Northgate Surety", gbp(0), d("2025-01-01"), d("2026-01-01"))))
                    .hasMessageContaining("for some amount");
        }

        @Test
        void refusesABondThatExpiresBeforeItIsIssued() {
            assertThatThrownBy(() -> RetentionBond.build(RetentionBondInput.of(
                            "RB-04", "Northgate Surety", gbp(10_000), d("2026-01-01"), d("2025-01-01"))))
                    .hasMessageContaining("expire before it is issued");
        }

        @Test
        void refusesAReductionLargerThanTheBondItself() {
            assertThatThrownBy(() -> RetentionBond.build(plain("RB-05").withReducesTo(gbp(12_000))))
                    .hasMessageContaining("reduce to more than it started at");
        }

        @Test
        void refusesARateOutsideNoughtToAHundred() {
            assertThatThrownBy(() -> RetentionBond.build(plain("RB-06").withRate(Percent.of(140))))
                    .hasMessageContaining("runs from 0 to 100");
        }
    }

    @Nested
    class Position {

        @Test
        void coversTheRetentionInFullWhileTheBondIsBigEnough() {
            BondPosition position = BOND.position(gbp(5000), d("2025-03-25"));
            assertThat(position.cover().major()).isEqualTo(9365);
            assertThat(position.released().major()).isEqualTo(5000);
            assertThat(position.shortfall().minor()).isZero();
            assertThat(position.expired()).isFalse();
        }

        @Test
        void reportsThePartOfTheRetentionTheBondCannotReach() {
            BondPosition position = BOND.position(gbp(12_000), d("2025-03-25"));
            assertThat(position.released().major()).isEqualTo(9365);
            assertThat(position.shortfall().major()).isEqualTo(2635);
        }

        @Test
        void coversNothingBeforeTheBondWasIssued() {
            BondPosition position = BOND.position(gbp(5000), d("2024-10-01"));
            assertThat(position.cover().minor()).isZero();
            assertThat(position.shortfall().major()).isEqualTo(5000);
        }

        @Test
        void coversNothingOnceTheBondHasLapsed() {
            BondPosition position = BOND.position(gbp(5000), d("2026-11-02"));
            assertThat(position.expired()).isTrue();
            assertThat(position.cover().minor()).isZero();
            assertThat(position.shortfall().major()).isEqualTo(5000);
        }

        @Test
        void dropsToTheReducedAmountFromSubstantialCompletion() {
            BondPosition position = BOND.position(gbp(9000), d("2025-10-01"), d("2025-09-26"));
            assertThat(position.cover().major()).isEqualTo(4682);
            assertThat(position.shortfall().major()).isEqualTo(4318);
        }

        @Test
        void holdsTheFullAmountUpToTheDayBeforeCompletion() {
            BondPosition position = BOND.position(gbp(9000), d("2025-09-25"), d("2025-09-26"));
            assertThat(position.cover().major()).isEqualTo(9365);
        }

        @Test
        void countsTheDaysToExpiry() {
            assertThat(BOND.position(gbp(5000), d("2026-10-25")).daysToExpiry()).isEqualTo(7);
        }
    }

    @Nested
    class WhatABondCosts {

        @Test
        void chargesOnTheFaceRatherThanOnWhatIsDrawn() {
            assertThat(BOND.annualCharge().major()).isCloseTo(112.38, byLessThan(0.005));
        }

        @Test
        void chargesForTheWholeLifeOfTheBond() {
            assertThat(BOND.totalCharge().major()).isCloseTo(224.76 * (730.0 / 730), byLessThan(0.05));
        }

        @Test
        void chargesNothingAtARateOfNothing() {
            RetentionBond free = RetentionBond.build(plain("RB-07").withRate(Percent.of(0)));
            assertThat(free.totalCharge().minor()).isZero();
        }
    }

    @Nested
    class IsWorthTaking {

        @Test
        void isWorthItWhenMoneyCostsMoreThanTheSuretyDoes() {
            assertThat(BOND.isWorthTaking(gbp(9365), Percent.of(6))).isTrue();
        }

        @Test
        void isNotWorthItWhenTheRetentionItWouldFreeUpIsSmall() {
            assertThat(BOND.isWorthTaking(gbp(500), Percent.of(2))).isFalse();
        }
    }

    @Nested
    class ExpiringWithin {

        private final RetentionBond soon = RetentionBond.build(
                RetentionBondInput.of("RB-08", "Northgate Surety", gbp(5000), d("2024-01-01"), d("2025-04-10")));

        @Test
        void namesTheBondsAboutToLapseSoonestFirst() {
            assertThat(RetentionBond.expiringWithin(List.of(BOND, soon), d("2025-03-25"), 30).stream()
                            .map(RetentionBond::id))
                    .containsExactly("RB-08");
        }

        @Test
        void leavesOutOneThatHasAlreadyLapsed() {
            assertThat(RetentionBond.expiringWithin(List.of(soon), d("2025-05-01"), 30))
                    .isEmpty();
        }

        @Test
        void leavesOutOneNowhereNearLapsing() {
            assertThat(RetentionBond.expiringWithin(List.of(BOND), d("2025-03-25"), 30))
                    .isEmpty();
        }
    }

    @Nested
    class DescribeBond {

        @Test
        void saysTheBondCoversTheRetention() {
            assertThat(BOND.position(gbp(5000), d("2025-03-25")).describe())
                    .isEqualTo("RB-01 with Northgate Surety, covers the retention in full");
        }

        @Test
        void saysHowFarShortItFalls() {
            assertThat(BOND.position(gbp(12_000), d("2025-03-25")).describe()).contains("2635 short of the retention");
        }

        @Test
        void saysSoWhenItHasLapsed() {
            assertThat(BOND.position(gbp(5000), d("2026-12-01")).describe()).isEqualTo("RB-01 expired on 2026-11-01");
        }
    }
}
