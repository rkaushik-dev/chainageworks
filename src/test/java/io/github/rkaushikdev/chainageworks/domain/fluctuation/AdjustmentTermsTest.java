package io.github.rkaushikdev.chainageworks.domain.fluctuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AdjustmentTermsTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static Percent p(double value) {
        return Percent.of(value);
    }

    private static final IndexSeries LABOUR = IndexSeries.build(
            "IDX-LAB",
            "civil engineering labour",
            List.of(
                    new IndexReading(2024, 10, 100, false),
                    new IndexReading(2025, 1, 110, false),
                    new IndexReading(2025, 3, 120, true)));

    private static final IndexSeries PLANT = IndexSeries.build(
            "IDX-PLA",
            "plant hire",
            List.of(
                    new IndexReading(2024, 10, 200, false),
                    new IndexReading(2025, 1, 210, false),
                    new IndexReading(2025, 3, 205, false)));

    private static final List<IndexSeries> SERIES = List.of(LABOUR, PLANT);

    private static final AdjustmentTerms TERMS = AdjustmentTerms.build(
            d("2024-10-25"), p(10), List.of(new Weighting("IDX-LAB", p(60)), new Weighting("IDX-PLA", p(30))));

    @Nested
    class Build {

        @Test
        void takesASetThatComesToTheWholePrice() {
            assertThat(TERMS.weightings()).hasSize(2);
            assertThat(TERMS.nonAdjustable().value()).isEqualTo(10);
        }

        @Test
        void takesNoFixedElementAsNoneAtAll() {
            AdjustmentTerms none =
                    AdjustmentTerms.build(d("2024-10-25"), null, List.of(new Weighting("IDX-LAB", p(100))));
            assertThat(none.nonAdjustable().value()).isZero();
        }

        @Test
        void refusesASetThatDoesNotComeToAHundred() {
            assertThatThrownBy(() ->
                            AdjustmentTerms.build(d("2024-10-25"), p(10), List.of(new Weighting("IDX-LAB", p(60)))))
                    .hasMessageContaining("have to come to 100");
        }

        @Test
        void refusesTwoWeightingsAgainstOneIndex() {
            assertThatThrownBy(() -> AdjustmentTerms.build(
                            d("2024-10-25"),
                            null,
                            List.of(new Weighting("IDX-LAB", p(50)), new Weighting("IDX-LAB", p(50)))))
                    .hasMessageContaining("two weightings");
        }

        @Test
        void refusesANegativeWeighting() {
            assertThatThrownBy(() -> AdjustmentTerms.build(
                            d("2024-10-25"),
                            null,
                            List.of(new Weighting("IDX-LAB", p(110)), new Weighting("IDX-PLA", p(-10)))))
                    .hasMessageContaining("cannot be negative");
        }
    }

    @Nested
    class FactorAt {

        @Test
        void weightsEachIndexByItsOwnMovement() {
            AdjustmentFactor factor = TERMS.factorAt(SERIES, d("2025-01-25"));
            assertThat(factor.lines().get(0).movement()).isCloseTo(0.1, byLessThan(5e-11));
            assertThat(factor.lines().get(0).contribution()).isCloseTo(0.06, byLessThan(5e-11));
            assertThat(factor.lines().get(1).movement()).isCloseTo(0.05, byLessThan(5e-11));
            assertThat(factor.lines().get(1).contribution()).isCloseTo(0.015, byLessThan(5e-11));
            assertThat(factor.factor()).isCloseTo(0.075, byLessThan(5e-11));
        }

        @Test
        void letsOneIndexFallWhileAnotherRises() {
            AdjustmentFactor factor = TERMS.factorAt(SERIES, d("2025-03-25"));
            assertThat(factor.lines().get(1).movement()).isCloseTo(0.025, byLessThan(5e-11));
            assertThat(factor.factor()).isCloseTo(0.6 * 0.2 + 0.3 * 0.025, byLessThan(5e-11));
        }

        @Test
        void isNothingAtTheBaseDateItself() {
            assertThat(TERMS.factorAt(SERIES, d("2024-10-25")).factor()).isZero();
        }

        @Test
        void saysSoWhileAnyContributingFigureIsProvisional() {
            assertThat(TERMS.factorAt(SERIES, d("2025-01-25")).provisional()).isFalse();
            assertThat(TERMS.factorAt(SERIES, d("2025-03-25")).provisional()).isTrue();
        }

        @Test
        void refusesAFactorAgainstAnIndexNobodyLoaded() {
            assertThatThrownBy(() -> TERMS.factorAt(List.of(LABOUR), d("2025-01-25")))
                    .hasMessageContaining("index series");
        }
    }

    @Nested
    class IndexLag {

        private final AdjustmentTerms lagged = TERMS.withLagMonths(2);

        @Test
        void readsTheCurrentFiguresTheLagBackFromTheValuation() {
            assertThat(lagged.indicesReadAt(d("2025-03-25"))).isEqualTo(d("2025-01-25"));
            AdjustmentFactor factor = lagged.factorAt(SERIES, d("2025-03-25"));
            assertThat(factor.asAt()).isEqualTo(d("2025-03-25"));
            assertThat(factor.indicesReadAt()).isEqualTo(d("2025-01-25"));
            assertThat(factor.factor())
                    .isCloseTo(TERMS.factorAt(SERIES, d("2025-01-25")).factor(), byLessThan(5e-11));
        }

        @Test
        void keepsAProvisionalFigureOutOfAFactorThatNoLongerReadsIt() {
            assertThat(TERMS.factorAt(SERIES, d("2025-03-25")).provisional()).isTrue();
            assertThat(lagged.factorAt(SERIES, d("2025-03-25")).provisional()).isFalse();
        }

        @Test
        void movesNothingWhileTheLagStillReachesBackPastTheBaseDate() {
            assertThat(lagged.indicesReadAt(d("2024-11-25"))).isEqualTo(d("2024-10-25"));
            assertThat(lagged.factorAt(SERIES, d("2024-11-25")).factor()).isZero();
        }

        @Test
        void readsTheValuationMonthItselfWithNoLag() {
            assertThat(TERMS.lagMonths()).isZero();
            assertThat(TERMS.indicesReadAt(d("2025-03-25"))).isEqualTo(d("2025-03-25"));
        }

        @Test
        void refusesALagThatRunsForwards() {
            assertThatThrownBy(() -> TERMS.withLagMonths(-1)).hasMessageContaining("index lag");
        }
    }

    @Nested
    class Adjust {

        @Test
        void takesTheFixedElementOffBeforeApplyingTheFactor() {
            PriceAdjustment adjustment = TERMS.adjust(SERIES, gbp(100_000), d("2025-01-25"));
            assertThat(adjustment.adjustable().major()).isEqualTo(90_000);
            assertThat(adjustment.amount().major()).isEqualTo(6750);
        }

        @Test
        void movesNothingAtAllWhenTheFixedElementIsTheWholePrice() {
            AdjustmentTerms fixed = AdjustmentTerms.build(d("2024-10-25"), p(100), List.of());
            PriceAdjustment adjustment = fixed.adjust(SERIES, gbp(100_000), d("2025-01-25"));
            assertThat(adjustment.adjustable().minor()).isZero();
            assertThat(adjustment.amount().minor()).isZero();
        }

        @Test
        void takesMoneyOffWhenTheIndicesHaveFallenBack() {
            IndexSeries falling = IndexSeries.build(
                    "IDX-LAB",
                    null,
                    List.of(new IndexReading(2024, 10, 100, false), new IndexReading(2025, 1, 90, false)));
            AdjustmentTerms all =
                    AdjustmentTerms.build(d("2024-10-25"), null, List.of(new Weighting("IDX-LAB", p(100))));
            assertThat(all.adjust(List.of(falling), gbp(100_000), d("2025-01-25"))
                            .amount()
                            .major())
                    .isEqualTo(-10_000);
        }

        @Test
        void takesTheLowerOfTheCompletionDateAndCurrentFactorsOnceLate() {
            PriceAdjustment held = TERMS.adjust(SERIES, gbp(100_000), d("2025-03-25"), Optional.of(d("2025-01-25")));
            assertThat(held.heldAtCompletion()).isTrue();
            assertThat(held.factor().asAt()).isEqualTo(d("2025-01-25"));
            assertThat(held.amount().major()).isEqualTo(6750);
            assertThat(held.asAt()).isEqualTo(d("2025-03-25"));
        }

        @Test
        void ignoresACompletionDateStillAhead() {
            PriceAdjustment current = TERMS.adjust(SERIES, gbp(100_000), d("2025-01-25"), Optional.of(d("2025-03-25")));
            assertThat(current.heldAtCompletion()).isFalse();
            assertThat(current).isEqualTo(TERMS.adjust(SERIES, gbp(100_000), d("2025-01-25")));
        }

        @Test
        void leavesEarlierWorkAtTheFactorItWasDoneAtWhenAppliedPerPeriod() {
            AdjustmentTerms perPeriod = TERMS.withPerPeriod(true);
            PriceAdjustment january =
                    perPeriod.adjustFrom(Optional.empty(), SERIES, gbp(100_000), d("2025-01-25"), Optional.empty());
            assertThat(january.amount().major()).isEqualTo(6750);
            PriceAdjustment march =
                    perPeriod.adjustFrom(Optional.of(january), SERIES, gbp(150_000), d("2025-03-25"), Optional.empty());
            assertThat(march.amount().major()).isEqualTo(6750 + 5737.5);
            assertThat(march.adjustable().major()).isEqualTo(135_000);
            assertThat(march.base().major()).isEqualTo(150_000);
            assertThat(march.provisional()).isTrue();
            assertThat(TERMS.adjust(SERIES, gbp(150_000), d("2025-03-25"))
                            .amount()
                            .major())
                    .isEqualTo(17_212.5);
        }

        @Test
        void carriesTheProvisionalFlagThroughFromTheFactor() {
            assertThat(TERMS.adjust(SERIES, gbp(100_000), d("2025-03-25")).provisional())
                    .isTrue();
        }

        @Test
        void isNothingOnAValuationOfNothing() {
            assertThat(TERMS.adjust(SERIES, gbp(0), d("2025-01-25")).amount().minor())
                    .isZero();
        }
    }

    @Nested
    class FactorPercent {

        @Test
        void readsBackAsAPercentage() {
            AdjustmentFactor factor = TERMS.factorAt(SERIES, d("2025-01-25"));
            assertThat(factor.asPercent().value()).isCloseTo(7.5, byLessThan(0.005));
        }
    }

    @Nested
    class NoAdjustment {

        @Test
        void movesNothingAndReportsNothingProvisional() {
            PriceAdjustment none = PriceAdjustment.none(gbp(100_000), d("2025-01-25"));
            assertThat(none.amount().minor()).isZero();
            assertThat(none.factor().lines()).isEmpty();
            assertThat(none.provisional()).isFalse();
        }
    }

    @Nested
    class Describe {

        @Test
        void saysTheFactorWhatItAppliedToAndWhetherItIsFirm() {
            PriceAdjustment adjustment = TERMS.adjust(SERIES, gbp(100_000), d("2025-01-25"));
            assertThat(adjustment.describe()).isEqualTo("factor 7.500 percent on 90000 adjustable, firm");
        }
    }
}
