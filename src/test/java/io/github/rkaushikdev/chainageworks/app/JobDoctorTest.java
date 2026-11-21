package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.range;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.emptyJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.haulingJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulLink;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.Weighting;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOffInput;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentNotice;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.http.ApiClient.Answer;
import io.github.rkaushikdev.chainageworks.progress.OutputAllowance;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.CliHarness;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The doctor's findings on jobs set up well and badly, read directly, over the API and from the command line. */
class JobDoctorTest {

    private static final ApiClient API = ApiClient.over(new Service(haulingJob(), Config.DEFAULT));

    private static Run doctor(Job job, String... argv) {
        return CliHarness.runCommand(List.of(argv), Options.none().withJob(job));
    }

    @Nested
    class AJobThatIsSetUpProperly {

        private final Diagnosis diagnosis = Doctor.diagnose(haulingJob());

        @Test
        void hasNoErrorsAndNoBlockers() {
            assertThat(diagnosis.errors()).isZero();
            assertThat(diagnosis.blockers()).isEmpty();
            assertThat(diagnosis.isHealthy()).isTrue();
        }

        @Test
        void stillWarnsAboutThePackagesNothingHasBeenMeasuredAgainst() {
            assertThat(diagnosis.findingsIn("packages"))
                    .extracting(Finding::subject)
                    .containsExactly("WP-02", "WP-03");
            assertThat(diagnosis.warnings()).isGreaterThan(0);
        }
    }

    @Nested
    class AJobWithNothingLoaded {

        @Test
        void reportsTheBlockersRatherThanAWallOfFindings() {
            Diagnosis diagnosis = Doctor.diagnose(emptyJob());
            assertThat(diagnosis.blockers()).hasSize(3);
            assertThat(diagnosis.findings()).isEmpty();
            assertThat(diagnosis.isHealthy()).isFalse();
        }
    }

    @Nested
    class ASignOffAgainstAPointNoPlanCarries {

        @Test
        void isAnErrorBecauseItGatesNothingAndNobodyNotices() {
            Job job = sampleJob();
            job.signOffs()
                    .add(SignOff.build(SignOffInput.of(
                            "SO-99", "IP-NOWHERE", "WP-01", range("12+000", "12+400"), d("2025-03-12"), "R Engineer")));
            Diagnosis diagnosis = Doctor.diagnose(job);
            assertThat(diagnosis.errors()).isEqualTo(1);
            assertThat(diagnosis.findingsIn("sign offs").get(0).message()).contains("gates nothing");
        }
    }

    @Nested
    class APaymentApplicationAgainstAnOrderNobodyPlaced {

        @Test
        void isAnError() {
            Job job = commercialJob();
            job.paymentApplications()
                    .add(new PaymentApplication(
                            "SA-99",
                            "SC-NOWHERE",
                            1,
                            d("2025-03-25"),
                            Money.of(100_000, Currency.GBP),
                            Money.of(0, Currency.GBP)));
            Diagnosis diagnosis = Doctor.diagnose(job);
            assertThat(diagnosis.findingsIn("applications").get(0).subject()).isEqualTo("SA-99");
        }
    }

    @Nested
    class ANoticeAnsweringNothing {

        @Test
        void isAnErrorAgainstTheApplicationItNames() {
            Job job = commercialJob();
            job.paymentNotices().add(new PaymentNotice("SA-NOWHERE", d("2025-03-25"), Money.of(100_000, Currency.GBP)));
            assertThat(Doctor.diagnose(job).findingsIn("notices").get(0).subject())
                    .isEqualTo("SA-NOWHERE");
        }
    }

    @Nested
    class AFluctuationClauseWithNoBulletinBehindIt {

        @Test
        void isAnErrorBecauseNoCertificateCanBeBuiltAtAll() {
            Job job = commercialJob();
            job.setSettings(job.settings()
                    .withAdjustment(new AdjustmentTerms(
                            d("2024-10-25"),
                            Percent.ofBasisPoints(0),
                            List.of(new Weighting("IDX-MISSING", Percent.ofBasisPoints(10_000))))));
            Diagnosis diagnosis = Doctor.diagnose(job);
            assertThat(diagnosis.findingsIn("fluctuations").get(0).message()).contains("no certificate");
            assertThat(diagnosis.isHealthy()).isFalse();
        }
    }

    @Nested
    class AHaulLinkNamingASiteNobodyLoaded {

        @Test
        void isOnlyAWarningBecauseThePlanStillBuilds() {
            Job job = haulingJob();
            List<HaulLink> links = new ArrayList<>(job.settings().haulLinks());
            links.add(new HaulLink("CUT-Z", "EMB-B", 1));
            job.setSettings(job.settings().withHaulLinks(links));
            Diagnosis diagnosis = Doctor.diagnose(job);
            assertThat(diagnosis.errors()).isZero();
            assertThat(diagnosis.findingsIn("haul").get(0).message()).isEqualTo("no haul site CUT-Z");
        }
    }

    @Nested
    class AnOutputAllowanceForAStepNothingHas {

        @Test
        void isAWarningThatTheAllowanceIsNeverUsed() {
            Job job = sampleJob();
            job.setSettings(job.settings().withOutputAllowances(List.of(new OutputAllowance("landscaping", 5))));
            assertThat(Doctor.diagnose(job).findingsIn("allowances").get(0).message())
                    .contains("never used");
        }
    }

    @Nested
    class GetDoctor {

        @Test
        void answers200WithTheFindings() {
            Answer response = API.get("/doctor");
            assertThat(response.status()).isEqualTo(200);
            JsonNode body = response.json();
            assertThat(body.get("healthy").booleanValue()).isTrue();
            assertThat(body.get("errors").isNumber()).isTrue();
            assertThat(body.get("errors").doubleValue()).isZero();
            assertThat(body.get("findings")).isNotEmpty();
        }
    }

    @Nested
    class CwDoctor {

        @Test
        void saysSoWhenNothingIsWrong() {
            Run run = doctor(haulingJob(), "doctor");
            assertThat(run.code()).isZero();
            assertThat(run.out()).matches("nothing wrong, \\d+ warnings not shown");
        }

        @Test
        void printsTheWarningsWhenAskedForThem() {
            Run run = doctor(haulingJob(), "doctor", "--all");
            assertThat(run.code()).isZero();
            assertThat(run.out()).contains("warning packages WP-02");
        }

        @Test
        void exitsNonZeroOnAJobWithErrors() {
            Run run = doctor(emptyJob(), "doctor");
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.out()).contains("error job: no bill has been loaded");
        }

        @Test
        void printsTheWholeDiagnosisAsJson() {
            JsonNode body = doctor(haulingJob(), "--json", "doctor").json();
            assertThat(body.get("errors").isNumber()).isTrue();
            assertThat(body.get("errors").doubleValue()).isZero();
            assertThat(body.get("warnings").doubleValue()).isGreaterThan(0);
        }
    }
}
