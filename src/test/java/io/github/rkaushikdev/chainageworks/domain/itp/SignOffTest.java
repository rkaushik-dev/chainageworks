package io.github.rkaushikdev.chainageworks.domain.itp;

import static io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue.PIPE_RUN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SignOffTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static ChainageRange r(String from, String to) {
        return ChainageRange.of(Stations.parse(from), Stations.parse(to));
    }

    private static final InspectionPlan PLAN = InspectionPlan.build(
            "ITP-PIPE",
            "pipe laying",
            "pipe-run",
            List.of(
                    InspectionPoint.of("IP-01", "trench formation", PointType.HOLD, "excavate"),
                    InspectionPoint.of("IP-02", "bedding grading", PointType.WITNESS, "bed"),
                    InspectionPoint.of("IP-03", "line and level", PointType.HOLD, "lay"),
                    InspectionPoint.of("IP-06", "joint inspection", PointType.HOLD, "lay")),
            PIPE_RUN);

    private static final ChainageRange EXTENT = r("12+000", "12+800");

    private static SignOff sign(String id, String pointId, String from, String to, String signedOn) {
        return SignOff.build(SignOffInput.of(id, pointId, "WP-01", r(from, to), d(signedOn), "A Surveyor"));
    }

    private static SignOff sign(
            String id, String pointId, String from, String to, String signedOn, SignOffOutcome outcome) {
        return SignOff.build(SignOffInput.of(id, pointId, "WP-01", r(from, to), d(signedOn), "A Surveyor")
                .withOutcome(outcome));
    }

    private static StepGate gate(String stepId, List<SignOff> signOffs) {
        return gate(stepId, signOffs, "2025-03-31");
    }

    private static StepGate gate(String stepId, List<SignOff> signOffs, String asAt) {
        return StepGate.forStep(new StepGate.Query(PLAN, signOffs, stepId, "WP-01", EXTENT, d(asAt)));
    }

    private static List<String> show(List<ChainageRange> ranges) {
        return ranges.stream().map(ChainageRange::format).toList();
    }

    @Nested
    class Build {

        @Test
        void defaultsTheOutcomeToPassed() {
            assertThat(sign("S1", "IP-01", "12+000", "12+800", "2025-03-10").outcome())
                    .isEqualTo(SignOffOutcome.PASSED);
        }

        @Test
        void refusesASignOffWithNobodyNamed() {
            assertThatThrownBy(() ->
                            SignOff.build(SignOffInput.of("S1", "IP-01", "WP-01", EXTENT, d("2025-03-10"), "   ")))
                    .hasMessageContaining("name who signed it");
        }

        @Test
        void treatsAFailedInspectionAsNoRelease() {
            assertThat(sign("S1", "IP-01", "12+000", "12+800", "2025-03-10").releases())
                    .isTrue();
            SignOff commented =
                    sign("S2", "IP-01", "12+000", "12+800", "2025-03-10", SignOffOutcome.PASSED_WITH_COMMENT);
            assertThat(commented.releases()).isTrue();
            assertThat(sign("S3", "IP-01", "12+000", "12+800", "2025-03-10", SignOffOutcome.FAILED)
                            .releases())
                    .isFalse();
        }
    }

    @Nested
    class NobodyAttended {

        private SignOff unattended(String noticeOn, String inspectedOn) {
            return SignOff.build(SignOffInput.of("S9", "IP-01", "WP-01", EXTENT, d(inspectedOn), "C Agent")
                    .withOutcome(SignOffOutcome.NOT_ATTENDED)
                    .withNoticeGivenOn(d(noticeOn)));
        }

        @Test
        void releasesTheWorkWhenNoticeWasGivenInTime() {
            SignOff deemed = unattended("2025-03-07", "2025-03-10");
            assertThat(deemed.releases()).isTrue();
            assertThat(deemed.isDeemed()).isTrue();
            assertThat(gate("excavate", List.of(deemed)).state()).isEqualTo(GateState.OPEN);
        }

        @Test
        void holdsTheWorkWhenTheNoticeCameOnTheDayItself() {
            SignOff sameDay = unattended("2025-03-10", "2025-03-10");
            assertThat(sameDay.releases()).isFalse();
            assertThat(sameDay.isDeemed()).isFalse();
            assertThat(gate("excavate", List.of(sameDay)).state()).isEqualTo(GateState.HELD);
        }

        @Test
        void refusesARecordOfNonAttendanceWithNoNoticeBehindIt() {
            assertThatThrownBy(() ->
                            SignOff.build(SignOffInput.of("S9", "IP-01", "WP-01", EXTENT, d("2025-03-10"), "C Agent")
                                    .withOutcome(SignOffOutcome.NOT_ATTENDED)))
                    .hasMessageContaining("never told about");
        }

        @Test
        void refusesNoticeGivenAfterTheInspection() {
            assertThatThrownBy(() -> unattended("2025-03-12", "2025-03-10"))
                    .hasMessageContaining("cannot come after it");
        }

        @Test
        void keepsAnOrdinaryInspectionFromCountingAsDeemed() {
            SignOff passed = sign("S1", "IP-01", "12+000", "12+800", "2025-03-10");
            assertThat(passed.isDeemed()).isFalse();
            assertThat(passed.noticeGivenOn()).isEmpty();
        }
    }

    @Nested
    class ForStep {

        @Test
        void isOpenOverTheWholeExtentWhenTheStepHasNoHoldPoint() {
            StepGate result = gate("bed", List.of());
            assertThat(result.state()).isEqualTo(GateState.OPEN);
            assertThat(show(result.openExtent())).containsExactly("12+000.000 to 12+800.000");
        }

        @Test
        void isHeldWhenTheHoldPointHasNotBeenSignedAtAll() {
            StepGate result = gate("excavate", List.of());
            assertThat(result.state()).isEqualTo(GateState.HELD);
            assertThat(result.openExtent()).isEmpty();
            assertThat(result.heldPoints()).containsExactly("IP-01");
            assertThat(result.openFraction(EXTENT)).isZero();
        }

        @Test
        void opensTheWholeExtentWhenTheHoldPointCoversIt() {
            StepGate result = gate("excavate", List.of(sign("S1", "IP-01", "12+000", "12+800", "2025-03-10")));
            assertThat(result.state()).isEqualTo(GateState.OPEN);
            assertThat(result.openFraction(EXTENT)).isEqualTo(1);
        }

        @Test
        void opensOnlyTheStretchThatWasInspected() {
            StepGate result = gate("excavate", List.of(sign("S1", "IP-01", "12+000", "12+200", "2025-03-10")));
            assertThat(result.state()).isEqualTo(GateState.PART_OPEN);
            assertThat(show(result.openExtent())).containsExactly("12+000.000 to 12+200.000");
            assertThat(result.openFraction(EXTENT)).isEqualTo(0.25);
        }

        @Test
        void addsUpSeveralSignOffsOnTheSamePoint() {
            StepGate result = gate(
                    "excavate",
                    List.of(
                            sign("S1", "IP-01", "12+000", "12+200", "2025-03-10"),
                            sign("S2", "IP-01", "12+200", "12+600", "2025-03-14")));
            assertThat(show(result.openExtent())).containsExactly("12+000.000 to 12+600.000");
            assertThat(result.openFraction(EXTENT)).isEqualTo(0.75);
        }

        @Test
        void needsEveryHoldPointOnTheStepNotJustOne() {
            StepGate oneOnly = gate("lay", List.of(sign("S1", "IP-03", "12+000", "12+800", "2025-03-10")));
            assertThat(oneOnly.state()).isEqualTo(GateState.HELD);
            assertThat(oneOnly.heldPoints()).containsExactly("IP-06");

            StepGate both = gate(
                    "lay",
                    List.of(
                            sign("S1", "IP-03", "12+000", "12+800", "2025-03-10"),
                            sign("S2", "IP-06", "12+000", "12+800", "2025-03-11")));
            assertThat(both.state()).isEqualTo(GateState.OPEN);
        }

        @Test
        void takesTheOverlapWhenTwoHoldPointsReleasedDifferentStretches() {
            StepGate result = gate(
                    "lay",
                    List.of(
                            sign("S1", "IP-03", "12+000", "12+600", "2025-03-10"),
                            sign("S2", "IP-06", "12+400", "12+800", "2025-03-11")));
            assertThat(show(result.openExtent())).containsExactly("12+400.000 to 12+600.000");
            assertThat(result.state()).isEqualTo(GateState.PART_OPEN);
            assertThat(result.heldPoints()).isEmpty();
        }

        @Test
        void ignoresASignOffDatedAfterTheCutOff() {
            List<SignOff> late = List.of(sign("S1", "IP-01", "12+000", "12+800", "2025-04-02"));
            assertThat(gate("excavate", late, "2025-03-31").state()).isEqualTo(GateState.HELD);
            assertThat(gate("excavate", late, "2025-04-30").state()).isEqualTo(GateState.OPEN);
        }

        @Test
        void ignoresAFailedInspection() {
            List<SignOff> failed =
                    List.of(sign("S1", "IP-01", "12+000", "12+800", "2025-03-10", SignOffOutcome.FAILED));
            assertThat(gate("excavate", failed).state()).isEqualTo(GateState.HELD);
        }

        @Test
        void ignoresASignOffAgainstAnotherPackage() {
            SignOff other =
                    SignOff.build(SignOffInput.of("S9", "IP-01", "WP-02", EXTENT, d("2025-03-10"), "A Surveyor"));
            assertThat(gate("excavate", List.of(other)).state()).isEqualTo(GateState.HELD);
        }

        @Test
        void clipsASignOffThatRunsPastTheExtentAskedAbout() {
            StepGate result = gate("excavate", List.of(sign("S1", "IP-01", "11+000", "14+000", "2025-03-10")));
            assertThat(show(result.openExtent())).containsExactly("12+000.000 to 12+800.000");
        }
    }

    @Nested
    class IntersectRangeLists {

        @Test
        void findsTheSharedStretches() {
            List<ChainageRange> shared = ChainageRange.intersectLists(
                    List.of(r("12+000", "12+600"), r("13+000", "13+400")), List.of(r("12+400", "13+200")));
            assertThat(show(shared)).containsExactly("12+400.000 to 12+600.000", "13+000.000 to 13+200.000");
        }

        @Test
        void givesNothingWhenEitherSideIsEmpty() {
            assertThat(ChainageRange.intersectLists(List.of(), List.of(r("12+000", "12+600"))))
                    .isEmpty();
            assertThat(ChainageRange.intersectLists(List.of(r("12+000", "12+600")), List.of()))
                    .isEmpty();
        }

        @Test
        void normalisesBothSidesFirstSoOverlapIsNotCountedTwice() {
            List<ChainageRange> shared = ChainageRange.intersectLists(
                    List.of(r("12+000", "12+400"), r("12+200", "12+600")), List.of(r("12+000", "12+800")));
            assertThat(show(shared)).containsExactly("12+000.000 to 12+600.000");
        }
    }

    @Nested
    class Describe {

        @Test
        void readsLikeARegisterLine() {
            assertThat(sign("S1", "IP-01", "12+000", "12+800", "2025-03-10").describe())
                    .isEqualTo("IP-01 passed on 2025-03-10 by A Surveyor");
        }
    }
}
