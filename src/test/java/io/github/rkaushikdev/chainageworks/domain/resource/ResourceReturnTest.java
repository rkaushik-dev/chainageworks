package io.github.rkaushikdev.chainageworks.domain.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.chainageworks.domain.time.DateRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResourceReturnTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static final ResourceLine GANG = new ResourceLine("GANG-EARTH", "earthworks gang", 6, 8, HourKind.WORKING);
    private static final ResourceLine STANDING =
            new ResourceLine("GANG-EARTH", "earthworks gang", 6, 2, HourKind.STANDING);
    private static final ResourceLine EXCAVATOR =
            new ResourceLine("PL-360-22T", "22 tonne tracked excavator", 2, 8, HourKind.WORKING);

    private static final ResourceReturn MONDAY =
            ResourceReturn.build(ResourceReturnInput.of("RR-01", "WP-01", d("2025-03-03"), "A Kerrigan")
                    .withStep("excavate")
                    .withLabour(List.of(GANG, STANDING))
                    .withPlant(List.of(EXCAVATOR)));

    private static final ResourceReturn TUESDAY =
            ResourceReturn.build(ResourceReturnInput.of("RR-02", "WP-01", d("2025-03-04"), "A Kerrigan")
                    .withShift(Shift.NIGHT)
                    .withLabour(List.of(GANG.withHours(10)))
                    .withPlant(List.of(EXCAVATOR.withHours(4).withKind(HourKind.IDLE)))
                    .withNote("water main strike, stood down after four hours"));

    private static final ResourceReturn ELSEWHERE =
            ResourceReturn.build(ResourceReturnInput.of("RR-03", "WP-02", d("2025-03-05"), "D Illingworth")
                    .withLabour(List.of(GANG.withCount(4))));

    private static ResourceReturnInput blank() {
        return ResourceReturnInput.of("RR-99", "WP-01", d("2025-03-03"), "A Kerrigan");
    }

    @Nested
    class Build {

        @Test
        void fillsInTheShiftTheStepAndTheNote() {
            assertThat(MONDAY.shift()).isEqualTo(Shift.DAY);
            assertThat(ELSEWHERE.stepId()).isEmpty();
            assertThat(ELSEWHERE.note()).isEmpty();
            assertThat(TUESDAY.shift()).isEqualTo(Shift.NIGHT);
        }

        @Test
        void refusesAReturnWithNothingOnIt() {
            assertThatThrownBy(() -> ResourceReturn.build(blank())).hasMessageContaining("says nothing");
        }

        @Test
        void refusesALineCoveringLessThanOneOfSomething() {
            assertThatThrownBy(() -> ResourceReturn.build(blank().withLabour(List.of(GANG.withCount(0)))))
                    .hasMessageContaining("at least one of something");
        }

        @Test
        void refusesNegativeHours() {
            assertThatThrownBy(() -> ResourceReturn.build(blank().withPlant(List.of(EXCAVATOR.withHours(-1)))))
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        void refusesADayLongerThanADay() {
            assertThatThrownBy(() -> ResourceReturn.build(blank().withLabour(List.of(GANG.withHours(25)))))
                    .hasMessageContaining("twenty four hours");
        }
    }

    @Nested
    class CountingHours {

        @Test
        void multipliesTheCountByTheHours() {
            assertThat(GANG.lineHours()).isEqualTo(48);
            assertThat(EXCAVATOR.lineHours()).isEqualTo(16);
        }

        @Test
        void addsUpASetOfLines() {
            assertThat(ResourceReturn.totalHours(MONDAY.labour())).isEqualTo(60);
        }

        @Test
        void picksOutOneKindOfHour() {
            assertThat(ResourceReturn.hoursOfKind(MONDAY.labour(), HourKind.WORKING))
                    .isEqualTo(48);
            assertThat(ResourceReturn.hoursOfKind(MONDAY.labour(), HourKind.STANDING))
                    .isEqualTo(12);
            assertThat(ResourceReturn.hoursOfKind(MONDAY.labour(), HourKind.IDLE))
                    .isZero();
        }
    }

    @Nested
    class TotalResources {

        @Test
        void keepsLabourAndPlantApart() {
            ResourceReturn.Totals totals = ResourceReturn.total(List.of(MONDAY, TUESDAY));
            assertThat(totals.labourHours()).isEqualTo(120);
            assertThat(totals.plantHours()).isEqualTo(24);
            assertThat(totals.returns()).isEqualTo(2);
            assertThat(totals.days()).isEqualTo(2);
        }

        @Test
        void addsTheKindsAcrossBoth() {
            ResourceReturn.Totals totals = ResourceReturn.total(List.of(MONDAY, TUESDAY));
            assertThat(totals.workingHours()).isEqualTo(124);
            assertThat(totals.standingHours()).isEqualTo(12);
            assertThat(totals.idleHours()).isEqualTo(8);
        }

        @Test
        void countsASecondReturnOnTheSameDayOnce() {
            ResourceReturn again =
                    ResourceReturn.build(ResourceReturnInput.of("RR-04", "WP-02", d("2025-03-03"), "D Illingworth")
                            .withLabour(List.of(GANG)));
            assertThat(ResourceReturn.total(List.of(MONDAY, again)).days()).isEqualTo(1);
        }

        @Test
        void isAllZeroesOnNothingAtAll() {
            assertThat(ResourceReturn.total(List.of())).isEqualTo(new ResourceReturn.Totals(0, 0, 0, 0, 0, 0, 0));
        }
    }

    @Nested
    class PickingReturnsOut {

        private final List<ResourceReturn> all = List.of(MONDAY, TUESDAY, ELSEWHERE);

        @Test
        void takesTheOnesAgainstAPackage() {
            assertThat(ResourceReturn.returnsFor(all, "WP-01").stream().map(ResourceReturn::id))
                    .containsExactly("RR-01", "RR-02");
        }

        @Test
        void takesTheOnesInsideADateRange() {
            DateRange range = DateRange.of(d("2025-03-04"), d("2025-03-05"));
            assertThat(ResourceReturn.returnsIn(all, range).stream().map(ResourceReturn::id))
                    .containsExactly("RR-02", "RR-03");
        }

        @Test
        void takesTheOnesUpToACutOffIncludingTheDayItself() {
            assertThat(ResourceReturn.returnsTo(all, d("2025-03-04")).stream().map(ResourceReturn::id))
                    .containsExactly("RR-01", "RR-02");
        }
    }

    @Nested
    class LostFraction {

        @Test
        void isTheStandingAndIdleHoursOverEverything() {
            assertThat(ResourceReturn.total(List.of(MONDAY, TUESDAY)).lostFraction())
                    .isCloseTo(20.0 / 144, byLessThan(5e-7));
        }

        @Test
        void isNothingWhenEveryHourWorked() {
            assertThat(ResourceReturn.total(List.of(ELSEWHERE)).lostFraction()).isZero();
        }

        @Test
        void isNothingRatherThanADivisionByZeroOnAnEmptySet() {
            assertThat(ResourceReturn.total(List.of()).lostFraction()).isZero();
        }
    }

    @Nested
    class Describe {

        @Test
        void saysTheDayInOneLine() {
            assertThat(MONDAY.describe()).isEqualTo("RR-01 WP-01 2025-03-03 day, 60 labour hours, 16 plant hours");
        }
    }
}
