package io.github.rkaushikdev.chainageworks.domain.bill;

import static io.github.rkaushikdev.chainageworks.domain.money.Currency.GBP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BillItemTest {

    private static Rate rate(double major, Unit unit) {
        return Rate.of(major, GBP, unit);
    }

    private static Quantity q(double amount, Unit unit) {
        return Quantity.of(amount, unit);
    }

    private static BillItemInput input(String code, String description, Rate rate, Quantity quantity) {
        return BillItemInput.of(ItemCode.parse(code), description, rate, quantity);
    }

    private static final BillItem EXCAVATION = BillItem.build(input(
            "E423.1", "excavation for cuttings, maximum depth 2 to 5 m", rate(4.85, Unit.M3), q(12_400, Unit.M3)));

    private static ChainageRange range(String from, String to) {
        return ChainageRange.of(Stations.parse(from), Stations.parse(to));
    }

    @Nested
    class Build {

        @Test
        void takesTheUnitFromTheBilledQuantity() {
            assertThat(EXCAVATION.unit()).isEqualTo(Unit.M3);
            assertThat(EXCAVATION.kind()).isEqualTo(BillItemKind.MEASURED);
            assertThat(EXCAVATION.chainage()).isEmpty();
        }

        @Test
        void trimsTheDescription() {
            BillItem item = BillItem.build(
                    input("E423.2", "  disposal of excavated material  ", rate(3.1, Unit.M3), q(100, Unit.M3)));
            assertThat(item.description()).isEqualTo("disposal of excavated material");
        }

        @Test
        void keepsAChainageRangeWhenTheBillGivesOne() {
            BillItem item =
                    BillItem.build(input("R712.1", "sub base, depth 225 mm", rate(11.4, Unit.M2), q(4000, Unit.M2))
                            .withChainage(range("12+000", "12+800")));
            assertThat(item.chainage()).isPresent();
        }

        @Test
        void refusesARateInADifferentUnitFromTheQuantity() {
            assertThatThrownBy(
                            () -> BillItem.build(input("E423.1", "excavation", rate(4.85, Unit.M2), q(100, Unit.M3))))
                    .hasMessageContaining("different units");
        }

        @Test
        void refusesAnEmptyDescription() {
            assertThatThrownBy(() -> BillItem.build(input("E423.1", "   ", rate(1, Unit.M3), q(1, Unit.M3))))
                    .hasMessageContaining("needs a description");
        }

        @Test
        void refusesANegativeBilledQuantity() {
            assertThatThrownBy(() -> BillItem.build(input("E423.1", "excavation", rate(1, Unit.M3), q(-1, Unit.M3))))
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        void allowsANilQuantityItemWhichABillOftenCarries() {
            BillItem nil =
                    BillItem.build(input("E423.9", "rock excavation, provisional", rate(28, Unit.M3), q(0, Unit.M3))
                            .withKind(BillItemKind.PROVISIONAL));
            assertThat(nil.isNilQuantity()).isTrue();
            assertThat(nil.billedValue().minor()).isZero();
        }

        @Test
        void refusesALumpItemMeasuredInAnythingButASum() {
            assertThatThrownBy(
                            () -> BillItem.build(input("A213.1", "establish site", rate(50_000, Unit.M3), q(1, Unit.M3))
                                    .withKind(BillItemKind.LUMP)))
                    .hasMessageContaining("billed as a sum");
        }

        @Test
        void refusesASumItemClaimingToBeMeasured() {
            assertThatThrownBy(() ->
                            BillItem.build(input("A213.1", "establish site", rate(50_000, Unit.SUM), q(1, Unit.SUM))
                                    .withKind(BillItemKind.MEASURED)))
                    .hasMessageContaining("lump or a prime cost item");
        }

        @Test
        void acceptsALumpSumItem() {
            BillItem lump = BillItem.build(
                    input("A213.1", "establish and remove site accommodation", rate(48_500, Unit.SUM), q(1, Unit.SUM))
                            .withKind(BillItemKind.LUMP));
            assertThat(lump.isLump()).isTrue();
            assertThat(lump.billedValue().major()).isEqualTo(48_500);
        }
    }

    @Nested
    class BilledValue {

        @Test
        void isTheRateTimesTheBilledQuantity() {
            assertThat(EXCAVATION.billedValue().major()).isEqualTo(60_140);
        }

        @Test
        void keepsTheCurrencyOfTheRate() {
            assertThat(EXCAVATION.billedValue().currency()).isEqualTo(Currency.GBP);
        }
    }

    @Nested
    class ItemKinds {

        private final BillItem provisional =
                BillItem.build(input("E423.9", "rock excavation", rate(28, Unit.M3), q(500, Unit.M3))
                        .withKind(BillItemKind.PROVISIONAL));

        private final BillItem primeCost = BillItem.build(input(
                        "A253.1",
                        "prime cost item, statutory undertaker diversion",
                        rate(120_000, Unit.SUM),
                        q(1, Unit.SUM))
                .withKind(BillItemKind.PRIME_COST));

        private final BillItem star = BillItem.build(
                input("X400.1", "agreed rate for hand excavation around services", rate(94, Unit.M3), q(40, Unit.M3))
                        .withKind(BillItemKind.STAR));

        @Test
        void treatsMeasuredAndStarItemsAsMeasured() {
            assertThat(EXCAVATION.isMeasured()).isTrue();
            assertThat(star.isMeasured()).isTrue();
            assertThat(provisional.isMeasured()).isFalse();
        }

        @Test
        void treatsProvisionalAndPrimeCostAsNotYetEarnable() {
            assertThat(provisional.isProvisional()).isTrue();
            assertThat(primeCost.isProvisional()).isTrue();
            assertThat(provisional.isEarnable()).isFalse();
            assertThat(primeCost.isEarnable()).isFalse();
        }

        @Test
        void treatsAMeasuredAndALumpItemAsEarnable() {
            assertThat(EXCAVATION.isEarnable()).isTrue();
            assertThat(star.isEarnable()).isTrue();
        }
    }

    @Nested
    class Describe {

        @Test
        void readsLikeABillLine() {
            assertThat(EXCAVATION.describe())
                    .isEqualTo("E423.1 excavation for cuttings, maximum depth 2 to 5 m 12400.00 m3 at £4.85/m3");
        }

        @Test
        void showsTheChainageWhenTheItemHasOne() {
            BillItem item = BillItem.build(input("R712.1", "sub base", rate(11.4, Unit.M2), q(4000, Unit.M2))
                    .withChainage(range("12+000", "12+800")));
            assertThat(item.describe()).contains("[12+000.000 to 12+800.000]");
        }
    }

    @Nested
    class ReQuantify {

        @Test
        void makesACopyAtANewBilledQuantity() {
            BillItem revised = EXCAVATION.reQuantify(13_000);
            assertThat(revised.quantity().milli()).isEqualTo(13_000_000);
            assertThat(revised.code()).isEqualTo(EXCAVATION.code());
            assertThat(revised.rate()).isEqualTo(EXCAVATION.rate());
            assertThat(EXCAVATION.quantity().milli()).isEqualTo(12_400_000);
        }

        @Test
        void stillRefusesANegativeQuantity() {
            assertThatThrownBy(() -> EXCAVATION.reQuantify(-1)).isInstanceOf(BadInputException.class);
        }
    }
}
