package io.github.rkaushikdev.chainageworks.domain.credit;

import static io.github.rkaushikdev.chainageworks.domain.credit.CreditStep.afterPrevious;
import static io.github.rkaushikdev.chainageworks.domain.credit.CreditStep.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CreditProfileTest {

    private static Percent p(double value) {
        return Percent.of(value);
    }

    private static final CreditProfile PIPE_RUN = CreditProfile.build(
            "pipe-run",
            "pipe laying",
            List.of(
                    of("excavate", "excavate trench", p(25)),
                    afterPrevious("bed", "lay bedding", p(10)),
                    afterPrevious("lay", "lay pipe", p(30)),
                    afterPrevious("backfill", "backfill and compact", p(25)),
                    afterPrevious("test", "pressure test", p(10))));

    private static final CreditProfile LOOSE =
            CreditProfile.build("loose", "independent steps", List.of(of("a", "a", p(40)), of("b", "b", p(60))));

    private static List<StepProgress> done(String... ids) {
        return Arrays.stream(ids).map(id -> new StepProgress(id, p(100))).toList();
    }

    private static double earned(CreditProfile profile, List<StepProgress> progress) {
        return profile.earnedFraction(progress).value();
    }

    @Nested
    class Build {

        @Test
        void keepsTheStepsInTheOrderGiven() {
            assertThat(PIPE_RUN.steps().stream().map(CreditStep::id))
                    .containsExactly("excavate", "bed", "lay", "backfill", "test");
        }

        @Test
        void defaultsRequiresPreviousToFalse() {
            assertThat(PIPE_RUN.steps().get(0).requiresPrevious()).isFalse();
            assertThat(PIPE_RUN.steps().get(1).requiresPrevious()).isTrue();
        }

        @Test
        void refusesAProfileWithNoSteps() {
            assertThatThrownBy(() -> CreditProfile.build("x", "x", List.of()))
                    .hasMessageContaining("at least one step");
        }

        @Test
        void refusesWeightsThatDoNotAddToAHundred() {
            assertThatThrownBy(() -> CreditProfile.build("x", "x", List.of(of("a", "a", p(40)), of("b", "b", p(40)))))
                    .hasMessageContaining("add to 100 percent");
        }

        @Test
        void refusesWeightsThatAddToMoreThanAHundred() {
            assertThatThrownBy(() -> CreditProfile.build("x", "x", List.of(of("a", "a", p(60)), of("b", "b", p(60)))))
                    .hasMessageContaining("add to 100 percent");
        }

        @Test
        void acceptsAwkwardWeightsThatStillAddUpExactly() {
            CreditProfile thirds = CreditProfile.build(
                    "thirds",
                    "thirds",
                    List.of(of("a", "a", p(33.33)), of("b", "b", p(33.33)), of("c", "c", p(33.34))));
            assertThat(thirds.steps()).hasSize(3);
        }

        @Test
        void refusesTwoStepsSharingAnId() {
            assertThatThrownBy(() ->
                            CreditProfile.build("x", "x", List.of(of("a", "first", p(50)), of("a", "second", p(50)))))
                    .hasMessageContaining("share an id");
        }

        @Test
        void refusesANegativeWeight() {
            assertThatThrownBy(() -> CreditProfile.build("x", "x", List.of(of("a", "a", p(-10)), of("b", "b", p(110)))))
                    .hasMessageContaining("cannot be negative");
        }
    }

    @Nested
    class LookingStepsUp {

        @Test
        void findsByIdAndReportsThePosition() {
            assertThat(PIPE_RUN.findStep("lay").orElseThrow().name()).isEqualTo("lay pipe");
            assertThat(PIPE_RUN.stepIndex("lay")).isEqualTo(2);
            assertThat(PIPE_RUN.stepIndex("nope")).isEqualTo(-1);
            assertThat(PIPE_RUN.findStep("nope")).isEmpty();
        }

        @Test
        void throwsFromTheStrictLookup() {
            assertThatThrownBy(() -> PIPE_RUN.requireStep("nope")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void addsUpTheWeightToAStep() {
            assertThat(PIPE_RUN.cumulativeWeight("excavate").value()).isEqualTo(25);
            assertThat(PIPE_RUN.cumulativeWeight("lay").value()).isEqualTo(65);
            assertThat(PIPE_RUN.cumulativeWeight("test").value()).isEqualTo(100);
            assertThatThrownBy(() -> PIPE_RUN.cumulativeWeight("nope")).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class EarnedFraction {

        @Test
        void earnsNothingForNothingDone() {
            assertThat(earned(PIPE_RUN, List.of())).isZero();
        }

        @Test
        void earnsTheFirstStepOnItsOwn() {
            assertThat(earned(PIPE_RUN, done("excavate"))).isEqualTo(25);
        }

        @Test
        void addsTheStepsAsTheyAreFinishedInOrder() {
            assertThat(earned(PIPE_RUN, done("excavate", "bed"))).isEqualTo(35);
            assertThat(earned(PIPE_RUN, done("excavate", "bed", "lay"))).isEqualTo(65);
        }

        @Test
        void earnsEverythingWhenEveryStepIsFinished() {
            assertThat(earned(PIPE_RUN, done("excavate", "bed", "lay", "backfill", "test")))
                    .isEqualTo(100);
        }

        @Test
        void creditsPartOfAStep() {
            assertThat(earned(PIPE_RUN, List.of(new StepProgress("excavate", p(60)))))
                    .isEqualTo(15);
        }

        @Test
        void holdsBackAStepWhosePredecessorsAreShort() {
            List<StepProgress> progress =
                    List.of(new StepProgress("excavate", p(50)), new StepProgress("backfill", p(100)));
            assertThat(earned(PIPE_RUN, progress)).isEqualTo(12.5);
            assertThat(PIPE_RUN.blockedSteps(progress).stream().map(CreditStep::id))
                    .containsExactly("backfill");
        }

        @Test
        void letsAnIndependentStepEarnOutOfOrder() {
            assertThat(earned(LOOSE, done("b"))).isEqualTo(60);
            assertThat(LOOSE.blockedSteps(done("b"))).isEmpty();
        }

        @Test
        void clampsAFractionOverAHundredRatherThanOverpaying() {
            assertThat(earned(PIPE_RUN, List.of(new StepProgress("excavate", p(150)))))
                    .isEqualTo(25);
        }

        @Test
        void treatsANegativeFractionAsNothing() {
            assertThat(earned(PIPE_RUN, List.of(new StepProgress("excavate", p(-20)))))
                    .isZero();
        }

        @Test
        void ignoresProgressAgainstAStepTheProfileDoesNotHave() {
            assertThat(earned(PIPE_RUN, List.of(new StepProgress("paint", p(100)))))
                    .isZero();
        }
    }

    @Nested
    class NextStepAndIsComplete {

        @Test
        void namesTheFirstUnfinishedStep() {
            assertThat(PIPE_RUN.nextStep(List.of()).orElseThrow().id()).isEqualTo("excavate");
            assertThat(PIPE_RUN.nextStep(done("excavate")).orElseThrow().id()).isEqualTo("bed");
        }

        @Test
        void namesAPartlyDoneStepAsTheNextOne() {
            assertThat(PIPE_RUN.nextStep(List.of(new StepProgress("excavate", p(90))))
                            .orElseThrow()
                            .id())
                    .isEqualTo("excavate");
        }

        @Test
        void reportsNothingLeftWhenEverythingIsDone() {
            List<StepProgress> all = done("excavate", "bed", "lay", "backfill", "test");
            assertThat(PIPE_RUN.nextStep(all)).isEmpty();
            assertThat(PIPE_RUN.isComplete(all)).isTrue();
            assertThat(PIPE_RUN.isComplete(done("excavate"))).isFalse();
        }
    }

    @Nested
    class Describe {

        @Test
        void listsTheStepsAndTheirWeights() {
            assertThat(LOOSE.describe()).isEqualTo("independent steps: a 40%, b 60%");
        }
    }
}
