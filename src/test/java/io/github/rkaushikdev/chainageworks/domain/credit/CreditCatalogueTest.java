package io.github.rkaushikdev.chainageworks.domain.credit;

import static io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue.BULK_EARTHWORKS;
import static io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue.DRAINAGE_STRUCTURE;
import static io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue.PAVEMENT;
import static io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue.PIPE_RUN;
import static io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue.SINGLE_STEP;
import static io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue.SITE_SETUP;
import static io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue.STANDARD_PROFILES;
import static io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue.STRUCTURAL_CONCRETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CreditCatalogueTest {

    private static final ProfileRegistry REGISTRY = ProfileRegistry.standard();

    private static Percent p(double value) {
        return Percent.of(value);
    }

    private static double earned(CreditProfile profile, StepProgress... progress) {
        return profile.earnedFraction(List.of(progress)).value();
    }

    @Nested
    class TheStandardProfiles {

        @Test
        void carriesTheSevenACivilJobKeepsNeeding() {
            assertThat(STANDARD_PROFILES.stream().map(CreditProfile::id))
                    .containsExactly(
                            "single-step",
                            "pipe-run",
                            "bulk-earthworks",
                            "structural-concrete",
                            "pavement",
                            "drainage-structure",
                            "site-setup");
        }

        @Test
        void givesEveryOneOfThemWeightsThatAddToAHundred() {
            for (CreditProfile profile : STANDARD_PROFILES) {
                List<StepProgress> all = profile.steps().stream()
                        .map(s -> new StepProgress(s.id(), p(100)))
                        .toList();
                assertThat(profile.earnedFraction(all).value()).isEqualTo(100);
            }
        }

        @Test
        void paysTheSingleStepProfileNothingUntilItIsFinished() {
            assertThat(earned(SINGLE_STEP)).isZero();
            assertThat(earned(SINGLE_STEP, new StepProgress("complete", p(50)))).isEqualTo(50);
        }

        @Test
        void ordersThePipeRunSoNothingCanBeClaimedOutOfSequence() {
            assertThat(earned(PIPE_RUN, new StepProgress("backfill", p(100)))).isZero();
        }

        @Test
        void letsEarthworksStripTopsoilBeforeAnythingElseHasHappened() {
            assertThat(earned(BULK_EARTHWORKS, new StepProgress("strip", p(100))))
                    .isEqualTo(10);
        }

        @Test
        void letsConcreteFormworkAndReinforcementRunInEitherOrder() {
            assertThat(earned(STRUCTURAL_CONCRETE, new StepProgress("reinforcement", p(100))))
                    .isEqualTo(25);
        }

        @Test
        void buildsAPavementFromTheBottomUp() {
            assertThat(earned(PAVEMENT, new StepProgress("surface", p(100)))).isZero();
        }

        @Test
        void letsSiteSetupEarnItsMaintenanceWithoutDemobilising() {
            assertThat(earned(SITE_SETUP, new StepProgress("mobilise", p(100)), new StepProgress("maintain", p(50))))
                    .isEqualTo(75);
        }
    }

    @Nested
    class TheRegistry {

        @Test
        void holdsEveryStandardProfile() {
            assertThat(REGISTRY.registeredIds())
                    .containsExactly(
                            "bulk-earthworks",
                            "drainage-structure",
                            "pavement",
                            "pipe-run",
                            "single-step",
                            "site-setup",
                            "structural-concrete");
        }

        @Test
        void findsAProfileById() {
            assertThat(REGISTRY.find("pipe-run")).containsSame(PIPE_RUN);
            assertThat(REGISTRY.find("nope")).isEmpty();
            assertThat(REGISTRY.require("pavement")).isSameAs(PAVEMENT);
        }

        @Test
        void throwsForAnIdNothingCarries() {
            assertThatThrownBy(() -> REGISTRY.require("nope")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void refusesTwoProfilesSharingAnId() {
            assertThatThrownBy(() -> ProfileRegistry.build(List.of(PIPE_RUN, PIPE_RUN)))
                    .hasMessageContaining("share an id");
        }

        @Test
        void canBeBuiltEmptyAndGrown() {
            ProfileRegistry empty = ProfileRegistry.build(List.of());
            assertThat(empty.registeredIds()).isEmpty();
            assertThat(empty.withProfile(PIPE_RUN).registeredIds()).containsExactly("pipe-run");
        }

        @Test
        void replacesAProfileWithTheSameIdRatherThanDuplicatingIt() {
            CreditProfile custom = CreditProfile.build(
                    "pipe-run",
                    "contract specific pipe laying",
                    List.of(CreditStep.of("dig", "dig", p(50)), CreditStep.afterPrevious("lay", "lay", p(50))));
            ProfileRegistry next = REGISTRY.withProfile(custom);
            assertThat(next.registeredIds()).hasSize(7);
            assertThat(next.require("pipe-run").name()).isEqualTo("contract specific pipe laying");
            assertThat(REGISTRY.require("pipe-run").name()).isEqualTo("pipe laying");
        }
    }

    @Nested
    class DefaultFor {

        @Test
        void mapsTheClassesThatHaveAnObviousSequence() {
            assertThat(REGISTRY.defaultFor("E")).isSameAs(BULK_EARTHWORKS);
            assertThat(REGISTRY.defaultFor("F")).isSameAs(STRUCTURAL_CONCRETE);
            assertThat(REGISTRY.defaultFor("I")).isSameAs(PIPE_RUN);
            assertThat(REGISTRY.defaultFor("K")).isSameAs(DRAINAGE_STRUCTURE);
            assertThat(REGISTRY.defaultFor("R")).isSameAs(PAVEMENT);
            assertThat(REGISTRY.defaultFor("A")).isSameAs(SITE_SETUP);
        }

        @Test
        void ignoresTheCaseOfTheClassLetter() {
            assertThat(REGISTRY.defaultFor("e")).isSameAs(BULK_EARTHWORKS);
        }

        @Test
        void fallsBackToCompleteOrNotWhichNeverOverstatesProgress() {
            assertThat(REGISTRY.defaultFor("T")).isSameAs(SINGLE_STEP);
            assertThat(REGISTRY.defaultFor("X")).isSameAs(SINGLE_STEP);
        }
    }
}
