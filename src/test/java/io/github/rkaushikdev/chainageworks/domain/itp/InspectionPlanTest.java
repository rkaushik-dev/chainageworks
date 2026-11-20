package io.github.rkaushikdev.chainageworks.domain.itp;

import static io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue.PIPE_RUN;
import static io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue.SINGLE_STEP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InspectionPlanTest {

    private static final InspectionPlan PLAN = InspectionPlan.build(
            "ITP-PIPE",
            "pipe laying inspection and test plan",
            "pipe-run",
            List.of(
                    InspectionPoint.of("IP-01", "trench formation level and width", PointType.HOLD, "excavate")
                            .withDiscipline("geotech")
                            .withReference("spec clause 505"),
                    InspectionPoint.of("IP-02", "bedding material grading", PointType.WITNESS, "bed"),
                    InspectionPoint.of("IP-03", "pipe line and level before backfill", PointType.HOLD, "lay"),
                    InspectionPoint.of("IP-04", "compaction test certificates", PointType.REVIEW, "backfill"),
                    InspectionPoint.of("IP-05", "pressure test witnessed", PointType.HOLD, "test")),
            PIPE_RUN);

    private static List<String> ids(List<InspectionPoint> points) {
        return points.stream().map(InspectionPoint::id).toList();
    }

    @Nested
    class Build {

        @Test
        void keepsThePointsItWasGiven() {
            assertThat(PLAN.points()).hasSize(5);
            assertThat(PLAN.points().get(0).reference()).isEqualTo("spec clause 505");
        }

        @Test
        void defaultsTheDisciplineToCivilAndTheReferenceToNothing() {
            assertThat(PLAN.points().get(1).discipline()).isEqualTo("civil");
            assertThat(PLAN.points().get(1).reference()).isEmpty();
        }

        @Test
        void refusesAPlanThatNamesADifferentRuleOfCredit() {
            assertThatThrownBy(() -> InspectionPlan.build("X", "x", "pipe-run", List.of(), SINGLE_STEP))
                    .hasMessageContaining("different rule of credit");
        }

        @Test
        void refusesTwoPointsSharingAnId() {
            assertThatThrownBy(() -> InspectionPlan.build(
                            "X",
                            "x",
                            "pipe-run",
                            List.of(
                                    InspectionPoint.of("IP-01", "a", PointType.HOLD, "excavate"),
                                    InspectionPoint.of("IP-01", "b", PointType.HOLD, "lay")),
                            PIPE_RUN))
                    .hasMessageContaining("share an id");
        }

        @Test
        void refusesAPointHungOnAStepTheProfileDoesNotHave() {
            assertThatThrownBy(() -> InspectionPlan.build(
                            "X",
                            "x",
                            "pipe-run",
                            List.of(InspectionPoint.of("IP-09", "paint", PointType.HOLD, "paint")),
                            PIPE_RUN))
                    .hasMessageContaining("a step the profile does not have");
        }

        @Test
        void acceptsAPlanWithNoPointsAtAll() {
            InspectionPlan bare =
                    InspectionPlan.build("ITP-NONE", "nothing to inspect", "pipe-run", List.of(), PIPE_RUN);
            assertThat(bare.gatedSteps()).isEmpty();
        }
    }

    @Nested
    class LookingPointsUp {

        @Test
        void findsById() {
            assertThat(PLAN.findPoint("IP-03").orElseThrow().name()).contains("line and level");
            assertThat(PLAN.findPoint("IP-99")).isEmpty();
            assertThat(PLAN.requirePoint("IP-01").type()).isEqualTo(PointType.HOLD);
        }

        @Test
        void throwsForAnIdNothingCarries() {
            assertThatThrownBy(() -> PLAN.requirePoint("IP-99")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void collectsEveryPointOnAStep() {
            assertThat(ids(PLAN.pointsForStep("excavate"))).containsExactly("IP-01");
            assertThat(ids(PLAN.pointsForStep("lay"))).containsExactly("IP-03");
            assertThat(PLAN.pointsForStep("nothing")).isEmpty();
        }
    }

    @Nested
    class PointTypes {

        @Test
        void countsOnlyHoldPointsAsGates() {
            assertThat(ids(PLAN.holdPointsForStep("excavate"))).containsExactly("IP-01");
            assertThat(PLAN.holdPointsForStep("bed")).isEmpty();
            assertThat(PLAN.holdPointsForStep("backfill")).isEmpty();
        }

        @Test
        void reportsWitnessPointsSeparately() {
            assertThat(ids(PLAN.witnessPointsForStep("bed"))).containsExactly("IP-02");
            assertThat(PLAN.witnessPointsForStep("excavate")).isEmpty();
        }

        @Test
        void listsTheStepsAHoldPointStandsOn() {
            assertThat(PLAN.gatedSteps()).containsExactly("excavate", "lay", "test");
        }

        @Test
        void listsTheDisciplinesInvolved() {
            assertThat(PLAN.disciplines()).containsExactly("civil", "geotech");
        }
    }

    @Nested
    class DescribePoint {

        @Test
        void readsLikeAPlanRow() {
            assertThat(PLAN.requirePoint("IP-01").describe())
                    .isEqualTo("IP-01 hold point, trench formation level and width");
        }
    }
}
