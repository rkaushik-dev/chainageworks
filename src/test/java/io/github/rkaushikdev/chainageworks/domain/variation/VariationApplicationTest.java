package io.github.rkaushikdev.chainageworks.domain.variation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillDiff;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemInput;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemKind;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.support.BillFixtures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VariationApplicationTest {

    private static final Bill BILL = BillFixtures.sampleBill();
    private static final LocalDate AS_AT = SiteDates.parse("2025-03-31");

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static ItemCode c(String text) {
        return ItemCode.parse(text);
    }

    private static VariationInput input(String id, VariationItem... items) {
        return VariationInput.of(id, "EI-045", "deepen the cutting", List.of(items))
                .withStatus(VariationStatus.INSTRUCTED)
                .withInstructedOn(d("2025-03-14"));
    }

    private static Variation variation(String id, VariationItem... items) {
        return Variation.build(input(id, items));
    }

    private static VariationItem billRates(String code, double amount, Unit unit) {
        return VariationItem.atBillRates(c(code), Quantity.of(amount, unit));
    }

    private static VariationItem priced(String code, double amount, Unit unit, double rate, ValuationBasis basis) {
        return VariationItem.priced(c(code), Quantity.of(amount, unit), Rate.of(rate, Currency.GBP, unit), basis);
    }

    private static List<BillDiff.ChangeKind> kinds(VariationApplication applied) {
        return applied.changes().stream().map(BillDiff.Change::kind).toList();
    }

    @Nested
    class ApplyingAProRataRate {

        private final Variation rock =
                variation("VO-020", VariationItem.proRata(c("E424.1"), Quantity.of(200, Unit.M3), c("E423.1"), 2.5));

        @Test
        void buildsTheNewItemsRateFromTheSourceItemTimesTheFactor() {
            BillItem item = VariationApplication.apply(BILL, List.of(rock), AS_AT)
                    .bill()
                    .require(c("E424.1"));
            assertThat(item.rate()).isEqualTo(BILL.require(c("E423.1")).rate().scale(2.5));
            assertThat(item.kind()).isEqualTo(BillItemKind.STAR);
            assertThat(VariationApplication.value(BILL, List.of(rock), AS_AT).major())
                    .isEqualTo(2425);
        }

        @Test
        void followsTheSourceRateWhenTheBillIsRevised() {
            BillItem source = BILL.require(c("E423.1"));
            Bill revised = BILL.withItem(BillItem.build(new BillItemInput(
                    source.code(),
                    source.description(),
                    Rate.of(5.2, Currency.GBP, Unit.M3),
                    source.quantity(),
                    source.kind(),
                    source.chainage())));
            assertThat(VariationApplication.value(revised, List.of(rock), AS_AT).major())
                    .isEqualTo(2600);
        }

        @Test
        void refusesARateBuiltFromAnItemMeasuredInAnotherUnit() {
            String linear = BILL.items().stream()
                    .filter(item -> item.unit() != Unit.M3 && item.unit().isMeasurable())
                    .findFirst()
                    .orElseThrow()
                    .code()
                    .format();
            Variation wrong =
                    variation("VO-021", VariationItem.proRata(c("E424.1"), Quantity.of(10, Unit.M3), c(linear), 1.1));
            assertThatThrownBy(() -> VariationApplication.apply(BILL, List.of(wrong), AS_AT))
                    .hasMessageContaining("another unit");
        }

        @Test
        void refusesARateBuiltFromAnItemTheBillDoesNotCarry() {
            Variation stray =
                    variation("VO-022", VariationItem.proRata(c("E424.1"), Quantity.of(10, Unit.M3), c("E999.9"), 1.1));
            assertThatThrownBy(() -> VariationApplication.apply(BILL, List.of(stray), AS_AT))
                    .hasMessageContaining("E999.9");
        }
    }

    @Nested
    class BuildingAProRataItem {

        @Test
        void refusesASourceOnAnItemThatIsNotProRata() {
            VariationItem star = VariationItem.proRata(c("E424.1"), Quantity.of(1, Unit.M3), c("E423.1"), 1.2)
                    .withBasis(ValuationBasis.STAR_RATE);
            assertThatThrownBy(() -> variation("VO-030", star)).hasMessageContaining("only a pro rata item");
        }

        @Test
        void refusesBothARateAndASource() {
            VariationItem both = VariationItem.proRata(c("E424.1"), Quantity.of(1, Unit.M3), c("E423.1"), 1.2)
                    .withRate(Rate.of(10, Currency.GBP, Unit.M3));
            assertThatThrownBy(() -> variation("VO-031", both)).hasMessageContaining("not both");
        }

        @Test
        void refusesAFactorOfNothing() {
            assertThatThrownBy(() -> variation(
                            "VO-032", VariationItem.proRata(c("E424.1"), Quantity.of(1, Unit.M3), c("E423.1"), 0)))
                    .hasMessageContaining("more than nothing");
        }
    }

    @Nested
    class ApplyingAChangeAtBillRates {

        private final Variation deepening = variation("VO-012", billRates("E423.1", 1400, Unit.M3));

        @Test
        void movesTheQuantityAndLeavesTheRateAlone() {
            BillItem item = VariationApplication.apply(BILL, List.of(deepening), AS_AT)
                    .bill()
                    .require(c("E423.1"));
            assertThat(item.quantity().format()).isEqualTo("13800.00 m3");
            assertThat(item.rate().major()).isEqualTo(4.85);
        }

        @Test
        void isWorthTheChangeAtTheBillRate() {
            assertThat(VariationApplication.value(BILL, List.of(deepening), AS_AT)
                            .major())
                    .isEqualTo(6790);
        }

        @Test
        void reportsTheChangeAsAQuantityMove() {
            VariationApplication applied = VariationApplication.apply(BILL, List.of(deepening), AS_AT);
            assertThat(kinds(applied)).containsExactly(BillDiff.ChangeKind.QUANTITY_CHANGED);
            assertThat(applied.applied()).containsExactly("VO-012");
        }

        @Test
        void takesAnOmissionOff() {
            Variation omission = variation("VO-013", billRates("E532.1", -800, Unit.M3));
            assertThat(VariationApplication.value(BILL, List.of(omission), AS_AT)
                            .major())
                    .isEqualTo(-2520);
        }

        @Test
        void refusesAnOmissionLargerThanTheItem() {
            Variation wipe = variation("VO-014", billRates("E532.1", -99_999, Unit.M3));
            assertThatThrownBy(() -> VariationApplication.apply(BILL, List.of(wipe), AS_AT))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void refusesABillRateChangeAgainstAnItemTheBillDoesNotCarry() {
            Variation stray = variation("VO-015", billRates("Z999.1", 1, Unit.M3));
            assertThatThrownBy(() -> VariationApplication.apply(BILL, List.of(stray), AS_AT))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class ApplyingAStarRate {

        private final Variation handDig = Variation.build(VariationInput.of(
                        "VO-020",
                        "EI-045",
                        "hand excavation around live services",
                        List.of(priced("X400.1", 40, Unit.M3, 94, ValuationBasis.STAR_RATE)))
                .withStatus(VariationStatus.INSTRUCTED)
                .withInstructedOn(d("2025-03-14")));

        @Test
        void createsTheItemTheBillNeverCarried() {
            BillItem item = VariationApplication.apply(BILL, List.of(handDig), AS_AT)
                    .bill()
                    .require(c("X400.1"));
            assertThat(item.kind()).isEqualTo(BillItemKind.STAR);
            assertThat(item.rate().major()).isEqualTo(94);
            assertThat(item.description()).isEqualTo("hand excavation around live services (EI-045)");
        }

        @Test
        void isWorthTheNewItemInFull() {
            assertThat(VariationApplication.value(BILL, List.of(handDig), AS_AT).major())
                    .isEqualTo(3760);
        }

        @Test
        void repricesAnItemTheBillAlreadyHad() {
            Variation reprice = variation("VO-021", priced("E423.2", 0, Unit.M3, 7.2, ValuationBasis.PRO_RATA));
            VariationApplication applied = VariationApplication.apply(BILL, List.of(reprice), AS_AT);
            BillItem item = applied.bill().require(c("E423.2"));
            assertThat(item.rate().major()).isEqualTo(7.2);
            assertThat(item.kind()).isEqualTo(BillItemKind.STAR);
            assertThat(applied.netValue().major()).isEqualTo(5760);
        }

        @Test
        void refusesToOmitAnItemTheBillDoesNotCarry() {
            Variation nonsense = variation("VO-022", priced("X400.9", -5, Unit.M3, 1, ValuationBasis.STAR_RATE));
            assertThatThrownBy(() -> VariationApplication.apply(BILL, List.of(nonsense), AS_AT))
                    .hasMessageContaining("cannot omit an item");
        }

        @Test
        void createsALumpItemWhenTheChangeIsASum() {
            Variation settlement = Variation.build(VariationInput.of(
                            "VO-023",
                            "EI-045",
                            "agreed lump sum for the temporary diversion",
                            List.of(priced("X410.1", 1, Unit.SUM, 22_500, ValuationBasis.LUMP_SUM)))
                    .withStatus(VariationStatus.INSTRUCTED)
                    .withInstructedOn(d("2025-03-14")));
            VariationApplication applied = VariationApplication.apply(BILL, List.of(settlement), AS_AT);
            assertThat(applied.bill().require(c("X410.1")).kind()).isEqualTo(BillItemKind.LUMP);
            assertThat(applied.netValue().major()).isEqualTo(22_500);
        }
    }

    @Nested
    class InstructingAProvisionalItem {

        private final Variation instruct = Variation.build(VariationInput.of(
                        "VO-030", "EI-045", "rock excavation instructed", List.of(billRates("E423.9", 0, Unit.M3)))
                .withStatus(VariationStatus.INSTRUCTED)
                .withInstructedOn(d("2025-03-14")));

        @Test
        void turnsItIntoMeasuredMoneyWithoutMovingAFigure() {
            assertThat(BILL.find(c("E423.9")).orElseThrow().kind()).isEqualTo(BillItemKind.PROVISIONAL);
            VariationApplication applied = VariationApplication.apply(BILL, List.of(instruct), AS_AT);
            BillItem item = applied.bill().require(c("E423.9"));
            assertThat(item.kind()).isEqualTo(BillItemKind.MEASURED);
            assertThat(item.quantity().format()).isEqualTo("500.00 m3");
            assertThat(applied.netValue().minor()).isZero();
        }

        @Test
        void showsUpAsAKindChangeAndNothingElse() {
            VariationApplication applied = VariationApplication.apply(BILL, List.of(instruct), AS_AT);
            assertThat(kinds(applied)).containsExactly(BillDiff.ChangeKind.KIND_CHANGED);
        }
    }

    @Nested
    class WhatIsInForce {

        private final Variation early = Variation.build(
                input("VO-040", billRates("E423.1", 100, Unit.M3)).withInstructedOn(d("2025-02-01")));
        private final Variation late = Variation.build(
                input("VO-041", billRates("E423.1", 200, Unit.M3)).withInstructedOn(d("2025-05-01")));
        private final Variation proposal = Variation.build(input("VO-042", billRates("E423.1", 400, Unit.M3))
                .withStatus(VariationStatus.PROPOSED)
                .withInstructedOn(null));

        @Test
        void appliesOnlyTheOnesInstructedByTheDate() {
            VariationApplication applied = VariationApplication.apply(BILL, List.of(early, late, proposal), AS_AT);
            assertThat(applied.applied()).containsExactly("VO-040");
            assertThat(applied.netValue().major()).isEqualTo(485);
        }

        @Test
        void appliesThemInInstructionOrderAndStacksTheEffects() {
            VariationApplication applied =
                    VariationApplication.apply(BILL, List.of(early, late, proposal), d("2025-05-31"));
            assertThat(applied.applied()).containsExactly("VO-040", "VO-041");
            assertThat(applied.netValue().major()).isEqualTo(1455);
        }

        @Test
        void leavesTheBillAloneWhenNothingIsInForce() {
            VariationApplication applied = VariationApplication.apply(BILL, List.of(proposal), AS_AT);
            assertThat(applied.changes()).isEmpty();
            assertThat(applied.bill().total().major()).isEqualTo(BILL.total().major());
        }

        @Test
        void doesNotMutateTheBillItWasGiven() {
            VariationApplication.apply(BILL, List.of(early), AS_AT);
            assertThat(BILL.require(c("E423.1")).quantity().format()).isEqualTo("12400.00 m3");
        }

        @Test
        void reportsEveryTouchedCodeOnce() {
            VariationApplication applied = VariationApplication.apply(BILL, List.of(early, late), d("2025-05-31"));
            assertThat(applied.changes().stream().map(ch -> ch.code().format())).containsExactly("E423.1");
        }
    }
}
