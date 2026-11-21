package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayCause;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayEvent;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayEventInput;
import io.github.rkaushikdev.chainageworks.domain.delay.Entitlement;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.reporting.DelayReport;
import io.github.rkaushikdev.chainageworks.reporting.DelayReport.DelayLine;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobDocuments;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The extension of time position read through the service at a valuation cut-off, and kept through the job file. */
class DelayPositionTest {

    private static final CompletionTerms TERMS =
            CompletionTerms.build(d("2025-09-26"), Money.fromMajor(2500, Currency.GBP), Percent.of(10), null);

    private static Job jobWithDelay() {
        Job job = sampleJob();
        job.setSettings(job.settings().withCompletion(TERMS));
        job.delayEvents()
                .add(DelayEvent.build(DelayEventInput.of(
                                "DE-01",
                                "EW/2025/003",
                                DelayCause.UTILITIES,
                                "live water main outside its recorded position",
                                d("2025-03-03"),
                                d("2025-03-14"))
                        .withNotifiedOn(d("2025-03-05"))
                        .withAwardedDays(8)));
        job.delayEvents()
                .add(DelayEvent.build(DelayEventInput.of(
                                "DE-02",
                                "EW/2025/004",
                                DelayCause.DESIGN,
                                "revised drainage layout issued late",
                                d("2025-03-10"),
                                d("2025-03-21"))
                        .withNotifiedOn(null)));
        job.delayEvents()
                .add(DelayEvent.build(DelayEventInput.of(
                                "DE-03",
                                "EW/2025/005",
                                DelayCause.WEATHER,
                                "exceptional rainfall, no earthworks",
                                d("2025-03-17"),
                                d("2025-03-19"))
                        .withNotifiedOn(d("2025-03-18"))));
        return job;
    }

    private static DelayReport delayAt(Job job, int valuation) {
        return new Service(job, Config.DEFAULT).delayFor(valuation).orElseThrow();
    }

    @Nested
    class DelayFor {

        @Test
        void isNullWhenTheContractFixesNoCompletionDate() {
            assertThat(new Service(sampleJob(), Config.DEFAULT).delayFor(3)).isEmpty();
        }

        @Test
        void reportsThePositionAtTheCutOff() {
            DelayReport report = delayAt(jobWithDelay(), 3);
            assertThat(SiteDates.format(report.asAt())).isEqualTo("2025-03-25");
            assertThat(report.lines()).hasSize(3);
            assertThat(report.position().awardedDays()).isEqualTo(11);
            assertThat(SiteDates.format(report.position().extendedCompletion())).isEqualTo("2025-10-13");
        }

        @Test
        void separatesWhatWasClaimedFromWhatWasAwarded() {
            DelayReport report = delayAt(jobWithDelay(), 3);
            assertThat(report.claimedDays()).isEqualTo(10 + 10 + 3);
            assertThat(report.position().barredDays()).isEqualTo(10);
            assertThat(report.barredLines()).extracting(DelayLine::reference).containsExactly("EW/2025/004");
        }

        @Test
        void listsTheEventsStillWaitingOnAnAssessment() {
            DelayReport report = delayAt(jobWithDelay(), 3);
            assertThat(report.unassessedLines())
                    .extracting(DelayLine::reference)
                    .containsExactly("EW/2025/005");
        }

        @Test
        void saysWhichEventsCarryMoneyAndWhichOnlyCarryTime() {
            DelayReport report = delayAt(jobWithDelay(), 3);
            Map<String, DelayLine> byRef =
                    report.lines().stream().collect(Collectors.toMap(DelayLine::reference, Function.identity()));
            assertThat(byRef.get("EW/2025/003").carriesMoney()).isTrue();
            assertThat(byRef.get("EW/2025/005").carriesMoney()).isFalse();
            assertThat(byRef.get("EW/2025/005").entitlement()).isEqualTo(Entitlement.TIME_ONLY);
        }

        @Test
        void carriesNoDamagesWhileTheWorksAreInsideTheExtendedDate() {
            DelayReport report = delayAt(jobWithDelay(), 3);
            assertThat(report.daysLate()).isZero();
            assertThat(report.damages().minor()).isZero();
        }

        @Test
        void chargesDamagesOnceTheExtendedDateHasPassed() {
            DelayReport report = delayAt(jobWithDelay(), 11);
            assertThat(report.daysLate()).isGreaterThan(0);
            assertThat(report.damages().major()).isGreaterThan(0);
        }
    }

    @Nested
    class RenderDelayReport {

        @Test
        void opensWithThePositionAndTablesTheEvents() {
            String text = delayAt(jobWithDelay(), 3).render();
            assertThat(Text.split(text, "\n").get(0)).isEqualTo("extension of time position at 2025-03-25");
            assertThat(text).containsPattern("days awarded +11");
            assertThat(text).containsPattern("days barred +10");
            assertThat(text).contains("not notified");
            assertThat(text).contains("EW/2025/003");
        }
    }

    @Nested
    class TheDelayRegisterThroughTheJobFile {

        @Test
        void roundTripsTheTermsAndTheEvents() {
            Job again = JobDocuments.throughText(jobWithDelay());
            CompletionTerms terms = again.settings().completion().orElseThrow();
            assertThat(SiteDates.format(terms.baseCompletion())).isEqualTo("2025-09-26");
            assertThat(terms.damagesPerDay().major()).isEqualTo(2500);
            assertThat(again.delayEvents().count()).isEqualTo(3);
            assertThat(again.delayEvents().require("DE-01").awardedDays()).contains(8);
            assertThat(again.delayEvents().require("DE-02").notifiedOn()).isEmpty();
        }

        @Test
        void reportsTheSamePositionAfterTheRoundTrip() {
            Job again = JobDocuments.throughText(jobWithDelay());
            assertThat(delayAt(again, 3).position().awardedDays()).isEqualTo(11);
        }

        @Test
        void carriesAJobWithNoCompletionTermsThroughAsNull() {
            Job again = JobDocuments.throughText(sampleJob());
            assertThat(again.settings().completion()).isEmpty();
            assertThat(again.delayEvents().count()).isZero();
        }
    }
}
