package io.github.rkaushikdev.chainageworks.domain.packages;

import static io.github.rkaushikdev.chainageworks.support.PackageFixtures.pkg;
import static io.github.rkaushikdev.chainageworks.support.PackageFixtures.samplePackages;
import static io.github.rkaushikdev.chainageworks.support.PackageFixtures.share;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.support.BillFixtures;
import io.github.rkaushikdev.chainageworks.support.PackageFixtures.PackageSpec;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WorkPackageTest {

    private static final List<WorkPackage> SAMPLE = samplePackages();
    private static final WorkPackage EARTHWORKS_ONE = SAMPLE.get(0);
    private static final WorkPackage EARTHWORKS_TWO = SAMPLE.get(1);
    private static final WorkPackage PAVEMENT = SAMPLE.get(2);

    private static ItemCode c(String text) {
        return ItemCode.parse(text);
    }

    @Nested
    class Build {

        @Test
        void keepsWhatItWasGiven() {
            assertThat(EARTHWORKS_ONE.id()).isEqualTo("WP-01");
            assertThat(EARTHWORKS_ONE.profileId()).isEqualTo("bulk-earthworks");
            assertThat(EARTHWORKS_ONE.section()).isEqualTo("section 1");
            assertThat(EARTHWORKS_ONE.length()).isEqualTo(800_000);
        }

        @Test
        void defaultsTheSectionToNothing() {
            WorkPackage bare = WorkPackage.build(new WorkPackageInput(
                    "WP-99",
                    "odds and ends",
                    "A1",
                    ChainageRange.of(Stations.parse("0+000"), Stations.parse("0+100")),
                    null,
                    "single-step",
                    List.of(new PackageAllocation(c("E423.1"), Quantity.of(1, Unit.M3)))));
            assertThat(bare.section()).isEmpty();
        }

        @Test
        void refusesAPackageWithNoId() {
            assertThatThrownBy(() -> pkg(PackageSpec.of("  ", "0+000", "0+100", share("E423.1", 1, Unit.M3))))
                    .hasMessageContaining("needs an id");
        }

        @Test
        void refusesAPackageThatCanNeverEarn() {
            assertThatThrownBy(() -> pkg(PackageSpec.of("WP-X", "0+000", "0+100")))
                    .hasMessageContaining("can never earn");
        }

        @Test
        void refusesTheSameBillItemAllocatedTwice() {
            assertThatThrownBy(() -> pkg(PackageSpec.of(
                            "WP-X", "0+000", "0+100", share("E423.1", 1, Unit.M3), share("E 4 2 3 . 1", 2, Unit.M3))))
                    .hasMessageContaining("allocated twice");
        }

        @Test
        void refusesANegativeAllocation() {
            assertThatThrownBy(() -> pkg(PackageSpec.of("WP-X", "0+000", "0+100", share("E423.1", -1, Unit.M3))))
                    .hasMessageContaining("cannot be negative");
        }
    }

    @Nested
    class Allocations {

        @Test
        void findsOneByCode() {
            assertThat(EARTHWORKS_ONE
                            .findAllocation(c("E423.1"))
                            .orElseThrow()
                            .quantity()
                            .milli())
                    .isEqualTo(7_000_000);
            assertThat(EARTHWORKS_ONE.findAllocation(c("R712.1"))).isEmpty();
        }

        @Test
        void throwsFromTheStrictLookup() {
            assertThatThrownBy(() -> EARTHWORKS_ONE.requireAllocation(c("R712.1")))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void listsTheCodesAPackageEarnsAgainst() {
            assertThat(EARTHWORKS_TWO.allocatedCodes().stream().map(ItemCode::format))
                    .containsExactly("E423.1", "E423.2", "E532.1");
        }
    }

    @Nested
    class Covers {

        @Test
        void answersOnTheHalfOpenExtent() {
            assertThat(EARTHWORKS_ONE.covers(Stations.parse("12+000"))).isTrue();
            assertThat(EARTHWORKS_ONE.covers(Stations.parse("12+400"))).isTrue();
            assertThat(EARTHWORKS_ONE.covers(Stations.parse("12+800"))).isFalse();
            assertThat(EARTHWORKS_TWO.covers(Stations.parse("12+800"))).isTrue();
        }
    }

    @Nested
    class ClashesWith {

        @Test
        void isFalseForPackagesThatAbut() {
            assertThat(EARTHWORKS_ONE.clashesWith(EARTHWORKS_TWO)).isFalse();
        }

        @Test
        void isFalseWhenTheyOverlapButShareNoBillItem() {
            assertThat(EARTHWORKS_ONE.clashesWith(PAVEMENT)).isFalse();
        }

        @Test
        void isTrueWhenTheyOverlapAndShareAnItem() {
            WorkPackage overlapping = pkg(PackageSpec.of("WP-04", "12+400", "13+000", share("E423.1", 100, Unit.M3)));
            assertThat(EARTHWORKS_ONE.clashesWith(overlapping)).isTrue();
        }

        @Test
        void isFalseAgainstItself() {
            assertThat(EARTHWORKS_ONE.clashesWith(EARTHWORKS_ONE)).isFalse();
        }

        @Test
        void isFalseAcrossAlignments() {
            WorkPackage other = pkg(PackageSpec.of("WP-05", "12+400", "13+000", share("E423.1", 100, Unit.M3))
                    .alignment("A2"));
            assertThat(EARTHWORKS_ONE.clashesWith(other)).isFalse();
        }
    }

    @Nested
    class Budget {

        private final Bill bill = BillFixtures.sampleBill();

        @Test
        void valuesTheAllocationAtTheBillRates() {
            assertThat(EARTHWORKS_ONE.budget(bill).major()).isEqualTo(7000 * 4.85 + 5000 * 3.15);
        }

        @Test
        void valuesThePavementPackage() {
            assertThat(PAVEMENT.budget(bill).major()).isEqualTo(4000 * 11.4 + 4000 * 18.75);
        }

        @Test
        void refusesAnAllocationTheBillDoesNotCarry() {
            WorkPackage stray = pkg(PackageSpec.of("WP-06", "0+000", "0+100", share("Z999.1", 1, Unit.M3)));
            assertThatThrownBy(() -> stray.budget(bill)).isInstanceOf(NotFoundException.class);
        }

        @Test
        void refusesAnAllocationInTheWrongUnitForTheItem() {
            WorkPackage wrong = pkg(PackageSpec.of("WP-07", "0+000", "0+100", share("E423.1", 1, Unit.M2)));
            assertThatThrownBy(() -> wrong.budget(bill)).hasMessageContaining("different units");
        }
    }

    @Nested
    class Describe {

        @Test
        void readsLikeAPackageRegisterLine() {
            assertThat(EARTHWORKS_ONE.describe())
                    .isEqualTo("WP-01 earthworks, chainage 12+000 to 12+800 [12+000.000 to 12+800.000]");
        }
    }
}
