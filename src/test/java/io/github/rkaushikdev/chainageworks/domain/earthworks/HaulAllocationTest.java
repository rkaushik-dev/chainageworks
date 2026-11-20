package io.github.rkaushikdev.chainageworks.domain.earthworks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HaulAllocationTest {

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static Quantity m3(double amount) {
        return Quantity.of(amount, Unit.M3);
    }

    private static final Money FREE = Money.zero(Currency.GBP);
    private static final HaulSite CUT = new HaulSite("CUT-A", "cutting at 12+400", SiteKind.CUT, m3(6000), FREE);
    private static final HaulSite BORROW =
            new HaulSite("BOR-1", "borrow pit off the haul road", SiteKind.BORROW, m3(4000), gbp(3));
    private static final HaulSite EMBANKMENT =
            new HaulSite("EMB-B", "embankment at 13+200", SiteKind.FILL, m3(7000), FREE);
    private static final HaulSite TIP = new HaulSite("TIP-1", "licensed tip", SiteKind.TIP, m3(5000), gbp(9));
    private static final Money HAUL_RATE = gbp(1);

    private static HaulAllocation allocate(List<HaulSite> sources, List<HaulSite> destinations, HaulLink... links) {
        return HaulAllocation.allocate(new HaulAllocation.Input(sources, destinations, List.of(links), HAUL_RATE));
    }

    @Nested
    class Allocate {

        private final HaulAllocation allocation = allocate(
                List.of(CUT, BORROW),
                List.of(EMBANKMENT, TIP),
                new HaulLink("CUT-A", "EMB-B", 3),
                new HaulLink("CUT-A", "TIP-1", 1),
                new HaulLink("BOR-1", "EMB-B", 2),
                new HaulLink("BOR-1", "TIP-1", 4));

        @Test
        void prefersALongHaulFromTheCuttingOverAShortOneToTheTip() {
            Movement first = allocation.movements().get(0);
            assertThat(first.fromId()).isEqualTo("CUT-A");
            assertThat(first.toId()).isEqualTo("EMB-B");
            assertThat(first.volume().amount()).isEqualTo(6000);
        }

        @Test
        void buysFromTheBorrowPitOnlyForWhatTheCuttingCouldNotFill() {
            List<Movement> fromBorrow = allocation.movementsFrom("BOR-1");
            assertThat(fromBorrow).hasSize(1);
            assertThat(fromBorrow.get(0).toId()).isEqualTo("EMB-B");
            assertThat(fromBorrow.get(0).volume().amount()).isEqualTo(1000);
        }

        @Test
        void neverSendsMaterialToATipItDoesNotHaveTo() {
            assertThat(allocation.movementsTo("TIP-1")).isEmpty();
        }

        @Test
        void pricesTheHaulAndTheSiteChargesApart() {
            Movement first = allocation.movements().get(0);
            assertThat(first.haulCost().major()).isEqualTo(18_000);
            assertThat(first.siteCost().minor()).isZero();
            assertThat(first.cost().major()).isEqualTo(18_000);
        }

        @Test
        void addsTheBorrowPitChargeToItsOwnMovement() {
            Movement fromBorrow = allocation.movementsFrom("BOR-1").get(0);
            assertThat(fromBorrow.haulCost().major()).isEqualTo(2000);
            assertThat(fromBorrow.siteCost().major()).isEqualTo(3000);
            assertThat(fromBorrow.cost().major()).isEqualTo(5000);
        }

        @Test
        void addsTheMovementsUp() {
            assertThat(allocation.cost().major()).isEqualTo(23_000);
        }

        @Test
        void neverBuysMaterialInOrderToThrowItAway() {
            HaulAllocation wasteful = allocate(List.of(BORROW), List.of(TIP), new HaulLink("BOR-1", "TIP-1", 1));
            assertThat(wasteful.movements()).isEmpty();
            assertThat(wasteful.unplaced().amount()).isEqualTo(4000);
        }

        @Test
        void reportsWhatIsLeftOverAtEachEnd() {
            assertThat(allocation.unplaced().amount()).isEqualTo(3000);
            assertThat(allocation.unfilled().amount()).isZero();
            assertThat(allocation.isBalanced()).isFalse();
        }

        @Test
        void countsTheRoomLeftAtTheTipAsSpareRatherThanAShortfall() {
            assertThat(allocation.spareTipCapacity().amount()).isEqualTo(5000);
        }
    }

    @Nested
    class ABalancedJob {

        private final HaulAllocation balanced = allocate(
                List.of(CUT.withVolume(m3(5000))),
                List.of(EMBANKMENT.withVolume(m3(5000))),
                new HaulLink("CUT-A", "EMB-B", 2));

        @Test
        void placesEverythingAndFillsEverything() {
            assertThat(balanced.isBalanced()).isTrue();
            assertThat(balanced.unplaced().milli()).isZero();
            assertThat(balanced.unfilled().milli()).isZero();
        }

        @Test
        void givesTheHaulMomentPlantIsSizedOn() {
            assertThat(balanced.haulMoment()).isEqualTo(10_000);
        }
    }

    @Nested
    class ALinkNobodyCanUse {

        @Test
        void leavesOutALinkNamingASiteThatIsNotOnTheJob() {
            HaulAllocation allocation = allocate(
                    List.of(CUT),
                    List.of(EMBANKMENT),
                    new HaulLink("CUT-A", "EMB-B", 2),
                    new HaulLink("CUT-Z", "EMB-B", 1));
            assertThat(allocation.movements()).hasSize(1);
        }

        @Test
        void movesNothingWhenNothingIsLinkedAtAll() {
            HaulAllocation allocation = allocate(List.of(CUT), List.of(EMBANKMENT));
            assertThat(allocation.movements()).isEmpty();
            assertThat(allocation.unplaced().amount()).isEqualTo(6000);
            assertThat(allocation.unfilled().amount()).isEqualTo(7000);
            assertThat(allocation.spareTipCapacity().milli()).isZero();
        }
    }

    @Nested
    class RefusingBadSites {

        @Test
        void refusesASiteMeasuredInAnythingButCubicMetres() {
            assertThatThrownBy(() -> allocate(List.of(CUT.withVolume(Quantity.of(6000, Unit.T))), List.of(EMBANKMENT)))
                    .hasMessageContaining("cubic metres");
        }

        @Test
        void refusesANegativeVolume() {
            assertThatThrownBy(() -> allocate(List.of(CUT), List.of(EMBANKMENT.withVolume(m3(-1)))))
                    .hasMessageContaining("negative volume");
        }
    }

    @Nested
    class Empty {

        @Test
        void isAPlanThatMovesNothingAndCostsNothing() {
            HaulAllocation empty = HaulAllocation.empty(Currency.GBP);
            assertThat(empty.movements()).isEmpty();
            assertThat(empty.cost().minor()).isZero();
            assertThat(empty.isBalanced()).isTrue();
            assertThat(empty.haulMoment()).isZero();
        }
    }

    @Nested
    class DescribeMovement {

        @Test
        void saysWhatGoesWhereAndHowFar() {
            HaulAllocation allocation = allocate(
                    List.of(CUT.withVolume(m3(5000))),
                    List.of(EMBANKMENT.withVolume(m3(5000))),
                    new HaulLink("CUT-A", "EMB-B", 2));
            assertThat(allocation.movements().get(0).describe()).isEqualTo("5000 m3 from CUT-A to EMB-B, 2 km");
        }
    }
}
