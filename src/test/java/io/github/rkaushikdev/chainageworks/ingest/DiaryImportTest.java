package io.github.rkaushikdev.chainageworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.DateRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.domain.time.Standdown;
import io.github.rkaushikdev.chainageworks.domain.time.StanddownReason;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DiaryImportTest {

    private static String diary(String... rows) {
        List<String> lines = new ArrayList<>();
        lines.add("date,reason,note");
        lines.addAll(List.of(rows));
        return String.join("\n", lines);
    }

    @Nested
    class ImportStanddowns {

        @Test
        void readsACleanDiary() {
            ImportReport<Standdown> report = DiaryImport.importStanddowns(
                    diary("2025-03-05,weather,rain all day", "2025-03-06,ground conditions,"));
            assertThat(report.hasErrors()).isFalse();
            assertThat(report.imported().stream().map(s -> SiteDates.format(s.date())))
                    .containsExactly("2025-03-05", "2025-03-06");
            assertThat(report.imported().get(0).reason()).isEqualTo(StanddownReason.WEATHER);
            assertThat(report.imported().get(0).note()).contains("rain all day");
            assertThat(report.imported().get(1).reason()).isEqualTo(StanddownReason.GROUND_CONDITIONS);
        }

        @Test
        void mapsTheSpellingsASiteDiaryActuallyUses() {
            ImportReport<Standdown> report = DiaryImport.importStanddowns(diary(
                    "2025-03-05,rain,",
                    "2025-03-06,frost,",
                    "2025-03-07,waterlogged,",
                    "2025-03-10,no access,",
                    "2025-03-11,strike,"));
            assertThat(report.imported().stream().map(Standdown::reason))
                    .containsExactly(
                            StanddownReason.WEATHER,
                            StanddownReason.WEATHER,
                            StanddownReason.GROUND_CONDITIONS,
                            StanddownReason.ACCESS,
                            StanddownReason.INDUSTRIAL);
            assertThat(report.hasErrors()).isFalse();
        }

        @Test
        void filesAnUnknownReasonAsOtherAndWarnsRatherThanDroppingTheDay() {
            ImportReport<Standdown> report = DiaryImport.importStanddowns(diary("2025-03-05,badger,"));
            assertThat(report.imported().get(0).reason()).isEqualTo(StanddownReason.OTHER);
            assertThat(report.warnings().get(0).message()).contains("filing it as other");
            assertThat(report.hasErrors()).isFalse();
        }

        @Test
        void rejectsARowWithNoDateAndOneWithNoReason() {
            assertThat(DiaryImport.importStanddowns(diary(",weather,"))
                            .errors()
                            .get(0)
                            .column())
                    .contains("date");
            assertThat(DiaryImport.importStanddowns(diary("2025-03-05,,"))
                            .errors()
                            .get(0)
                            .column())
                    .contains("reason");
        }

        @Test
        void rejectsADateThatDoesNotExist() {
            assertThat(DiaryImport.importStanddowns(diary("2025-02-30,weather,"))
                            .errors()
                            .get(0)
                            .column())
                    .contains("date");
        }

        @Test
        void warnsAboutTheSameDayTwiceAndKeepsBothSinceTheCalendarDedupes() {
            ImportReport<Standdown> report =
                    DiaryImport.importStanddowns(diary("2025-03-05,weather,", "2025-03-05,access,"));
            assertThat(report.imported()).hasSize(2);
            assertThat(report.warnings().get(0).message()).isEqualTo("2025-03-05 is stood down twice");
            SiteCalendar calendar =
                    SiteCalendar.build(CalendarInput.of("C1", "wet march").withStanddowns(report.imported()));
            assertThat(calendar.standdowns()).hasSize(1);
            assertThat(calendar.standdowns().get(0).reason()).isEqualTo(StanddownReason.ACCESS);
        }

        @Test
        void feedsStraightIntoTheCalendar() {
            ImportReport<Standdown> report =
                    DiaryImport.importStanddowns(diary("2025-03-05,rain,", "2025-03-06,rain,"));
            SiteCalendar calendar =
                    SiteCalendar.build(CalendarInput.of("C1", "wet march").withStanddowns(report.imported()));
            assertThat(calendar.productiveDaysIn(DateRange.month(2025, 3))).isEqualTo(19);
        }

        @Test
        void namesAMissingColumnOnce() {
            ImportReport<Standdown> report = DiaryImport.importStanddowns("date\n2025-03-05");
            assertThat(report.issues()).hasSize(1);
            assertThat(report.issues().get(0).column()).contains("reason");
        }
    }

    @Nested
    class ImportHolidays {

        @Test
        void readsAListOfDates() {
            ImportReport<LocalDate> report = DiaryImport.importHolidays(
                    String.join("\n", "date,name", "2025-04-18,good friday", "2025-04-21,easter monday"));
            assertThat(report.imported().stream().map(SiteDates::format)).containsExactly("2025-04-18", "2025-04-21");
            assertThat(report.hasErrors()).isFalse();
        }

        @Test
        void dropsARepeatedDateWithAWarning() {
            ImportReport<LocalDate> report =
                    DiaryImport.importHolidays(String.join("\n", "date", "2025-04-18", "2025-04-18"));
            assertThat(report.imported()).hasSize(1);
            assertThat(report.warnings().get(0).message()).isEqualTo("2025-04-18 is listed twice");
        }

        @Test
        void rejectsADateItCannotReadAndKeepsGoing() {
            ImportReport<LocalDate> report =
                    DiaryImport.importHolidays(String.join("\n", "date", "easter", "2025-04-21"));
            assertThat(report.imported().stream().map(SiteDates::format)).containsExactly("2025-04-21");
            assertThat(report.errors()).hasSize(1);
        }

        @Test
        void namesAMissingColumn() {
            assertThat(DiaryImport.importHolidays("name\ngood friday")
                            .issues()
                            .get(0)
                            .column())
                    .contains("date");
        }
    }
}
