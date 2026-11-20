package io.github.rkaushikdev.chainageworks.progress;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.range;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleRecords;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.signOff;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue;
import io.github.rkaushikdev.chainageworks.domain.credit.StepProgress;
import io.github.rkaushikdev.chainageworks.domain.itp.GateState;
import io.github.rkaushikdev.chainageworks.domain.itp.Ncr;
import io.github.rkaushikdev.chainageworks.domain.itp.NcrInput;
import io.github.rkaushikdev.chainageworks.domain.itp.NcrStatus;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.itp.StepGate;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.support.ContextFixtures.Overrides;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StepProgressDetailTest {

    private static final ProgressContext CONTEXT = sampleContext();
    private static final WorkPackage WP01 = CONTEXT.register().require("WP-01");
    private static final LocalDate CUT_OFF = d("2025-03-31");

    private static List<String> show(List<ChainageRange> ranges) {
        return ranges.stream().map(ChainageRange::format).toList();
    }

    @Nested
    class BuiltExtent {

        @Test
        void takesTheStretchTheSurveyBookCoversForAStep() {
            assertThat(show(StepProgressDetail.builtExtent(CONTEXT, WP01, "strip", CUT_OFF)))
                    .containsExactly("12+000.000 to 12+800.000");
            assertThat(show(StepProgressDetail.builtExtent(CONTEXT, WP01, "excavate", CUT_OFF)))
                    .containsExactly("12+000.000 to 12+600.000");
        }

        @Test
        void isEmptyForAStepNothingWasBookedAgainst() {
            assertThat(StepProgressDetail.builtExtent(CONTEXT, WP01, "trim", CUT_OFF))
                    .isEmpty();
        }

        @Test
        void ignoresRecordsThatAreNotInDateYet() {
            assertThat(StepProgressDetail.builtExtent(CONTEXT, WP01, "excavate", d("2025-03-05")))
                    .isEmpty();
        }

        @Test
        void clipsARecordThatRunsPastThePackage() {
            List<SurveyRecord> records = sampleRecords();
            ProgressContext wide = sampleContext(Overrides.NONE.withRecords(
                    List.of(records.get(0), records.get(1).withExtent(range("11+000", "14+000")))));
            assertThat(show(StepProgressDetail.builtExtent(wide, WP01, "excavate", CUT_OFF)))
                    .containsExactly("12+000.000 to 12+800.000");
        }
    }

    @Nested
    class Gate {

        @Test
        void isOpenOverTheWholePackageWhenNoPlanGatesTheProfile() {
            ProgressContext ungated = sampleContext(Overrides.NONE.withPlans(List.of()));
            StepGate gate = StepProgressDetail.gate(ungated, WP01, "excavate", CUT_OFF);
            assertThat(gate.state()).isEqualTo(GateState.OPEN);
            assertThat(show(gate.openExtent())).containsExactly("12+000.000 to 12+800.000");
        }

        @Test
        void isOpenOnlyOverWhatTheHoldPointReleased() {
            assertThat(show(StepProgressDetail.gate(CONTEXT, WP01, "excavate", CUT_OFF)
                            .openExtent()))
                    .containsExactly("12+000.000 to 12+400.000");
        }

        @Test
        void isOpenOverTheWholePackageForAStepWithNoHoldPoint() {
            assertThat(StepProgressDetail.gate(CONTEXT, WP01, "strip", CUT_OFF).state())
                    .isEqualTo(GateState.OPEN);
        }
    }

    @Nested
    class ForStep {

        @Test
        void creditsTheWholeOfAStepWithNothingGatingIt() {
            StepProgressDetail strip = StepProgressDetail.forStep(CONTEXT, WP01, "strip", CUT_OFF);
            assertThat(strip.fraction()).isEqualTo(1);
            assertThat(strip.gate()).isEqualTo(GateState.OPEN);
            assertThat(strip.heldBackExtent()).isEmpty();
        }

        @Test
        void creditsOnlyWhatTheHoldPointReleased() {
            StepProgressDetail excavate = StepProgressDetail.forStep(CONTEXT, WP01, "excavate", CUT_OFF);
            assertThat(show(excavate.builtExtent())).containsExactly("12+000.000 to 12+600.000");
            assertThat(show(excavate.creditedExtent())).containsExactly("12+000.000 to 12+400.000");
            assertThat(excavate.fraction()).isEqualTo(0.5);
            assertThat(show(excavate.heldBackExtent())).containsExactly("12+400.000 to 12+600.000");
        }

        @Test
        void takesTheOpenNonConformanceOutOfWhatIsLeft() {
            StepProgressDetail place = StepProgressDetail.forStep(CONTEXT, WP01, "place", CUT_OFF);
            assertThat(show(place.builtExtent())).containsExactly("12+000.000 to 12+400.000");
            assertThat(show(place.creditedExtent()))
                    .containsExactly("12+000.000 to 12+100.000", "12+150.000 to 12+200.000");
            assertThat(place.fraction()).isCloseTo(0.1875, byLessThan(5e-7));
            assertThat(show(place.withheldExtent())).containsExactly("12+100.000 to 12+150.000");
        }

        @Test
        void creditsNothingForAStepNobodyHasStarted() {
            StepProgressDetail trim = StepProgressDetail.forStep(CONTEXT, WP01, "trim", CUT_OFF);
            assertThat(trim.fraction()).isZero();
            assertThat(trim.creditedExtent()).isEmpty();
        }

        @Test
        void namesTheHoldPointThatIsStoppingAStep() {
            ProgressContext noSignOffs = sampleContext(Overrides.NONE.withSignOffs(List.of()));
            StepProgressDetail excavate = StepProgressDetail.forStep(noSignOffs, WP01, "excavate", CUT_OFF);
            assertThat(excavate.gate()).isEqualTo(GateState.HELD);
            assertThat(excavate.heldPoints()).containsExactly("IP-E1");
            assertThat(excavate.fraction()).isZero();
            assertThat(show(excavate.heldBackExtent())).containsExactly("12+000.000 to 12+600.000");
        }

        @Test
        void releasesTheWithheldStretchOnceTheReportIsSettled() {
            Ncr settledNcr = Ncr.build(
                    NcrInput.from(sampleContext().ncrs().get(0)).withSettlement(NcrStatus.CLOSED, d("2025-03-28")));
            ProgressContext settled = sampleContext(Overrides.NONE.withNcrs(List.of(settledNcr)));
            assertThat(show(StepProgressDetail.forStep(settled, WP01, "place", CUT_OFF)
                            .creditedExtent()))
                    .containsExactly("12+000.000 to 12+200.000");
        }

        @Test
        void readsMoreOnceALaterSignOffLands() {
            List<SignOff> signOffs = new ArrayList<>(sampleContext().signOffs());
            signOffs.add(signOff("SO-03", "IP-E1", "WP-01", "12+400", "12+800", "2025-04-04"));
            ProgressContext later = sampleContext(Overrides.NONE.withSignOffs(signOffs));
            assertThat(StepProgressDetail.forStep(later, WP01, "excavate", CUT_OFF)
                            .fraction())
                    .isEqualTo(0.5);
            assertThat(StepProgressDetail.forStep(later, WP01, "excavate", d("2025-04-30"))
                            .fraction())
                    .isEqualTo(0.75);
        }
    }

    @Nested
    class PackageStepProgressAndTheRuleOfCredit {

        private final List<StepProgressDetail> details = StepProgressDetail.forPackage(CONTEXT, WP01, CUT_OFF);

        @Test
        void givesOneDetailPerStepInTheProfileOrder() {
            assertThat(details.stream().map(StepProgressDetail::stepId))
                    .containsExactly("strip", "excavate", "place", "trim");
        }

        @Test
        void turnsTheDetailsIntoTheShapeTheRuleOfCreditReads() {
            assertThat(StepProgressDetail.toStepProgress(details).stream()
                            .map(s -> s.fraction().value()))
                    .containsExactly(100.0, 50.0, 18.75, 0.0);
        }

        @Test
        void earnsNothingForThePlacingBecauseTheExcavationIsNotFinished() {
            List<StepProgress> progress = StepProgressDetail.toStepProgress(details);
            assertThat(CreditCatalogue.BULK_EARTHWORKS.earnedFraction(progress).value())
                    .isEqualTo(32.5);
        }

        @Test
        void releasesThePlacingOnceTheExcavationIsSignedOffInFull() {
            List<SurveyRecord> records = sampleRecords();
            ProgressContext complete = sampleContext(Overrides.NONE
                    .withSignOffs(List.of(
                            signOff("SO-01", "IP-E1", "WP-01", "12+000", "12+800", "2025-03-12"),
                            signOff("SO-02", "IP-E2", "WP-01", "12+000", "12+800", "2025-03-22")))
                    .withRecords(List.of(
                            records.get(0), records.get(1).withExtent(range("12+000", "12+800")), records.get(2)))
                    .withNcrs(List.of()));
            List<StepProgress> progress =
                    StepProgressDetail.toStepProgress(StepProgressDetail.forPackage(complete, WP01, CUT_OFF));
            assertThat(CreditCatalogue.BULK_EARTHWORKS.earnedFraction(progress).value())
                    .isEqualTo(72.5);
        }
    }
}
