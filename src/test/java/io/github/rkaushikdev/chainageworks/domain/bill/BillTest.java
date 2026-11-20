package io.github.rkaushikdev.chainageworks.domain.bill;

import static io.github.rkaushikdev.chainageworks.support.BillFixtures.bill;
import static io.github.rkaushikdev.chainageworks.support.BillFixtures.item;
import static io.github.rkaushikdev.chainageworks.support.BillFixtures.sampleBill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.support.BillFixtures.ItemSpec;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BillTest {

    private static List<String> codes(Bill b) {
        return b.items().stream().map(i -> i.code().format()).toList();
    }

    private static ItemCode c(String text) {
        return ItemCode.parse(text);
    }

    @Nested
    class Build {

        @Test
        void sortsTheItemsIntoBillOrderHoweverTheyArrive() {
            assertThat(codes(sampleBill()))
                    .containsExactly("A213.1", "E423.1", "E423.2", "E423.9", "E532.1", "F624.1", "R712.1", "R712.2");
        }

        @Test
        void refusesTheSameCodeTwice() {
            assertThatThrownBy(() -> bill(
                            List.of(ItemSpec.of("E423.1", 1, 1, Unit.M3), ItemSpec.of("E 4 2 3 . 1", 2, 2, Unit.M3))))
                    .hasMessageContaining("appears twice in one bill");
        }

        @Test
        void refusesAnItemPricedInAnotherCurrency() {
            BillItem euro = BillItem.build(BillItemInput.of(
                    c("E423.1"), "excavation", Rate.of(1, Currency.EUR, Unit.M3), Quantity.of(1, Unit.M3)));
            assertThatThrownBy(() -> Bill.build("B9", "mixed", Currency.GBP, List.of(euro)))
                    .hasMessageContaining("different currency from the bill");
        }

        @Test
        void acceptsAnEmptyBill() {
            Bill empty = Bill.empty("B0", "nothing yet", Currency.GBP);
            assertThat(empty.isEmpty()).isTrue();
            assertThat(empty.itemCount()).isZero();
            assertThat(empty.total().minor()).isZero();
            assertThat(empty.zero()).isEqualTo(Money.of(0, Currency.GBP));
        }
    }

    @Nested
    class LookingItemsUp {

        private final Bill b = sampleBill();

        @Test
        void findsAnItemByCode() {
            assertThat(b.find(c("E423.1")).orElseThrow().description()).contains("2 to 5 m");
            assertThat(b.has(c("E423.1"))).isTrue();
        }

        @Test
        void givesNothingForACodeTheBillDoesNotCarry() {
            assertThat(b.find(c("Z999.1"))).isEmpty();
            assertThat(b.has(c("Z999.1"))).isFalse();
        }

        @Test
        void throwsFromTheStrictLookup() {
            assertThatThrownBy(() -> b.require(c("Z999.1"))).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> b.require(c("Z999.1"))).hasMessageContaining("no bill item with id Z999.1");
        }

        @Test
        void doesNotMatchAHeadingAgainstAnItem() {
            assertThat(b.find(c("E42"))).isEmpty();
        }
    }

    @Nested
    class ItemsUnder {

        private final Bill b = sampleBill();

        @Test
        void collectsEverythingUnderAHeadingInBillOrder() {
            assertThat(b.itemsUnder(c("E42")).stream().map(i -> i.code().format()))
                    .containsExactly("E423.1", "E423.2", "E423.9");
        }

        @Test
        void collectsAWholeClass() {
            assertThat(b.itemsUnder(c("E"))).hasSize(4);
            assertThat(b.itemsUnder(c("R"))).hasSize(2);
        }

        @Test
        void collectsNothingForAHeadingNothingSitsUnder() {
            assertThat(b.itemsUnder(c("T"))).isEmpty();
        }
    }

    @Nested
    class Totals {

        private final Bill b = sampleBill();

        @Test
        void addsEveryItem() {
            assertThat(b.total().major()).isEqualTo(312_190);
        }

        @Test
        void leavesProvisionalMoneyOutOfTheMeasuredTotal() {
            assertThat(b.provisionalTotal().major()).isEqualTo(14_000);
            assertThat(b.measuredTotal().major()).isEqualTo(298_190);
        }

        @Test
        void totalsUnderAHeading() {
            assertThat(b.totalUnder(c("E42")).major()).isEqualTo(91_420);
            assertThat(b.totalUnder(c("R")).major()).isEqualTo(120_600);
            assertThat(b.totalUnder(c("T")).major()).isZero();
        }
    }

    @Nested
    class RollupByClass {

        private final List<Bill.ClassRollup> rollup = sampleBill().rollupByClass();

        @Test
        void givesOneLinePerClassInClassOrder() {
            assertThat(rollup.stream().map(Bill.ClassRollup::workClass)).containsExactly("A", "E", "F", "R");
        }

        @Test
        void namesTheClassAndCountsTheItems() {
            assertThat(rollup.get(1).name()).isEqualTo("earthworks");
            assertThat(rollup.get(1).items()).isEqualTo(4);
            assertThat(rollup.get(0).items()).isEqualTo(1);
        }

        @Test
        void valuesEachClass() {
            assertThat(rollup.stream().map(line -> line.value().major()))
                    .containsExactly(48_500.0, 122_290.0, 20_800.0, 120_600.0);
        }

        @Test
        void addsBackToTheBillTotal() {
            long sum = rollup.stream().mapToLong(line -> line.value().minor()).sum();
            assertThat(sum).isEqualTo(sampleBill().total().minor());
        }

        @Test
        void givesNothingForAnEmptyBill() {
            assertThat(Bill.empty("B0", "x", Currency.GBP).rollupByClass()).isEmpty();
        }
    }

    @Nested
    class WithItemAndWithoutItem {

        private final Bill b = sampleBill();

        @Test
        void addsAnItemAndKeepsTheOrder() {
            Bill next = b.withItem(item(ItemSpec.of("E511.1", 2.2, 100, Unit.M3)));
            assertThat(next.itemCount()).isEqualTo(9);
            assertThat(codes(next).get(4)).isEqualTo("E511.1");
        }

        @Test
        void replacesAnItemWithTheSameCodeRatherThanDuplicatingIt() {
            Bill next = b.withItem(item(ItemSpec.of("E423.1", 5, 12_400, Unit.M3)));
            assertThat(next.itemCount()).isEqualTo(8);
            assertThat(next.total().major()).isEqualTo(312_190 - 60_140 + 62_000);
        }

        @Test
        void leavesTheOriginalBillAlone() {
            b.withItem(item(ItemSpec.of("E511.1", 2.2, 100, Unit.M3)));
            assertThat(b.itemCount()).isEqualTo(8);
        }

        @Test
        void removesAnItem() {
            Bill next = b.withoutItem(c("E423.9"));
            assertThat(next.itemCount()).isEqualTo(7);
            assertThat(next.provisionalTotal().major()).isZero();
        }

        @Test
        void refusesToRemoveAnItemThatIsNotThere() {
            assertThatThrownBy(() -> b.withoutItem(c("Z999.1"))).isInstanceOf(NotFoundException.class);
        }
    }
}
