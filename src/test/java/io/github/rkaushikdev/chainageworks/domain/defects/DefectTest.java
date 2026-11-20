package io.github.rkaushikdev.chainageworks.domain.defects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefectTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static final SiteCalendar CALENDAR = SiteCalendar.build(CalendarInput.of("C1", "main works"));

    private static final DefectInput BASE = DefectInput.of(
            "DF-01",
            "DEF/2025/011",
            "WP-03",
            "surface course ravelling at the joint",
            d("2025-11-03"),
            d("2025-12-01"));

    private static final Defect MINOR = Defect.build(BASE);

    private static final Defect SAFETY = Defect.build(BASE.withId("DF-02")
            .withReference("DEF/2025/012")
            .withDescription("missing safety barrier at the retaining wall")
            .withSeverity(DefectSeverity.SAFETY)
            .withDates(d("2025-11-05"), d("2025-11-06")));

    @Nested
    class Build {

        @Test
        void defaultsToMinorAndToTheContractor() {
            assertThat(MINOR.severity()).isEqualTo(DefectSeverity.MINOR);
            assertThat(MINOR.attribution()).isEqualTo(Attribution.CONTRACTOR);
            assertThat(MINOR.rectifiedOn()).isEmpty();
            assertThat(MINOR.extent()).isEmpty();
        }

        @Test
        void refusesAnEmptyDescription() {
            assertThatThrownBy(() -> Defect.build(BASE.withDescription(" ")))
                    .hasMessageContaining("needs a description");
        }

        @Test
        void refusesADueDateBeforeTheDefectWasRaised() {
            assertThatThrownBy(() -> Defect.build(BASE.withDueBy(d("2025-10-01"))))
                    .hasMessageContaining("due before it was raised");
        }

        @Test
        void refusesARectificationBeforeTheDefectWasRaised() {
            assertThatThrownBy(() -> Defect.build(BASE.withRectifiedOn(d("2025-10-01"))))
                    .hasMessageContaining("put right before it was raised");
        }
    }

    @Nested
    class DueDateFor {

        @Test
        void givesASafetyDefectOneWorkingDay() {
            assertThat(DefectSeverity.SAFETY.rectificationDays()).isEqualTo(1);
            assertThat(SiteDates.format(Defect.dueDateFor(DefectSeverity.SAFETY, d("2025-11-03"), CALENDAR)))
                    .isEqualTo("2025-11-04");
        }

        @Test
        void givesAMajorDefectTwoWorkingWeeksAndAMinorOneFour() {
            assertThat(SiteDates.format(Defect.dueDateFor(DefectSeverity.MAJOR, d("2025-11-03"), CALENDAR)))
                    .isEqualTo("2025-11-17");
            assertThat(SiteDates.format(Defect.dueDateFor(DefectSeverity.MINOR, d("2025-11-03"), CALENDAR)))
                    .isEqualTo("2025-12-01");
        }

        @Test
        void stepsOverAWeekendRatherThanCountingIt() {
            assertThat(SiteDates.format(Defect.dueDateFor(DefectSeverity.SAFETY, d("2025-11-07"), CALENDAR)))
                    .isEqualTo("2025-11-10");
        }
    }

    @Nested
    class IsOutstandingAt {

        @Test
        void isNotOutstandingBeforeItWasRaised() {
            assertThat(MINOR.isOutstandingAt(d("2025-11-02"))).isFalse();
        }

        @Test
        void isOutstandingFromTheDayItWasRaised() {
            assertThat(MINOR.isOutstandingAt(d("2025-11-03"))).isTrue();
            assertThat(MINOR.isOutstandingAt(d("2026-01-01"))).isTrue();
        }

        @Test
        void stopsBeingOutstandingOnTheDayItIsPutRight() {
            Defect done = Defect.build(BASE.withRectifiedOn(d("2025-11-20")));
            assertThat(done.isOutstandingAt(d("2025-11-19"))).isTrue();
            assertThat(done.isOutstandingAt(d("2025-11-20"))).isFalse();
        }
    }

    @Nested
    class IsOverdueAt {

        @Test
        void isNotOverdueOnTheDayItIsDue() {
            assertThat(MINOR.isOverdueAt(d("2025-12-01"))).isFalse();
            assertThat(MINOR.isOverdueAt(d("2025-12-02"))).isTrue();
        }

        @Test
        void isNotOverdueOnceItHasBeenPutRight() {
            Defect done = Defect.build(BASE.withRectifiedOn(d("2025-11-20")));
            assertThat(done.isOverdueAt(d("2026-01-01"))).isFalse();
        }
    }

    @Nested
    class TheOutstandingList {

        private final Defect employerItem = Defect.build(BASE.withId("DF-03")
                .withReference("DEF/2025/013")
                .withDescription("damage by a third party contractor")
                .withAttribution(Attribution.EMPLOYER)
                .withDates(d("2025-11-10"), d("2025-11-24")));
        private final Defect disputed = Defect.build(BASE.withId("DF-04")
                .withReference("DEF/2025/014")
                .withDescription("cracking at the headwall, cause not agreed")
                .withSeverity(DefectSeverity.MAJOR)
                .withAttribution(Attribution.UNRESOLVED)
                .withDates(d("2025-11-12"), d("2025-11-26")));
        private final List<Defect> list = List.of(MINOR, SAFETY, employerItem, disputed);

        @Test
        void sortsByTheDateTheyFallDue() {
            assertThat(Defect.outstanding(list, d("2025-11-15")).stream().map(Defect::id))
                    .containsExactly("DF-02", "DF-03", "DF-04", "DF-01");
        }

        @Test
        void reportsWhatIsOverdue() {
            assertThat(Defect.overdue(list, d("2025-11-15")).stream().map(Defect::id))
                    .containsExactly("DF-02");
            assertThat(Defect.overdue(list, d("2025-11-06"))).isEmpty();
        }

        @Test
        void summarisesBySeverityAndCountsTheDisputes() {
            Defect.Summary summary = Defect.summarise(list, d("2025-11-15"));
            assertThat(summary.outstanding()).isEqualTo(4);
            assertThat(summary.overdue()).isEqualTo(1);
            assertThat(summary.bySeverity())
                    .isEqualTo(Map.of(DefectSeverity.MINOR, 2, DefectSeverity.MAJOR, 1, DefectSeverity.SAFETY, 1));
            assertThat(summary.disputed()).isEqualTo(1);
        }

        @Test
        void countsNothingBeforeAnythingWasRaised() {
            assertThat(Defect.summarise(list, d("2025-01-01")).outstanding()).isZero();
        }
    }

    @Nested
    class CertificateBlockTest {

        private final LocalDate ends = d("2026-11-01");

        @Test
        void refusesBeforeTheDefectsPeriodHasRun() {
            assertThat(Defect.certificateBlock(List.of(), ends, d("2026-01-01")))
                    .contains(CertificateBlock.PERIOD_NOT_ENDED);
        }

        @Test
        void issuesOnceThePeriodHasRunAndNothingIsOpen() {
            assertThat(Defect.certificateBlock(List.of(), ends, d("2026-11-01")))
                    .isEmpty();
            assertThat(Defect.canIssueDefectsCertificate(List.of(), ends, d("2026-11-01")))
                    .isTrue();
        }

        @Test
        void isStoppedByAnOpenSafetyDefectAboveEverythingElse() {
            assertThat(Defect.certificateBlock(List.of(SAFETY, MINOR), ends, d("2026-11-01")))
                    .contains(CertificateBlock.SAFETY_DEFECT_OPEN);
        }

        @Test
        void isStoppedByAnOpenContractorDefect() {
            assertThat(Defect.certificateBlock(List.of(MINOR), ends, d("2026-11-01")))
                    .contains(CertificateBlock.CONTRACTOR_DEFECTS_OPEN);
        }

        @Test
        void isStoppedWhileNobodyHasDecidedWhoCausedIt() {
            Defect disputed = Defect.build(BASE.withAttribution(Attribution.UNRESOLVED));
            assertThat(Defect.certificateBlock(List.of(disputed), ends, d("2026-11-01")))
                    .contains(CertificateBlock.ATTRIBUTION_UNRESOLVED);
        }

        @Test
        void isNotStoppedByADefectTheEmployerCaused() {
            Defect employerItem = Defect.build(BASE.withAttribution(Attribution.EMPLOYER));
            assertThat(Defect.certificateBlock(List.of(employerItem), ends, d("2026-11-01")))
                    .isEmpty();
        }

        @Test
        void isNotStoppedOnceEverythingHasBeenPutRight() {
            Defect done = Defect.build(BASE.withRectifiedOn(d("2025-11-20")));
            assertThat(Defect.canIssueDefectsCertificate(List.of(done), ends, d("2026-11-01")))
                    .isTrue();
        }
    }

    @Nested
    class Describe {

        @Test
        void readsLikeAListLineWhileItIsOpen() {
            assertThat(MINOR.describe())
                    .isEqualTo("DEF/2025/011 minor, due 2025-12-01: surface course ravelling at the joint");
        }

        @Test
        void saysWhenItWasPutRight() {
            Defect done = Defect.build(BASE.withRectifiedOn(d("2025-11-20")));
            assertThat(done.describe()).contains("put right 2025-11-20");
        }
    }
}
