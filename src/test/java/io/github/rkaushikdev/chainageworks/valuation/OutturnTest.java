package io.github.rkaushikdev.chainageworks.valuation;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayCause;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayEvent;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayEventInput;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexReading;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexSeries;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.Weighting;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.variation.Variation;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationInput;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationItem;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationStatus;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutturnTest {

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final ProgressContext CONTEXT = sampleContext();
    private static final Money CONTRACT_SUM = gbp(312_190);
    private static final SiteCalendar CALENDAR = SiteCalendar.build(CalendarInput.of("C1", "main works"));
    private static final LocalDate AS_AT = d("2025-03-25");

    private static final OutturnInput BASE = OutturnInput.of(CONTEXT, AS_AT, CONTRACT_SUM);

    private static final VariationInput VARIATION_INPUT = VariationInput.of(
                    "VO-01",
                    "VO/2025/001",
                    "extra rock excavation at the cutting",
                    List.of(VariationItem.atBillRates(ItemCode.parse("E423.1"), Quantity.of(500, Unit.M3))))
            .withStatus(VariationStatus.INSTRUCTED)
            .withInstructedOn(d("2025-02-10"));

    private static final Variation VARIATION = Variation.build(VARIATION_INPUT);

    private static final IndexSeries LABOUR = IndexSeries.build(
            "IDX-LAB", null, List.of(IndexReading.firm(2024, 10, 100), IndexReading.firm(2025, 3, 110)));

    private static final AdjustmentTerms CLAUSE =
            AdjustmentTerms.build(d("2024-10-25"), Percent.of(10), List.of(new Weighting("IDX-LAB", Percent.of(90))));

    private static final CompletionTerms COMPLETION =
            CompletionTerms.build(d("2025-02-14"), gbp(2500), Percent.of(10), null);

    @Nested
    class ABareOutturn {

        private static final Outturn OUTTURN = Outturn.build(BASE);

        @Test
        void isTheMeasuredWorksAndNothingElse() {
            assertThat(OUTTURN.measuredWorks().major())
                    .isEqualTo(OUTTURN.account().forecastOutturn().major());
            assertThat(OUTTURN.variations().minor()).isZero();
            assertThat(OUTTURN.daywork().minor()).isZero();
            assertThat(OUTTURN.damages().minor()).isZero();
            assertThat(OUTTURN.priceAdjustment().amount().minor()).isZero();
        }

        @Test
        void totalsToTheMeasuredWorks() {
            assertThat(OUTTURN.total().major())
                    .isEqualTo(OUTTURN.measuredWorks().major());
        }

        @Test
        void measuresTheMovementAgainstTheContractSum() {
            assertThat(OUTTURN.movement().major()).isEqualTo(OUTTURN.total().major() - CONTRACT_SUM.major());
            assertThat(OUTTURN.movementPercent().value()).isLessThanOrEqualTo(0);
            assertThat(OUTTURN.isOverContractSum()).isFalse();
        }

        @Test
        void forecastsAJobWithNothingMeasuredAgainstItAtTheBillTotal() {
            assertThat(OUTTURN.total().major()).isEqualTo(CONTRACT_SUM.major());
            assertThat(OUTTURN.movement().minor()).isZero();
        }

        @Test
        void addsUpFromItsOwnParts() {
            assertThat(OUTTURN.partsAddUp()).isTrue();
        }
    }

    @Nested
    class AnOutturnWithVariations {

        private static final Outturn OUTTURN = Outturn.build(BASE.withVariations(List.of(VARIATION)));

        @Test
        void valuesTheVariationAgainstTheUnvariedBill() {
            assertThat(OUTTURN.variations().minor()).isPositive();
            assertThat(OUTTURN.variations().major()).isEqualTo(2425);
        }

        @Test
        void carriesTheVariationIntoTheTotal() {
            Outturn bare = Outturn.build(BASE);
            assertThat(OUTTURN.total().major())
                    .isEqualTo(bare.total().major() + OUTTURN.variations().major());
        }

        @Test
        void ignoresAVariationThatHasNotBeenInstructed() {
            Outturn proposed = Outturn.build(BASE.withVariations(
                    List.of(Variation.build(VARIATION_INPUT.withStatus(VariationStatus.PROPOSED)))));
            assertThat(proposed.variations().minor()).isZero();
        }
    }

    @Nested
    class AnOutturnWithAFluctuationClause {

        private static final Outturn OUTTURN = Outturn.build(BASE.withAdjustment(CLAUSE, List.of(LABOUR)));

        @Test
        void appliesTheFactorToTheForecastPermanentWork() {
            assertThat(OUTTURN.priceAdjustment().factor().factor()).isCloseTo(0.09, byLessThan(5e-11));
            assertThat(OUTTURN.priceAdjustment().amount().minor()).isPositive();
        }

        @Test
        void takesTheFixedElementOffBeforeApplyingIt() {
            assertThat(OUTTURN.priceAdjustment().adjustable().major())
                    .isCloseTo(OUTTURN.measuredWorks().major() * 0.9, byLessThan(0.05));
        }

        @Test
        void stillAddsUpFromItsParts() {
            assertThat(OUTTURN.partsAddUp()).isTrue();
        }
    }

    @Nested
    class AnOutturnOnALateJob {

        private static final Outturn OUTTURN = Outturn.build(BASE.withCompletion(COMPLETION, CALENDAR)
                .withDelayEvents(List.of(DelayEvent.build(DelayEventInput.of(
                                "DE-01",
                                "EW/2025/001",
                                DelayCause.UTILITIES,
                                "live water main",
                                d("2025-01-06"),
                                d("2025-01-10"))
                        .withNotifiedOn(d("2025-01-07"))
                        .withAwardedDays(5)))));

        @Test
        void deductsTheDamagesFromTheTotal() {
            assertThat(OUTTURN.damages().minor()).isPositive();
            Outturn bare = Outturn.build(BASE);
            assertThat(OUTTURN.total().major())
                    .isEqualTo(bare.total().major() - OUTTURN.damages().major());
        }

        @Test
        void reportsHowLateTheJobIsForecastToBe() {
            assertThat(Outturn.forecastDaysLate(
                            BASE.withCompletion(COMPLETION, CALENDAR).withDelayEvents(List.of())))
                    .isPositive();
        }

        @Test
        void isNotLateAtAllWhenTheContractFixesNoCompletionDate() {
            assertThat(Outturn.forecastDaysLate(BASE)).isZero();
            assertThat(Outturn.build(BASE).damages().minor()).isZero();
        }

        @Test
        void stillAddsUpFromItsParts() {
            assertThat(OUTTURN.partsAddUp()).isTrue();
        }
    }

    @Nested
    class OutturnParts {

        @Test
        void setsTheAccountOutTheWayAFinalAccountLetterDoes() {
            Outturn outturn = Outturn.build(BASE.withVariations(List.of(VARIATION)));
            assertThat(outturn.parts().stream().map(Outturn.Part::name))
                    .containsExactly(
                            "measured works", "variations", "daywork", "price adjustment", "liquidated damages");
        }

        @Test
        void carriesTheDamagesAsADeduction() {
            Outturn outturn = Outturn.build(BASE.withCompletion(COMPLETION, CALENDAR));
            Outturn.Part damages = outturn.parts().get(outturn.parts().size() - 1);
            assertThat(damages.amount().minor()).isNegative();
        }
    }

    @Nested
    class DescribeOutturn {

        @Test
        void saysWhichSideOfTheContractSumTheJobIsOn() {
            assertThat(Outturn.build(BASE).describe()).matches("outturn at 2025-03-25, [\\d.]+ under the contract sum");
        }
    }
}
