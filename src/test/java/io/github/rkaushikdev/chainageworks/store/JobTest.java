package io.github.rkaushikdev.chainageworks.store;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.emptyJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.domain.time.Standdown;
import io.github.rkaushikdev.chainageworks.domain.time.StanddownReason;
import io.github.rkaushikdev.chainageworks.progress.PackageEarning;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.valuation.PeriodSchedule;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JobTest {

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static Job sixDayJob() {
        return Job.create(
                JobSettingsInput.of("J2", "six day job", gbp(1000)).withWorkingDays(List.of(1, 2, 3, 4, 5, 6)));
    }

    @Nested
    class JobSettingsBuild {

        private final JobSettingsInput base = JobSettingsInput.of("J1", "a job", gbp(1000));

        @Test
        void fillsInTheContractDefaults() {
            JobSettings settings = JobSettings.build(base);
            assertThat(settings.currency()).isEqualTo(Currency.GBP);
            assertThat(settings.retention().rate().value()).isEqualTo(5);
            assertThat(settings.cutOffDay()).isEqualTo(25);
            assertThat(settings.issueDays()).isEqualTo(7);
            assertThat(settings.paymentDays()).isEqualTo(14);
            assertThat(settings.workingDays()).containsExactly(1, 2, 3, 4, 5);
            assertThat(settings.materialsAllowance()).isEmpty();
            assertThat(settings.substantialCompletionOn()).isEmpty();
        }

        @Test
        void takesWhatItIsGiven() {
            JobSettings settings = JobSettings.build(base.withCutOffDay(28)
                    .withIssueDays(3)
                    .withPaymentDays(30)
                    .withWorkingDays(List.of(1, 2, 3, 4, 5, 6))
                    .withMaterialsAllowance(Percent.of(85))
                    .withSubstantialCompletionOn(d("2025-11-01")));
            assertThat(settings.cutOffDay()).isEqualTo(28);
            assertThat(settings.workingDays()).containsExactly(1, 2, 3, 4, 5, 6);
            assertThat(settings.materialsAllowance().orElseThrow().value()).isEqualTo(85);
        }

        @Test
        void refusesACutOffDayThatIsNotADayOfTheMonth() {
            assertThatThrownBy(() -> JobSettings.build(base.withCutOffDay(0))).hasMessageContaining("day of the month");
            assertThatThrownBy(() -> JobSettings.build(base.withCutOffDay(32)))
                    .hasMessageContaining("day of the month");
        }

        @Test
        void refusesANegativeContractSum() {
            assertThatThrownBy(() -> JobSettings.build(base.withContractSum(gbp(-1))))
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        void refusesAContractSumInAnotherCurrency() {
            assertThatThrownBy(() ->
                            JobSettings.build(base.withCurrency(Currency.EUR).withContractSum(gbp(1000))))
                    .hasMessageContaining("different currency");
        }
    }

    @Nested
    class CreateJob {

        @Test
        void startsWithTheStandardRulesOfCreditAndNothingElse() {
            Job job = emptyJob();
            assertThat(job.profiles().count()).isEqualTo(7);
            assertThat(job.packages().count()).isZero();
            assertThat(job.billRevisions().count()).isZero();
            assertThat(job.holidays()).isEmpty();
        }

        @Test
        void namesEveryRepositoryForItsErrorMessages() {
            Job job = emptyJob();
            assertThatThrownBy(() -> job.packages().require("WP-99"))
                    .hasMessageContaining("no work package with id WP-99");
            assertThatThrownBy(() -> job.records().require("SR-99"))
                    .hasMessageContaining("no survey record with id SR-99");
        }
    }

    @Nested
    class JobCalendar {

        @Test
        void buildsFromTheSettingsAndTheHolidayList() {
            Job job = emptyJob();
            job.setHolidays(List.of(d("2025-04-18")));
            SiteCalendar calendar = job.calendar();
            assertThat(calendar.id()).isEqualTo("A1-IMP-calendar");
            assertThat(calendar.isWorkingDay(d("2025-04-18"))).isFalse();
            assertThat(calendar.isWorkingDay(d("2025-04-17"))).isTrue();
        }

        @Test
        void honoursASixDayWeek() {
            assertThat(sixDayJob().calendar().isWorkingDay(d("2025-03-15"))).isTrue();
        }

        @Test
        void carriesTheStanddownsThrough() {
            Job job = emptyJob();
            job.setStanddowns(List.of(new Standdown(d("2025-03-05"), StanddownReason.WEATHER)));
            assertThat(job.calendar().standdowns()).hasSize(1);
        }
    }

    @Nested
    class JobContext {

        @Test
        void assemblesAContextTheEngineCanValueFrom() {
            ProgressContext context = sampleJob().context();
            assertThat(context.register().packages()).hasSize(3);
            assertThat(context.records()).hasSize(3);
            assertThat(context.plans()).hasSize(2);
            assertThat(PackageEarning.earnAll(context, d("2025-03-31"))
                            .get(0)
                            .earnedValue()
                            .major())
                    .isEqualTo(16_152.5);
        }

        @Test
        void refusesAJobWithNoBill() {
            assertThatThrownBy(() -> emptyJob().context()).isInstanceOf(BadInputException.class);
            assertThatThrownBy(() -> emptyJob().context()).hasMessageContaining("no bill yet");
        }

        @Test
        void picksUpARecordAddedAfterTheLastCall() {
            Job job = sampleJob();
            assertThat(job.context().records()).hasSize(3);
            job.records().remove("SR-03");
            assertThat(job.context().records()).hasSize(2);
        }
    }

    @Nested
    class JobSchedule {

        @Test
        void buildsFromTheJobCutOffAndLags() {
            PeriodSchedule schedule = sampleJob().schedule(2025, 1, 3);
            assertThat(schedule.periods()).hasSize(3);
            assertThat(SiteDates.format(schedule.periods().get(2).cutOff())).isEqualTo("2025-03-25");
            assertThat(SiteDates.format(schedule.periods().get(2).issuedOn())).isEqualTo("2025-04-03");
        }

        @Test
        void movesTheCutOffWhenTheJobWorksSixDays() {
            assertThat(SiteDates.format(
                            sixDayJob().schedule(2025, 5, 1).periods().get(0).cutOff()))
                    .isEqualTo("2025-05-24");
        }
    }

    @Nested
    class JobBlockers {

        @Test
        void namesEverythingAFreshJobIsMissing() {
            assertThat(emptyJob().blockers())
                    .containsExactly(
                            "no bill has been loaded",
                            "no work packages have been set up",
                            "no alignment has been loaded");
            assertThat(emptyJob().isReady()).isFalse();
        }

        @Test
        void saysNothingAboutAJobThatIsReady() {
            assertThat(sampleJob().blockers()).isEmpty();
            assertThat(sampleJob().isReady()).isTrue();
        }
    }
}
