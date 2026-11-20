package io.github.rkaushikdev.chainageworks.domain.bill;

import static io.github.rkaushikdev.chainageworks.support.BillFixtures.item;
import static io.github.rkaushikdev.chainageworks.support.BillFixtures.sampleBill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.DateRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.support.BillFixtures.ItemSpec;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BillHistoryTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static final BillRevision REV_A =
            new BillRevision("Rev A", d("2025-01-01"), sampleBill(), "contract bill at award");

    private static final BillRevision REV_B = new BillRevision(
            "Rev B",
            d("2025-04-01"),
            sampleBill().withItem(item(ItemSpec.of("E423.1", 5.2, 12_400, Unit.M3))),
            "excavation rate agreed after the ground investigation");

    private static final BillRevision REV_C = new BillRevision(
            "Rev C",
            d("2025-07-01"),
            REV_B.bill().withItem(item(ItemSpec.of("X400.1", 94, 40, Unit.M3))),
            "hand excavation around services added");

    private static final BillHistory HISTORY = BillHistory.build(List.of(REV_C, REV_A, REV_B));

    @Nested
    class Build {

        @Test
        void sortsRevisionsByTheDateTheyTakeEffect() {
            assertThat(HISTORY.revisions().stream().map(BillRevision::reference))
                    .containsExactly("Rev A", "Rev B", "Rev C");
            assertThat(HISTORY.count()).isEqualTo(3);
        }

        @Test
        void refusesAnEmptyHistory() {
            assertThatThrownBy(() -> BillHistory.build(List.of())).hasMessageContaining("at least one revision");
        }

        @Test
        void refusesTwoRevisionsTakingEffectOnTheSameDay() {
            assertThatThrownBy(() -> BillHistory.build(List.of(REV_A, REV_B.withEffectiveFrom(d("2025-01-01")))))
                    .hasMessageContaining("same day");
        }

        @Test
        void refusesTwoRevisionsSharingAReference() {
            assertThatThrownBy(() -> BillHistory.build(List.of(REV_A, REV_B.withReference("Rev A"))))
                    .hasMessageContaining("share a reference");
        }

        @Test
        void namesTheFirstAndTheLatest() {
            assertThat(HISTORY.first().reference()).isEqualTo("Rev A");
            assertThat(HISTORY.latest().reference()).isEqualTo("Rev C");
        }
    }

    @Nested
    class AsAt {

        @Test
        void takesTheRevisionInForceOnTheDay() {
            assertThat(HISTORY.asAt(d("2025-01-01")).reference()).isEqualTo("Rev A");
            assertThat(HISTORY.asAt(d("2025-03-31")).reference()).isEqualTo("Rev A");
            assertThat(HISTORY.asAt(d("2025-04-01")).reference()).isEqualTo("Rev B");
            assertThat(HISTORY.asAt(d("2025-06-30")).reference()).isEqualTo("Rev B");
            assertThat(HISTORY.asAt(d("2025-12-31")).reference()).isEqualTo("Rev C");
        }

        @Test
        void refusesADateBeforeAnyRevisionWasInForce() {
            assertThatThrownBy(() -> HISTORY.asAt(d("2024-12-31")))
                    .hasMessageContaining("no bill revision was in force");
            assertThat(HISTORY.hasRevisionAsAt(d("2024-12-31"))).isFalse();
            assertThat(HISTORY.hasRevisionAsAt(d("2025-01-01"))).isTrue();
        }
    }

    @Nested
    class RateAsAt {

        private final ItemCode excavation = ItemCode.parse("E423.1");

        @Test
        void givesTheRateThatWasInForceNotTheLatestOne() {
            assertThat(HISTORY.rateAsAt(d("2025-03-31"), excavation).major()).isEqualTo(4.85);
            assertThat(HISTORY.rateAsAt(d("2025-04-01"), excavation).major()).isEqualTo(5.2);
        }

        @Test
        void valuesAMarchMeasurementAtTheMarchRateAfterTheBillHasMovedOn() {
            assertThat(HISTORY.billAsAt(d("2025-03-01")).total().major()).isEqualTo(312_190);
            assertThat(HISTORY.billAsAt(d("2025-05-01")).total().major()).isEqualTo(312_190 - 60_140 + 64_480);
        }

        @Test
        void refusesAnItemTheBillDidNotCarryOnThatDate() {
            ItemCode star = ItemCode.parse("X400.1");
            assertThatThrownBy(() -> HISTORY.rateAsAt(d("2025-05-01"), star)).isInstanceOf(NotFoundException.class);
            assertThat(HISTORY.rateAsAt(d("2025-08-01"), star).major()).isEqualTo(94);
        }

        @Test
        void readsTheWholeItemNotOnlyTheRate() {
            assertThat(HISTORY.itemAsAt(d("2025-01-15"), excavation).description())
                    .contains("2 to 5 m");
        }
    }

    @Nested
    class RevisionsIn {

        @Test
        void findsRevisionsThatTakeEffectInsideAPeriod() {
            assertThat(HISTORY.revisionsIn(DateRange.month(2025, 4)).stream().map(BillRevision::reference))
                    .containsExactly("Rev B");
        }

        @Test
        void findsNothingInAQuietMonth() {
            assertThat(HISTORY.revisionsIn(DateRange.month(2025, 5))).isEmpty();
        }
    }

    @Nested
    class Find {

        @Test
        void findsByReference() {
            assertThat(HISTORY.find("Rev B").note()).contains("ground investigation");
        }

        @Test
        void throwsForAReferenceNothingCarries() {
            assertThatThrownBy(() -> HISTORY.find("Rev Z")).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class WithRevision {

        @Test
        void addsARevisionAndLeavesTheOriginalAlone() {
            BillHistory next = HISTORY.withRevision(new BillRevision(
                    "Rev D", d("2025-10-01"), sampleBill(), "reissued after the variation account was agreed"));
            assertThat(next.count()).isEqualTo(4);
            assertThat(HISTORY.count()).isEqualTo(3);
            assertThat(next.latest().reference()).isEqualTo("Rev D");
        }

        @Test
        void stillRefusesAClash() {
            assertThatThrownBy(() -> HISTORY.withRevision(REV_A.withReference("Rev D")))
                    .hasMessageContaining("same day");
        }
    }

    @Nested
    class Describe {

        @Test
        void readsLikeATransmittalLine() {
            assertThat(REV_A.describe()).isEqualTo("Rev A from 2025-01-01: contract bill at award");
        }
    }
}
