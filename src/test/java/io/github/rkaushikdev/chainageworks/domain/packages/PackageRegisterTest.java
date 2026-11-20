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
import io.github.rkaushikdev.chainageworks.domain.packages.PackageRegister.Problem;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageRegister.ReconciliationLine;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.support.BillFixtures;
import io.github.rkaushikdev.chainageworks.support.PackageFixtures.PackageSpec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PackageRegisterTest {

    private static final PackageRegister REGISTER = PackageRegister.build(samplePackages());
    private static final Bill BILL = BillFixtures.sampleBill();

    private static long at(String text) {
        return Stations.parse(text);
    }

    private static ItemCode c(String text) {
        return ItemCode.parse(text);
    }

    private static List<String> ids(List<WorkPackage> packages) {
        return packages.stream().map(WorkPackage::id).toList();
    }

    private static List<String> show(List<ChainageRange> ranges) {
        return ranges.stream().map(ChainageRange::format).toList();
    }

    @Nested
    class Build {

        @Test
        void sortsByWhereThePackageStartsThenById() {
            assertThat(ids(REGISTER.packages())).containsExactly("WP-01", "WP-03", "WP-02");
        }

        @Test
        void refusesTwoPackagesSharingAnId() {
            assertThatThrownBy(() -> PackageRegister.build(List.of(
                            pkg(PackageSpec.of("WP-01", "0+000", "0+100", share("E423.1", 1, Unit.M3))),
                            pkg(PackageSpec.of("WP-01", "0+100", "0+200", share("E423.1", 1, Unit.M3))))))
                    .hasMessageContaining("share an id");
        }

        @Test
        void acceptsAnEmptyRegister() {
            assertThat(PackageRegister.build(List.of()).packages()).isEmpty();
        }
    }

    @Nested
    class LookingPackagesUp {

        @Test
        void findsById() {
            assertThat(REGISTER.find("WP-02").orElseThrow().name()).containsPattern("12\\+800 to 13\\+600");
            assertThat(REGISTER.find("WP-99")).isEmpty();
            assertThat(REGISTER.require("WP-01").id()).isEqualTo("WP-01");
        }

        @Test
        void throwsForAnIdNothingCarries() {
            assertThatThrownBy(() -> REGISTER.require("WP-99")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void listsTheSections() {
            assertThat(REGISTER.sections()).containsExactly("section 1", "section 2");
            assertThat(ids(REGISTER.inSection("section 2"))).containsExactly("WP-03");
        }

        @Test
        void findsEveryPackageCoveringAStation() {
            assertThat(ids(REGISTER.at(at("12+400")))).containsExactly("WP-01", "WP-03");
            assertThat(ids(REGISTER.at(at("13+000")))).containsExactly("WP-03", "WP-02");
            assertThat(REGISTER.at(at("14+000"))).isEmpty();
        }

        @Test
        void findsEveryPackageEarningAgainstAnItem() {
            assertThat(ids(REGISTER.forCode(c("E423.1")))).containsExactly("WP-01", "WP-02");
            assertThat(REGISTER.forCode(c("F624.1"))).isEmpty();
        }
    }

    @Nested
    class AllocatedQuantity {

        @Test
        void addsTheSharesAcrossEveryPackage() {
            assertThat(REGISTER.allocatedQuantity(c("E423.1")).orElseThrow().format())
                    .isEqualTo("12000.00 m3");
            assertThat(REGISTER.allocatedQuantity(c("E532.1")).orElseThrow().format())
                    .isEqualTo("9800.00 m3");
        }

        @Test
        void givesNothingForAnItemNothingIsAllocatedAgainst() {
            assertThat(REGISTER.allocatedQuantity(c("F624.1"))).isEmpty();
        }
    }

    @Nested
    class ReconcileWith {

        private final List<ReconciliationLine> lines = REGISTER.reconcileWith(BILL);

        private ReconciliationLine line(List<ReconciliationLine> from, String code) {
            return from.stream()
                    .filter(l -> l.code().format().equals(code))
                    .findFirst()
                    .orElseThrow();
        }

        @Test
        void reportsEveryProblemInBillOrder() {
            assertThat(lines.stream().map(l -> l.code().format())).containsExactly("A213.1", "E423.1", "F624.1");
        }

        @Test
        void reportsTheEarthworksItemThatIsShortOfTheBilledQuantity() {
            ReconciliationLine shortLine = line(lines, "E423.1");
            assertThat(shortLine.problem()).isEqualTo(Problem.UNDER_ALLOCATED);
            assertThat(shortLine.message()).isEqualTo("E423.1 is allocated 12000.00 m3 of a billed 12400.00 m3");
        }

        @Test
        void reportsItemsNoPackageEarnsAgainst() {
            List<ReconciliationLine> orphans = lines.stream()
                    .filter(l -> l.problem() == Problem.NOT_ALLOCATED)
                    .toList();
            assertThat(orphans.stream().map(l -> l.code().format())).containsExactly("A213.1", "F624.1");
            assertThat(orphans.get(0).message()).contains("no package earns against it");
        }

        @Test
        void leavesProvisionalItemsOutBecauseNothingMayEarnAgainstThemYet() {
            assertThat(lines.stream().anyMatch(l -> l.code().format().equals("E423.9")))
                    .isFalse();
        }

        @Test
        void saysNothingAboutAnItemThatBalancesExactly() {
            assertThat(lines.stream().anyMatch(l -> l.code().format().equals("E532.1")))
                    .isFalse();
            assertThat(lines.stream().anyMatch(l -> l.code().format().equals("R712.1")))
                    .isFalse();
        }

        @Test
        void reportsOverAllocation() {
            PackageRegister greedy = PackageRegister.build(
                    List.of(pkg(PackageSpec.of("WP-10", "12+000", "12+800", share("E423.1", 20_000, Unit.M3)))));
            ReconciliationLine over = line(greedy.reconcileWith(BILL), "E423.1");
            assertThat(over.problem()).isEqualTo(Problem.OVER_ALLOCATED);
            assertThat(over.message()).isEqualTo("E423.1 is allocated 20000.00 m3 against a billed 12400.00 m3");
        }

        @Test
        void reportsAnAllocationAgainstAnItemTheBillDropped() {
            PackageRegister stale = PackageRegister.build(
                    List.of(pkg(PackageSpec.of("WP-11", "0+000", "0+100", share("Z999.1", 1, Unit.M3)))));
            ReconciliationLine unknown = line(stale.reconcileWith(BILL), "Z999.1");
            assertThat(unknown.problem()).isEqualTo(Problem.UNKNOWN_ITEM);
            assertThat(unknown.billed()).isEmpty();
        }
    }

    @Nested
    class UncoveredStretches {

        @Test
        void findsTheLengthBeyondTheLastPackage() {
            assertThat(show(REGISTER.uncoveredStretches(ChainageRange.of(at("12+000"), at("14+000")), "A1")))
                    .containsExactly("13+600.000 to 14+000.000");
        }

        @Test
        void findsAHoleInTheMiddle() {
            PackageRegister holed = PackageRegister.build(List.of(
                    pkg(PackageSpec.of("WP-20", "0+000", "0+100", share("E423.1", 1, Unit.M3))),
                    pkg(PackageSpec.of("WP-21", "0+300", "0+400", share("E423.1", 1, Unit.M3)))));
            assertThat(show(holed.uncoveredStretches(ChainageRange.of(at("0+000"), at("0+400")), "A1")))
                    .containsExactly("0+100.000 to 0+300.000");
        }

        @Test
        void findsNothingWhenThePackagesCoverTheLot() {
            assertThat(REGISTER.uncoveredStretches(ChainageRange.of(at("12+000"), at("13+600")), "A1"))
                    .isEmpty();
        }

        @Test
        void ignoresPackagesOnAnotherAlignment() {
            assertThat(show(REGISTER.uncoveredStretches(ChainageRange.of(at("12+000"), at("13+600")), "A2")))
                    .containsExactly("12+000.000 to 13+600.000");
        }

        @Test
        void measuresTheCoveredLengthOnceCountingOverlapOnce() {
            assertThat(REGISTER.coveredExtent("A1")).isEqualTo(1_600_000);
            assertThat(REGISTER.coveredExtent("A2")).isZero();
        }
    }

    @Nested
    class Clashes {

        @Test
        void findsNothingWhenPackagesThatOverlapShareNoItem() {
            assertThat(REGISTER.clashes()).isEmpty();
        }

        @Test
        void findsAPairThatOverlapsAndSharesAnItem() {
            List<WorkPackage> packages = new ArrayList<>(samplePackages());
            packages.add(pkg(PackageSpec.of("WP-30", "12+400", "13+000", share("E423.1", 100, Unit.M3))));
            PackageRegister clashing = PackageRegister.build(packages);
            assertThat(clashing.clashes().stream()
                            .map(cl -> cl.first().id() + "/" + cl.second().id()))
                    .containsExactly("WP-01/WP-30", "WP-30/WP-02");
        }
    }
}
