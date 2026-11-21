package io.github.rkaushikdev.chainageworks.progress;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static io.github.rkaushikdev.chainageworks.support.PackageFixtures.samplePackages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.resource.HourKind;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceLine;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturn;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturnInput;
import io.github.rkaushikdev.chainageworks.progress.PackageOutput.StepOutput;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PackageOutputTest {

    private static final ProgressContext CONTEXT = sampleContext();
    private static final WorkPackage WP01 = samplePackages().get(0);
    private static final LocalDate CUT_OFF = d("2025-03-25");

    private static ResourceLine gang(int count, double hours) {
        return gang(count, hours, HourKind.WORKING);
    }

    private static ResourceLine gang(int count, double hours, HourKind kind) {
        return new ResourceLine("GANG-EARTH", "earthworks gang", count, hours, kind);
    }

    private static ResourceReturnInput input(String id, String pkg, String date) {
        return ResourceReturnInput.of(id, pkg, d(date), "A Kerrigan");
    }

    private static final List<ResourceReturn> RETURNS = List.of(
            ResourceReturn.build(input("RR-01", "WP-01", "2025-03-03")
                    .withStep("strip")
                    .withLabour(List.of(gang(6, 8)))
                    .withPlant(List.of(new ResourceLine("PL-D6", "dozer", 1, 8, HourKind.WORKING)))),
            ResourceReturn.build(
                    input("RR-02", "WP-01", "2025-03-04").withStep("strip").withLabour(List.of(gang(6, 8)))),
            ResourceReturn.build(input("RR-03", "WP-01", "2025-03-10")
                    .withStep("excavate")
                    .withLabour(List.of(gang(4, 10)))
                    .withPlant(List.of(new ResourceLine("PL-360", "360", 1, 4, HourKind.STANDING)))),
            ResourceReturn.build(input("RR-04", "WP-01", "2025-03-11")
                    .withLabour(List.of(gang(2, 8)))
                    .withNote("general site tidy, no step named")),
            ResourceReturn.build(input("RR-05", "WP-01", "2025-03-12")
                    .withStep("landscaping")
                    .withLabour(List.of(gang(1, 6)))
                    .withNote("a step this rule of credit does not carry")),
            ResourceReturn.build(ResourceReturnInput.of("RR-06", "WP-02", d("2025-03-13"), "D Illingworth")
                    .withStep("strip")
                    .withLabour(List.of(gang(8, 8)))),
            ResourceReturn.build(input("RR-07", "WP-01", "2025-04-02")
                    .withStep("strip")
                    .withLabour(List.of(gang(6, 8)))
                    .withNote("after the cut off")));

    private static final List<OutputAllowance> ALLOWANCES =
            OutputAllowance.build(List.of(new OutputAllowance("strip", 10), new OutputAllowance("excavate", 12)));

    @Nested
    class BuildAllowances {

        @Test
        void takesACleanSet() {
            assertThat(ALLOWANCES).hasSize(2);
        }

        @Test
        void refusesTwoAllowancesAgainstOneStep() {
            assertThatThrownBy(() -> OutputAllowance.build(
                            List.of(new OutputAllowance("strip", 10), new OutputAllowance("strip", 12))))
                    .hasMessageContaining("two output allowances");
        }

        @Test
        void refusesAnAllowanceOfNothingAnHour() {
            assertThatThrownBy(() -> OutputAllowance.build(List.of(new OutputAllowance("strip", 0))))
                    .hasMessageContaining("more than nothing");
        }
    }

    @Nested
    class Output {

        private final PackageOutput output = PackageOutput.of(CONTEXT, WP01, RETURNS, CUT_OFF, ALLOWANCES);

        private StepOutput step(String id) {
            return output.steps().stream()
                    .filter(s -> s.stepId().equals(id))
                    .findFirst()
                    .orElseThrow();
        }

        @Test
        void reportsOneLineForEveryStepOfTheRuleOfCredit() {
            assertThat(output.steps().stream().map(StepOutput::stepId))
                    .containsExactly("strip", "excavate", "place", "trim");
        }

        @Test
        void setsWhatWasBuiltAgainstTheHoursItTook() {
            assertThat(step("strip").builtMetres()).isEqualTo(800);
            assertThat(step("strip").labourHours()).isEqualTo(96);
            assertThat(step("strip").achieved().getAsDouble()).isCloseTo(8.3333, byLessThan(5e-5));
        }

        @Test
        void countsAStepBehindItsAllowance() {
            assertThat(step("strip").ratio().getAsDouble()).isCloseTo(0.83333, byLessThan(5e-6));
            assertThat(output.behindAllowance().stream().map(StepOutput::stepId))
                    .containsExactly("strip");
        }

        @Test
        void countsAStepAheadOfItsAllowance() {
            assertThat(step("excavate").achieved().getAsDouble()).isEqualTo(15);
            assertThat(step("excavate").ratio().getAsDouble()).isEqualTo(1.25);
        }

        @Test
        void leavesTheRatioEmptyWhereNoAllowanceWasSet() {
            assertThat(step("place").allowed()).isEmpty();
            assertThat(step("place").ratio()).isEmpty();
        }

        @Test
        void leavesEverythingEmptyOnAStepNobodyBookedHoursTo() {
            assertThat(step("place").labourHours()).isZero();
            assertThat(step("place").achieved()).isEmpty();
        }

        @Test
        void keepsThePlantHoursApartFromTheLabourHours() {
            assertThat(step("strip").plantHours()).isEqualTo(8);
            assertThat(step("excavate").plantHours()).isEqualTo(4);
            assertThat(output.plantHours()).isEqualTo(12);
        }

        @Test
        void countsTheStandingPlantAsLostTime() {
            assertThat(step("excavate").lostHours()).isEqualTo(4);
            assertThat(step("strip").lostHours()).isZero();
        }

        @Test
        void holdsBackTheHoursNobodyBookedToAStep() {
            assertThat(output.unallocatedHours()).isEqualTo(22);
        }

        @Test
        void leavesOutAnotherPackageAndAnythingAfterTheCutOff() {
            assertThat(output.labourHours()).isEqualTo(158);
        }

        @Test
        void isEmptyOfHoursWhenNoReturnsHaveBeenMade() {
            PackageOutput empty = PackageOutput.of(CONTEXT, WP01, List.of(), CUT_OFF, ALLOWANCES);
            assertThat(empty.labourHours()).isZero();
            assertThat(empty.steps()).allMatch(s -> s.achieved().isEmpty());
        }
    }

    @Nested
    class NothingToShow {

        @Test
        void namesAStepWithHoursBookedAndNothingBuilt() {
            PackageOutput output = PackageOutput.of(
                    CONTEXT,
                    WP01,
                    List.of(ResourceReturn.build(input("RR-08", "WP-01", "2025-03-14")
                            .withStep("trim")
                            .withLabour(List.of(gang(4, 8))))),
                    CUT_OFF,
                    ALLOWANCES);
            assertThat(output.nothingToShow().stream().map(StepOutput::stepId)).containsExactly("trim");
        }

        @Test
        void namesNothingWhenEveryBookedStepBuiltSomething() {
            assertThat(PackageOutput.of(CONTEXT, WP01, List.of(RETURNS.get(0)), CUT_OFF)
                            .nothingToShow())
                    .isEmpty();
        }
    }

    @Nested
    class DescribeStepOutput {

        private final PackageOutput output = PackageOutput.of(CONTEXT, WP01, RETURNS, CUT_OFF, ALLOWANCES);

        @Test
        void saysTheRateAndHowItCompares() {
            assertThat(output.steps().get(0).describe())
                    .isEqualTo("strip, 8.33 metres an hour, 83 percent of the allowance");
        }

        @Test
        void saysSoWhenNoAllowanceWasSet() {
            assertThat(output.steps().get(1).describe()).contains("125 percent");
        }

        @Test
        void saysSoWhenNoHoursWereBooked() {
            assertThat(output.steps().get(2).describe()).isEqualTo("place, no hours booked");
        }
    }
}
