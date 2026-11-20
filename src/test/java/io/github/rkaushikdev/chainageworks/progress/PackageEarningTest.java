package io.github.rkaushikdev.chainageworks.progress;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleRecords;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.signOff;
import static io.github.rkaushikdev.chainageworks.support.PackageFixtures.pkg;
import static io.github.rkaushikdev.chainageworks.support.PackageFixtures.share;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageRegister;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.support.ContextFixtures.Overrides;
import io.github.rkaushikdev.chainageworks.support.PackageFixtures.PackageSpec;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PackageEarningTest {

    private static final ProgressContext CONTEXT = sampleContext();
    private static final WorkPackage WP01 = CONTEXT.register().require("WP-01");
    private static final LocalDate CUT_OFF = d("2025-03-31");

    private static ProgressContext withLaterSignOff() {
        List<SignOff> signOffs = new ArrayList<>(sampleContext().signOffs());
        signOffs.add(signOff("SO-03", "IP-E1", "WP-01", "12+400", "12+800", "2025-04-04"));
        return sampleContext(Overrides.NONE.withSignOffs(signOffs));
    }

    @Nested
    class Earn {

        private final PackageEarning earning = PackageEarning.earn(CONTEXT, WP01, CUT_OFF);

        @Test
        void earnsTheFractionTheRuleOfCreditWorkedOut() {
            assertThat(earning.fraction().value()).isEqualTo(32.5);
        }

        @Test
        void carriesTheStepDetailThroughForTheReport() {
            assertThat(earning.steps().stream().map(StepProgressDetail::stepId))
                    .containsExactly("strip", "excavate", "place", "trim");
        }

        @Test
        void takesThatFractionOfEachAllocatedQuantity() {
            assertThat(earning.allocations().stream()
                            .map(a -> a.earnedQuantity().format()))
                    .containsExactly("2275.00 m3", "1625.00 m3");
        }

        @Test
        void pricesItAtTheBillRatesInForceOnTheDay() {
            assertThat(earning.allocations().stream().map(a -> a.earnedValue().major()))
                    .containsExactly(11_033.75, 5118.75);
            assertThat(earning.earnedValue().major()).isEqualTo(16_152.5);
        }

        @Test
        void reportsTheBudgetThePackageWasSet() {
            assertThat(earning.budget().major()).isEqualTo(49_700);
        }

        @Test
        void earnsNothingBeforeAnythingWasBuilt() {
            PackageEarning early = PackageEarning.earn(CONTEXT, WP01, d("2025-02-28"));
            assertThat(early.fraction().value()).isZero();
            assertThat(early.earnedValue().minor()).isZero();
        }
    }

    @Nested
    class TheMeasurementCap {

        @Test
        void doesNotBiteWhileTheSurveyBookIsAheadOfTheCredit() {
            assertThat(PackageEarning.earn(CONTEXT, WP01, CUT_OFF).cappedAllocations())
                    .isEmpty();
        }

        @Test
        void holdsTheClaimDownToWhatWasActuallyMeasured() {
            ProgressContext thin = sampleContext(Overrides.NONE.withRecords(sampleRecords().stream()
                    .map(r -> r.id().equals("SR-03") ? r.withQuantity(Quantity.of(900, Unit.M3)) : r)
                    .toList()));
            PackageEarning earning = PackageEarning.earn(thin, WP01, CUT_OFF);
            PackageEarning.Allocation fill = earning.allocations().stream()
                    .filter(a -> a.code().format().equals("E532.1"))
                    .findFirst()
                    .orElseThrow();
            assertThat(fill.cappedByMeasurement()).isTrue();
            assertThat(fill.earnedQuantity().format()).isEqualTo("900.00 m3");
            assertThat(fill.earnedValue().major()).isEqualTo(2835);
        }

        @Test
        void neverEarnsMoreThanTheSurveyBookSupports() {
            ProgressContext nothing = sampleContext(Overrides.NONE.withRecords(List.of()));
            assertThat(PackageEarning.earn(nothing, WP01, CUT_OFF).earnedValue().minor())
                    .isZero();
        }
    }

    @Nested
    class ItemsThatCannotEarn {

        @Test
        void earnsNothingAgainstAProvisionalItemHoweverMuchWasBuilt() {
            PackageRegister provisional = PackageRegister.build(
                    List.of(pkg(PackageSpec.of("WP-40", "12+000", "12+800", share("E423.9", 500, Unit.M3))
                            .profile("bulk-earthworks"))));
            PackageEarning earning = PackageEarning.earn(
                    sampleContext().withRegister(provisional), provisional.require("WP-40"), CUT_OFF);
            assertThat(earning.allocations().get(0).notEarnable()).isTrue();
            assertThat(earning.earnedValue().minor()).isZero();
        }

        @Test
        void letsALumpSumEarnOnTheRuleOfCreditAloneWithNothingMeasured() {
            PackageRegister lump = PackageRegister.build(
                    List.of(pkg(PackageSpec.of("WP-41", "12+000", "12+800", share("A213.1", 1, Unit.SUM))
                            .profile("bulk-earthworks"))));
            ProgressContext context = sampleContext()
                    .withRegister(lump)
                    .withRecords(sampleRecords().stream()
                            .map(r -> r.withPackage("WP-41"))
                            .toList());
            PackageEarning earning = PackageEarning.earn(context, lump.require("WP-41"), CUT_OFF);
            assertThat(earning.fraction().value()).isEqualTo(10);
            assertThat(earning.earnedValue().major()).isEqualTo(4850);
        }

        @Test
        void refusesAnAllocationTheBillDoesNotCarryOnThatDate() {
            PackageRegister stray = PackageRegister.build(
                    List.of(pkg(PackageSpec.of("WP-42", "0+000", "0+100", share("Z999.1", 1, Unit.M3)))));
            assertThatThrownBy(() ->
                            PackageEarning.earn(sampleContext().withRegister(stray), stray.require("WP-42"), CUT_OFF))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class EarnAll {

        private final List<PackageEarning> all = PackageEarning.earnAll(CONTEXT, CUT_OFF);

        @Test
        void coversEveryPackageInTheRegister() {
            assertThat(all.stream().map(PackageEarning::packageId)).containsExactly("WP-01", "WP-03", "WP-02");
        }

        @Test
        void earnsNothingOnThePackagesNothingWasBookedAgainst() {
            assertThat(PackageEarning.find(all, "WP-02")
                            .orElseThrow()
                            .earnedValue()
                            .minor())
                    .isZero();
            assertThat(PackageEarning.find(all, "WP-03")
                            .orElseThrow()
                            .earnedValue()
                            .minor())
                    .isZero();
            assertThat(PackageEarning.find(all, "WP-99")).isEmpty();
        }

        @Test
        void addsToTheWholeJobTotal() {
            assertThat(PackageEarning.totalEarned(all, CONTEXT, CUT_OFF).major())
                    .isEqualTo(16_152.5);
        }
    }

    @Nested
    class TheCutOffDecidesTheAnswer {

        @Test
        void readsMoreAsTheSignOffsArrive() {
            ProgressContext later = withLaterSignOff();
            assertThat(PackageEarning.earn(later, WP01, CUT_OFF).fraction().value())
                    .isEqualTo(32.5);
            assertThat(PackageEarning.earn(later, WP01, d("2025-04-30"))
                            .fraction()
                            .value())
                    .isGreaterThan(32.5);
        }

        @Test
        void doesNotLetAnAprilSignOffChangeTheMarchCertificate() {
            assertThat(PackageEarning.earn(withLaterSignOff(), WP01, CUT_OFF)
                            .earnedValue()
                            .major())
                    .isEqualTo(16_152.5);
        }
    }
}
