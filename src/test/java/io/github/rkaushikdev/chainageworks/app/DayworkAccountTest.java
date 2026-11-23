package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkRate;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSchedule;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkScheduleInput;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSheet;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSheetInput;
import io.github.rkaushikdev.chainageworks.domain.daywork.LabourEntry;
import io.github.rkaushikdev.chainageworks.domain.daywork.MaterialEntry;
import io.github.rkaushikdev.chainageworks.domain.daywork.PlantEntry;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.http.Dto;
import io.github.rkaushikdev.chainageworks.reporting.format.PlainText;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobDocuments;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Signed daywork through the certificate, on the printed page, over the wire and through the job file. */
class DayworkAccountTest {

    private static final DayworkSchedule SCHEDULE = DayworkSchedule.build(DayworkScheduleInput.of(
            List.of(DayworkRate.of("ganger", "ganger", 24), DayworkRate.of("labourer", "labourer", 19.5)),
            List.of(DayworkRate.of("excavator-13t", "13 tonne excavator", 42))));

    private static Job jobWithDaywork() {
        return jobWithDaywork("2025-03-13");
    }

    /** The sample job with the schedule and one sheet, signed on {@code signedOn} or unsigned when it is null. */
    private static Job jobWithDaywork(String signedOn) {
        Job job = sampleJob();
        job.setSettings(job.settings().withDaywork(SCHEDULE));
        job.dayworkSheets()
                .add(DayworkSheet.build(new DayworkSheetInput(
                        "DW-01",
                        "DW/2025/014",
                        "WP-01",
                        "EI-045",
                        d("2025-03-12"),
                        signedOn == null ? null : d(signedOn),
                        signedOn == null ? "" : "R Engineer",
                        List.of(new LabourEntry("ganger", 2, 8, 0)),
                        List.of(new PlantEntry("excavator-13t", 8, 0)),
                        List.of(new MaterialEntry("imported stone", Money.fromMajor(400, Currency.GBP))))));
        return job;
    }

    private static Certificate certificateFor(Job job, int valuation) {
        return new Service(job, Config.DEFAULT).certificateFor(valuation);
    }

    @Nested
    class DayworkOnTheCertificate {

        @Test
        void isNothingWhenTheContractHasNoDayworkSchedule() {
            Certificate certificate = certificateFor(sampleJob(), 3);
            assertThat(certificate.daywork().minor()).isZero();
            assertThat(certificate.gross().major()).isEqualTo(16_152.5);
        }

        @Test
        void addsASignedSheetIntoTheGross() {
            Certificate certificate = certificateFor(jobWithDaywork(), 3);
            assertThat(certificate.daywork().major()).isEqualTo(1549.92);
            assertThat(certificate.gross().major()).isEqualTo(16_152.5 + 1549.92);
        }

        @Test
        void leavesAnUnsignedSheetOutHoweverMuchWorkIsOnIt() {
            assertThat(certificateFor(jobWithDaywork(null), 3).daywork().minor())
                    .isZero();
        }

        @Test
        void picksTheSheetUpInThePeriodTheSignatureLandsIn() {
            Service service = new Service(jobWithDaywork("2025-04-02"), Config.DEFAULT);
            assertThat(service.certificateFor(3).daywork().minor()).isZero();
            assertThat(service.certificateFor(4).daywork().major()).isEqualTo(1549.92);
        }

        @Test
        void holdsRetentionOnTheDayworkLikeEverythingElse() {
            assertThat(certificateFor(jobWithDaywork(), 3).retention().accrued().major())
                    .isEqualTo(885.12);
        }

        @Test
        void showsOnThePrintedCertificate() {
            assertThat(PlainText.certificate(certificateFor(jobWithDaywork(), 3)))
                    .containsPattern("daywork +£1,549\\.92");
        }

        @Test
        void showsOnTheWire() {
            assertThat(Dto.certificate(certificateFor(jobWithDaywork(), 3))
                            .get("daywork")
                            .get("amount")
                            .doubleValue())
                    .isEqualTo(1549.92);
        }
    }

    @Nested
    class DayworkThroughTheJobFile {

        @Test
        void roundTripsTheScheduleAndTheSheets() {
            Job again = JobDocuments.throughText(jobWithDaywork());
            DayworkSchedule schedule = again.settings().daywork().orElseThrow();
            assertThat(schedule.labour()).hasSize(2);
            assertThat(schedule.overtimeFactor()).isEqualTo(1.5);
            assertThat(again.dayworkSheets().count()).isEqualTo(1);
            DayworkSheet sheet = again.dayworkSheets().require("DW-01");
            assertThat(sheet.labour().get(0).people()).isEqualTo(2);
            assertThat(sheet.materials().get(0).value().major()).isEqualTo(400);
        }

        @Test
        void valuesTheSameAfterTheRoundTrip() {
            Job again = JobDocuments.throughText(jobWithDaywork());
            assertThat(certificateFor(again, 3).daywork().major()).isEqualTo(1549.92);
        }

        @Test
        void keepsStandingPlantAndTheStandingRate() {
            Job job = sampleJob();
            job.setSettings(job.settings()
                    .withDaywork(DayworkSchedule.build(DayworkScheduleInput.of(
                                    List.of(), List.of(DayworkRate.of("excavator-13t", "13 tonne excavator", 42)))
                            .withStandingFactor(0.6))));
            job.dayworkSheets()
                    .add(DayworkSheet.build(new DayworkSheetInput(
                            "DW-02",
                            "DW/2025/015",
                            "WP-01",
                            "EI-046",
                            d("2025-03-12"),
                            d("2025-03-13"),
                            "R Engineer",
                            List.of(),
                            List.of(new PlantEntry("excavator-13t", 4, 0, 3)),
                            List.of())));
            Job again = JobDocuments.throughText(job);
            assertThat(again.settings().daywork().orElseThrow().standingFactor())
                    .isEqualTo(0.6);
            assertThat(again.dayworkSheets().require("DW-02").plant().get(0).standingHours())
                    .isEqualTo(3);
            assertThat(JobDocuments.throughText(jobWithDaywork())
                            .dayworkSheets()
                            .require("DW-01")
                            .plant()
                            .get(0)
                            .standingHours())
                    .isZero();
        }

        @Test
        void keepsTheDeadlineAndTheDayASheetWentIn() {
            Job job = jobWithDaywork();
            job.setSettings(job.settings()
                    .withDaywork(DayworkSchedule.build(DayworkScheduleInput.of(
                                    List.of(DayworkRate.of("ganger", "ganger", 24)),
                                    List.of(DayworkRate.of("excavator-13t", "13 tonne excavator", 42)))
                            .withSubmissionDays(7))));
            DayworkSheet sheet = job.dayworkSheets().require("DW-01");
            job.dayworkSheets()
                    .put(DayworkSheet.build(new DayworkSheetInput(
                                    sheet.id(),
                                    sheet.reference(),
                                    sheet.packageId(),
                                    sheet.instruction(),
                                    sheet.workedOn(),
                                    sheet.signedOn().orElseThrow(),
                                    sheet.signedBy(),
                                    sheet.labour(),
                                    sheet.plant(),
                                    sheet.materials())
                            .withSubmittedOn(d("2025-03-25"))));
            Job again = JobDocuments.throughText(job);
            assertThat(again.settings().daywork().orElseThrow().submissionDays())
                    .contains(7);
            assertThat(again.dayworkSheets().require("DW-01").submittedOn()).contains(d("2025-03-25"));
            assertThat(certificateFor(again, 3).daywork().minor()).isZero();
            assertThat(certificateFor(jobWithDaywork(), 3).daywork().minor()).isPositive();
        }

        @Test
        void carriesAJobWithNoDayworkThroughAsNull() {
            Job again = JobDocuments.throughText(sampleJob());
            assertThat(again.settings().daywork()).isEmpty();
            assertThat(again.dayworkSheets().count()).isZero();
        }
    }
}
