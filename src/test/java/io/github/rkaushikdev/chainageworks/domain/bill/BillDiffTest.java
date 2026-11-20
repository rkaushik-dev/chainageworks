package io.github.rkaushikdev.chainageworks.domain.bill;

import static io.github.rkaushikdev.chainageworks.support.BillFixtures.item;
import static io.github.rkaushikdev.chainageworks.support.BillFixtures.sampleBill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.bill.BillDiff.ChangeKind;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.support.BillFixtures.ItemSpec;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BillDiffTest {

    private static final Bill BASE = sampleBill();

    @Nested
    class Between {

        @Test
        void reportsNothingWhenTheBillsMatch() {
            BillDiff diff = BillDiff.between(BASE, sampleBill());
            assertThat(diff.isEmpty()).isTrue();
            assertThat(diff.netDelta().minor()).isZero();
        }

        @Test
        void reportsARateChangeAndWhatItIsWorth() {
            Bill after = BASE.withItem(item(ItemSpec.of("E423.1", 5.2, 12_400, Unit.M3)));
            BillDiff diff = BillDiff.between(BASE, after);
            assertThat(diff.changes()).hasSize(1);
            assertThat(diff.changes().get(0).kind()).isEqualTo(ChangeKind.RATE_CHANGED);
            assertThat(diff.netDelta().major()).isEqualTo(64_480 - 60_140);
            assertThat(diff.hasRateChanges()).isTrue();
        }

        @Test
        void reportsAQuantityChangeOnItsOwn() {
            Bill after = BASE.withItem(item(ItemSpec.of("E423.1", 4.85, 13_000, Unit.M3)));
            BillDiff diff = BillDiff.between(BASE, after);
            assertThat(diff.changes().get(0).kind()).isEqualTo(ChangeKind.QUANTITY_CHANGED);
            assertThat(diff.hasRateChanges()).isFalse();
        }

        @Test
        void reportsARateAndQuantityMoveAsOneChangeNotTwo() {
            Bill after = BASE.withItem(item(ItemSpec.of("E423.1", 5.2, 13_000, Unit.M3)));
            BillDiff diff = BillDiff.between(BASE, after);
            assertThat(diff.changes()).hasSize(1);
            assertThat(diff.changes().get(0).kind()).isEqualTo(ChangeKind.RATE_AND_QUANTITY_CHANGED);
            assertThat(diff.hasRateChanges()).isTrue();
        }

        @Test
        void reportsAnAddedItemAtItsFullValue() {
            Bill after = BASE.withItem(item(ItemSpec.of("X400.1", 94, 40, Unit.M3)));
            BillDiff diff = BillDiff.between(BASE, after);
            assertThat(diff.ofKind(ChangeKind.ADDED)).hasSize(1);
            assertThat(diff.netDelta().major()).isEqualTo(3760);
            assertThat(diff.changes().get(0).before()).isEmpty();
        }

        @Test
        void reportsARemovedItemAsANegative() {
            Bill after = BASE.withoutItem(ItemCode.parse("E423.9"));
            BillDiff diff = BillDiff.between(BASE, after);
            assertThat(diff.ofKind(ChangeKind.REMOVED)).hasSize(1);
            assertThat(diff.netDelta().major()).isEqualTo(-14_000);
            assertThat(diff.changes().get(0).after()).isEmpty();
        }

        @Test
        void reportsADescriptionChangeWithNoMoneyAttached() {
            Bill after = BASE.withItem(item(ItemSpec.of("E423.1", 4.85, 12_400, Unit.M3)
                    .described("excavation for cuttings, revised wording")));
            BillDiff diff = BillDiff.between(BASE, after);
            assertThat(diff.changes().get(0).kind()).isEqualTo(ChangeKind.DESCRIPTION_CHANGED);
            assertThat(diff.netDelta().minor()).isZero();
        }

        @Test
        void reportsAnItemThatBecameProvisional() {
            Bill after = BASE.withItem(item(ItemSpec.of("E423.1", 4.85, 12_400, Unit.M3)
                    .described("excavation for cuttings, maximum depth 2 to 5 m")
                    .kind(BillItemKind.PROVISIONAL)));
            BillDiff diff = BillDiff.between(BASE, after);
            assertThat(diff.changes().get(0).kind()).isEqualTo(ChangeKind.KIND_CHANGED);
        }

        @Test
        void reportsSeveralChangesInBillOrder() {
            Bill after = BASE.withItem(item(ItemSpec.of("E423.1", 5.2, 12_400, Unit.M3)));
            after = after.withoutItem(ItemCode.parse("R712.2"));
            after = after.withItem(item(ItemSpec.of("X400.1", 94, 40, Unit.M3)));
            BillDiff diff = BillDiff.between(BASE, after);
            assertThat(diff.changes().stream().map(c -> c.code().format()))
                    .containsExactly("E423.1", "R712.2", "X400.1");
            assertThat(diff.netDelta().major()).isEqualTo(4340 - 75_000 + 3760);
        }

        @Test
        void refusesTwoBillsInDifferentCurrencies() {
            Bill euro = Bill.build("B2", "euro bill", Currency.EUR, List.of());
            assertThatThrownBy(() -> BillDiff.between(BASE, euro)).hasMessageContaining("different currencies");
        }
    }

    @Nested
    class DescribeChange {

        @Test
        void readsTheChangeKindAsWords() {
            Bill after = BASE.withItem(item(ItemSpec.of("E423.1", 5.2, 13_000, Unit.M3)));
            BillDiff diff = BillDiff.between(BASE, after);
            assertThat(diff.changes().get(0).describe()).isEqualTo("E423.1 rate and quantity changed");
        }
    }
}
