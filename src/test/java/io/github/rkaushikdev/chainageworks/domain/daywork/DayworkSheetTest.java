package io.github.rkaushikdev.chainageworks.domain.daywork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DayworkSheetTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static final DayworkSchedule SCHEDULE = DayworkSchedule.build(DayworkScheduleInput.of(
            List.of(DayworkRate.of("ganger", "ganger", 24), DayworkRate.of("labourer", "general labourer", 19.5)),
            List.of(
                    DayworkRate.of("excavator-13t", "13 tonne tracked excavator", 42),
                    DayworkRate.of("dumper-6t", "6 tonne dumper", 18))));

    private static final DayworkSheetInput BASE = new DayworkSheetInput(
            "DW-01",
            "DW/2025/014",
            "WP-01",
            "EI-045",
            d("2025-03-12"),
            d("2025-03-13"),
            "R Engineer",
            List.of(new LabourEntry("ganger", 1, 8, 0)),
            List.of(new PlantEntry("excavator-13t", 8, 0)),
            List.of(new MaterialEntry("imported stone", Money.fromMajor(400, Currency.GBP))));

    private static final DayworkSheet SHEET = DayworkSheet.build(BASE);

    @Nested
    class BuildSchedule {

        @Test
        void carriesTheContractAdditionsAndAnOvertimeFactor() {
            assertThat(SCHEDULE.additions()).isEqualTo(DayworkAdditions.DEFAULT);
            assertThat(SCHEDULE.overtimeFactor()).isEqualTo(1.5);
            assertThat(SCHEDULE.describe()).isEqualTo("2 labour rates, 2 plant rates, overtime at 1.50");
        }

        @Test
        void takesAnAdditionOfItsOwn() {
            DayworkSchedule cheap = DayworkSchedule.build(
                    DayworkScheduleInput.of(List.of(DayworkRate.of("ganger", "ganger", 24)), List.of())
                            .withLabourAddition(Percent.of(50)));
            assertThat(cheap.additions().labour()).isEqualTo(Percent.of(50));
            assertThat(cheap.additions().plant()).isEqualTo(DayworkAdditions.DEFAULT.plant());
        }

        @Test
        void refusesTwoRatesSharingAnId() {
            assertThatThrownBy(() -> DayworkSchedule.build(DayworkScheduleInput.of(
                            List.of(DayworkRate.of("a", "a", 1), DayworkRate.of("a", "b", 2)), List.of())))
                    .hasMessageContaining("share an id");
        }

        @Test
        void refusesARateInAnotherCurrencyAndANegativeOne() {
            assertThatThrownBy(() -> DayworkSchedule.build(DayworkScheduleInput.of(
                            List.of(new DayworkRate("a", "a", Rate.of(10, Currency.EUR, Unit.HR))), List.of())))
                    .hasMessageContaining("another currency");
            assertThatThrownBy(() -> DayworkSchedule.build(
                            DayworkScheduleInput.of(List.of(DayworkRate.of("a", "a", -1)), List.of())))
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        void refusesAnOvertimeFactorUnderOne() {
            assertThatThrownBy(() -> DayworkSchedule.build(
                            DayworkScheduleInput.of(List.of(), List.of()).withOvertimeFactor(0.5)))
                    .hasMessageContaining("at least one");
        }

        @Test
        void findsRatesAndThrowsForOneItDoesNotCarry() {
            assertThat(SCHEDULE.findLabourRate("ganger").orElseThrow().description())
                    .isEqualTo("ganger");
            assertThat(SCHEDULE.findLabourRate("nope")).isEmpty();
            assertThatThrownBy(() -> SCHEDULE.requirePlantRate("nope")).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class BuildSheet {

        @Test
        void keepsWhatItWasGiven() {
            assertThat(SHEET.reference()).isEqualTo("DW/2025/014");
            assertThat(SHEET.labour()).hasSize(1);
            assertThat(SHEET.isSignedAt(d("2025-03-31"))).isTrue();
        }

        @Test
        void allowsAnUnsignedSheet() {
            DayworkSheet unsigned = DayworkSheet.build(BASE.withSignature(null, ""));
            assertThat(unsigned.signedOn()).isEmpty();
            assertThat(unsigned.isSignedAt(d("2025-12-31"))).isFalse();
        }

        @Test
        void refusesASignedSheetWithNobodyNamed() {
            assertThatThrownBy(() -> DayworkSheet.build(BASE.withSignedBy("  ")))
                    .hasMessageContaining("name who signed it");
        }

        @Test
        void refusesASheetSignedBeforeTheWorkWasDone() {
            assertThatThrownBy(() -> DayworkSheet.build(BASE.withSignedOn(d("2025-03-01"))))
                    .hasMessageContaining("signed before the work was done");
        }

        @Test
        void refusesHoursThatAreNegativeOrLongerThanADay() {
            assertThatThrownBy(() -> DayworkSheet.build(BASE.withLabour(List.of(new LabourEntry("ganger", 1, -1, 0)))))
                    .hasMessageContaining("cannot be negative");
            assertThatThrownBy(() -> DayworkSheet.build(BASE.withPlant(List.of(new PlantEntry("dumper-6t", 30, 0)))))
                    .hasMessageContaining("more than a day");
        }

        @Test
        void refusesALabourLineWithNobodyOnIt() {
            assertThatThrownBy(() -> DayworkSheet.build(BASE.withLabour(List.of(new LabourEntry("ganger", 0, 8, 0)))))
                    .hasMessageContaining("at least one person");
        }

        @Test
        void refusesAnEmptySheet() {
            assertThatThrownBy(() -> DayworkSheet.build(
                            BASE.withLabour(List.of()).withPlant(List.of()).withMaterials(List.of())))
                    .hasMessageContaining("nothing on it");
        }
    }

    @Nested
    class ValueSheet {

        @Test
        void addsEachHeadSeparatelyBeforeAddingThemTogether() {
            DayworkSheet.Value value = SHEET.value(SCHEDULE);
            assertThat(value.labour().major()).isEqualTo(360.96);
            assertThat(value.plant().major()).isEqualTo(378);
            assertThat(value.materials().major()).isEqualTo(450);
            assertThat(value.total().major()).isEqualTo(1188.96);
        }

        @Test
        void multipliesALabourLineByTheNumberOfPeople() {
            DayworkSheet gang = DayworkSheet.build(BASE.withLabour(List.of(new LabourEntry("labourer", 4, 6, 0)))
                    .withPlant(List.of())
                    .withMaterials(List.of()));
            assertThat(gang.value(SCHEDULE).labour().major()).isEqualTo(879.84);
        }

        @Test
        void chargesOvertimeAtTheContractFactorAndNotOnTheAddition() {
            DayworkSheet evening = DayworkSheet.build(BASE.withLabour(List.of(new LabourEntry("ganger", 1, 8, 2)))
                    .withPlant(List.of())
                    .withMaterials(List.of()));
            assertThat(evening.value(SCHEDULE).labour().major()).isEqualTo(496.32);
        }

        @Test
        void valuesASheetWithOnlyMaterials() {
            DayworkSheet supply = DayworkSheet.build(BASE.withLabour(List.of()).withPlant(List.of()));
            DayworkSheet.Value value = supply.value(SCHEDULE);
            assertThat(value.labour().minor()).isZero();
            assertThat(value.total().major()).isEqualTo(450);
        }

        @Test
        void paysStandingPlantAtTheStandingRateWithTheAdditionOnTop() {
            DayworkSheet waiting =
                    DayworkSheet.build(BASE.withPlant(List.of(new PlantEntry("excavator-13t", 8, 0, 4))));
            assertThat(waiting.value(SCHEDULE).plant().major()).isEqualTo(378 + 94.5);
        }

        @Test
        void honoursAStandingRateTheScheduleSetsItself() {
            DayworkSchedule full = DayworkSchedule.build(DayworkScheduleInput.of(
                            List.of(), List.of(DayworkRate.of("excavator-13t", "13 tonne tracked excavator", 42)))
                    .withStandingFactor(1));
            assertThat(full.standingFactor()).isEqualTo(1);
            assertThat(full.plantValue(new PlantEntry("excavator-13t", 0, 0, 2)).major())
                    .isEqualTo(84);
            assertThat(SCHEDULE.standingFactor()).isEqualTo(DayworkSchedule.DEFAULT_STANDING_FACTOR);
        }

        @Test
        void refusesAStandingRateOutsideNoneToAll() {
            DayworkScheduleInput input = DayworkScheduleInput.of(List.of(), List.of());
            assertThatThrownBy(() -> DayworkSchedule.build(input.withStandingFactor(1.2)))
                    .hasMessageContaining("between none and all");
            assertThatThrownBy(() -> DayworkSchedule.build(input.withStandingFactor(-0.1)))
                    .hasMessageContaining("between none and all");
        }

        @Test
        void refusesAPlantLineBookedForMoreThanADayAltogether() {
            assertThatThrownBy(() ->
                            DayworkSheet.build(BASE.withPlant(List.of(new PlantEntry("excavator-13t", 10, 4, 12)))))
                    .hasMessageContaining("working and standing hours cannot be more than a day");
            assertThatThrownBy(() ->
                            DayworkSheet.build(BASE.withPlant(List.of(new PlantEntry("excavator-13t", 8, 0, -1)))))
                    .hasMessageContaining("standing hours cannot be negative");
        }

        @Test
        void refusesARateTheScheduleDoesNotCarry() {
            DayworkSheet stray = DayworkSheet.build(BASE.withLabour(List.of(new LabourEntry("foreman", 1, 8, 0))));
            assertThatThrownBy(() -> stray.value(SCHEDULE)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class HandingSheetsIn {

        private final DayworkSchedule weekly = DayworkSchedule.build(DayworkScheduleInput.of(
                        List.of(DayworkRate.of("ganger", "ganger", 24)),
                        List.of(DayworkRate.of("excavator-13t", "13 tonne tracked excavator", 42)))
                .withSubmissionDays(7));

        @Test
        void leavesASheetHandedInAfterTheDeadlineOffTheAccount() {
            DayworkSheet late = DayworkSheet.build(BASE.withId("DW-04").withSubmittedOn(d("2025-03-24")));
            assertThat(late.isLate(weekly)).isTrue();
            assertThat(DayworkSheet.dayworkValue(weekly, List.of(late), d("2025-03-31"))
                            .minor())
                    .isZero();
            assertThat(DayworkSheet.late(weekly, List.of(late, SHEET), d("2025-03-31")))
                    .extracting(DayworkSheet::id)
                    .containsExactly("DW-04");
        }

        @Test
        void valuesASheetHandedInOnTheLastDay() {
            DayworkSheet onTime = DayworkSheet.build(BASE.withSubmittedOn(d("2025-03-19")));
            assertThat(onTime.isLate(weekly)).isFalse();
            assertThat(DayworkSheet.dayworkValue(weekly, List.of(onTime), d("2025-03-31")))
                    .isEqualTo(SHEET.value(weekly).total());
        }

        @Test
        void takesTheSignatureAsTheDayItWentInWhenNothingElseIsRecorded() {
            DayworkSheet signedLate = DayworkSheet.build(BASE.withSignedOn(d("2025-03-28")));
            assertThat(signedLate.handedInOn()).contains(d("2025-03-28"));
            assertThat(signedLate.isLate(weekly)).isTrue();
            assertThat(signedLate.isLate(SCHEDULE)).isFalse();
        }

        @Test
        void refusesADeadlineBelowNothingAndASheetHandedInBeforeTheWork() {
            assertThatThrownBy(() -> DayworkSchedule.build(
                            DayworkScheduleInput.of(List.of(), List.of()).withSubmissionDays(-1)))
                    .hasMessageContaining("whole number of days");
            assertThatThrownBy(() -> DayworkSheet.build(BASE.withSubmittedOn(d("2025-03-11"))))
                    .hasMessageContaining("handed in before the work was done");
        }
    }

    @Nested
    class DayworkValue {

        private final DayworkSheet later = DayworkSheet.build(BASE.withId("DW-02")
                .withReference("DW/2025/015")
                .withWorkedOn(d("2025-03-26"))
                .withSignedOn(d("2025-04-02")));
        private final DayworkSheet unsigned = DayworkSheet.build(BASE.withId("DW-03")
                .withReference("DW/2025/016")
                .withWorkedOn(d("2025-03-20"))
                .withSignature(null, ""));
        private final List<DayworkSheet> sheets = List.of(SHEET, later, unsigned);

        @Test
        void countsOnlyWhatWasSignedByTheCutOff() {
            assertThat(DayworkSheet.dayworkValue(SCHEDULE, sheets, d("2025-03-31"))
                            .major())
                    .isEqualTo(1188.96);
        }

        @Test
        void picksUpTheLateSignatureInTheNextPeriod() {
            assertThat(DayworkSheet.dayworkValue(SCHEDULE, sheets, d("2025-04-30"))
                            .major())
                    .isEqualTo(2377.92);
        }

        @Test
        void isNothingWhenNothingHasBeenSigned() {
            assertThat(DayworkSheet.dayworkValue(SCHEDULE, List.of(unsigned), d("2025-12-31"))
                            .minor())
                    .isZero();
            assertThat(DayworkSheet.dayworkValue(SCHEDULE, List.of(), d("2025-12-31"))
                            .minor())
                    .isZero();
        }

        @Test
        void listsWhatIsWaitingOnASignature() {
            assertThat(DayworkSheet.unsigned(sheets, d("2025-03-31")).stream().map(DayworkSheet::id))
                    .containsExactly("DW-02", "DW-03");
        }

        @Test
        void doesNotChaseASheetForWorkThatHasNotHappenedYet() {
            assertThat(DayworkSheet.unsigned(sheets, d("2025-03-15"))).isEmpty();
        }
    }

    @Nested
    class Describe {

        @Test
        void readsLikeARegisterLine() {
            assertThat(SHEET.describe()).isEqualTo("DW/2025/014 worked 2025-03-12, signed 2025-03-13");
            DayworkSheet unsigned = DayworkSheet.build(BASE.withSignature(null, ""));
            assertThat(unsigned.describe()).contains("unsigned");
        }
    }
}
