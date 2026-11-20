package io.github.rkaushikdev.chainageworks.domain.variation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VariationTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static final VariationItem DEEPEN =
            VariationItem.atBillRates(ItemCode.parse("E423.1"), Quantity.of(1400, Unit.M3));

    private static final VariationInput BASE = VariationInput.of(
                    "VO-012", "EI-045", "deepen the cutting between 12+400 and 12+600", List.of(DEEPEN))
            .withStatus(VariationStatus.INSTRUCTED)
            .withInstructedOn(d("2025-03-14"))
            .withExtent(ChainageRange.of(Stations.parse("12+400"), Stations.parse("12+600")));

    private static final Variation DEEPENING = Variation.build(BASE);

    private static Variation proposal() {
        return Variation.build(BASE.withStatus(VariationStatus.PROPOSED).withInstructedOn(null));
    }

    @Nested
    class Build {

        @Test
        void defaultsToProposedWithNoDates() {
            Variation proposal = proposal();
            assertThat(proposal.status()).isEqualTo(VariationStatus.PROPOSED);
            assertThat(proposal.instructedOn()).isEmpty();
            assertThat(proposal.agreedOn()).isEmpty();
        }

        @Test
        void refusesAnInstructedVariationWithNoInstructionDate() {
            assertThatThrownBy(() -> Variation.build(BASE.withInstructedOn(null)))
                    .hasMessageContaining("needs the date it was instructed");
        }

        @Test
        void refusesAnAgreedVariationWithNoAgreementDate() {
            assertThatThrownBy(() -> Variation.build(BASE.withStatus(VariationStatus.AGREED)))
                    .hasMessageContaining("needs the date it was agreed");
        }

        @Test
        void refusesAgreementBeforeInstruction() {
            assertThatThrownBy(() -> Variation.build(
                            BASE.withStatus(VariationStatus.AGREED).withAgreedOn(d("2025-03-01"))))
                    .hasMessageContaining("agreed before it was instructed");
        }

        @Test
        void refusesAVariationThatChangesNothing() {
            assertThatThrownBy(() -> Variation.build(BASE.withItems(List.of())))
                    .hasMessageContaining("is not a variation");
        }

        @Test
        void refusesTheSameItemTwice() {
            assertThatThrownBy(() -> Variation.build(
                            BASE.withItems(List.of(DEEPEN, DEEPEN.withCode(ItemCode.parse("E 4 2 3 . 1"))))))
                    .hasMessageContaining("same item twice");
        }

        @Test
        void refusesABillRateItemThatCarriesItsOwnRate() {
            assertThatThrownBy(() -> Variation.build(
                            BASE.withItems(List.of(DEEPEN.withRate(Rate.of(5, Currency.GBP, Unit.M3))))))
                    .hasMessageContaining("cannot carry a rate of its own");
        }

        @Test
        void refusesAStarRateItemWithNoRate() {
            assertThatThrownBy(
                            () -> Variation.build(BASE.withItems(List.of(DEEPEN.withBasis(ValuationBasis.STAR_RATE)))))
                    .hasMessageContaining("needs its own rate");
        }

        @Test
        void refusesARateInADifferentUnitFromTheChange() {
            assertThatThrownBy(() -> Variation.build(BASE.withItems(List.of(
                            DEEPEN.withBasis(ValuationBasis.STAR_RATE).withRate(Rate.of(5, Currency.GBP, Unit.M2))))))
                    .hasMessageContaining("different units");
        }

        @Test
        void acceptsAnOmissionAsANegativeChange() {
            Variation omission = Variation.build(BASE.withId("VO-013")
                    .withItems(
                            List.of(VariationItem.atBillRates(ItemCode.parse("E532.1"), Quantity.of(-800, Unit.M3)))));
            assertThat(omission.items().get(0).quantityChange().milli()).isEqualTo(-800_000);
        }

        @Test
        void acceptsAStarRateForWorkTheBillNeverDescribed() {
            Variation star = Variation.build(BASE.withId("VO-014")
                    .withItems(List.of(VariationItem.priced(
                            ItemCode.parse("X400.1"),
                            Quantity.of(40, Unit.M3),
                            Rate.of(94, Currency.GBP, Unit.M3),
                            ValuationBasis.STAR_RATE))));
            assertThat(star.items().get(0).basis()).isEqualTo(ValuationBasis.STAR_RATE);
        }
    }

    @Nested
    class IsEffectiveAt {

        @Test
        void isFalseForAProposalHoweverOld() {
            assertThat(proposal().isEffectiveAt(d("2026-01-01"))).isFalse();
        }

        @Test
        void isFalseForARejectedVariation() {
            Variation rejected = Variation.build(BASE.withStatus(VariationStatus.REJECTED));
            assertThat(rejected.isEffectiveAt(d("2026-01-01"))).isFalse();
        }

        @Test
        void isTrueFromTheDayItWasInstructed() {
            assertThat(DEEPENING.isEffectiveAt(d("2025-03-13"))).isFalse();
            assertThat(DEEPENING.isEffectiveAt(d("2025-03-14"))).isTrue();
        }

        @Test
        void countsFromInstructionAndNotFromAgreement() {
            Variation late =
                    Variation.build(BASE.withStatus(VariationStatus.AGREED).withAgreedOn(d("2025-06-20")));
            assertThat(late.isEffectiveAt(d("2025-03-31"))).isTrue();
        }

        @Test
        void separatesAgreedFromMerelyInstructed() {
            assertThat(DEEPENING.isUnagreed()).isTrue();
            assertThat(DEEPENING.isAgreed()).isFalse();
            Variation settled =
                    Variation.build(BASE.withStatus(VariationStatus.AGREED).withAgreedOn(d("2025-06-20")));
            assertThat(settled.isAgreed()).isTrue();
            assertThat(settled.isUnagreed()).isFalse();
        }
    }

    @Nested
    class Effective {

        private final Variation later = Variation.build(BASE.withId("VO-020").withInstructedOn(d("2025-04-02")));
        private final Variation proposal = Variation.build(
                BASE.withId("VO-021").withStatus(VariationStatus.PROPOSED).withInstructedOn(null));

        @Test
        void takesTheOnesInForceInTheOrderTheyWereInstructed() {
            assertThat(Variation.effective(List.of(later, DEEPENING, proposal), d("2025-04-30")).stream()
                            .map(Variation::id))
                    .containsExactly("VO-012", "VO-020");
        }

        @Test
        void leavesOutAnythingInstructedAfterTheDate() {
            assertThat(Variation.effective(List.of(later, DEEPENING), d("2025-03-31")).stream()
                            .map(Variation::id))
                    .containsExactly("VO-012");
        }

        @Test
        void givesNothingWhenNoneAreInForce() {
            assertThat(Variation.effective(List.of(proposal), d("2026-01-01"))).isEmpty();
        }
    }

    @Nested
    class ItemForAndDescribe {

        @Test
        void findsTheChangeAgainstAnItem() {
            assertThat(DEEPENING.itemFor(ItemCode.parse("E423.1")).orElseThrow().basis())
                    .isEqualTo(ValuationBasis.BILL_RATES);
            assertThat(DEEPENING.itemFor(ItemCode.parse("R712.1"))).isEmpty();
        }

        @Test
        void readsLikeAVariationRegisterLine() {
            assertThat(DEEPENING.describe())
                    .isEqualTo("VO-012 deepen the cutting between 12+400 and 12+600 (EI-045, instructed 2025-03-14)");
        }

        @Test
        void saysSoWhenItHasNotBeenInstructed() {
            assertThat(proposal().describe()).contains("not instructed");
        }
    }
}
