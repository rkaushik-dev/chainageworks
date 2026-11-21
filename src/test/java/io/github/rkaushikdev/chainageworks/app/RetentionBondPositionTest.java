package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.reporting.format.PlainText;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.CliHarness;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.support.JobDocuments;
import io.github.rkaushikdev.chainageworks.valuation.BondPosition;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.RetentionBond;
import io.github.rkaushikdev.chainageworks.valuation.RetentionBondInput;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Certificates on a job whose retention is covered by a bond, through the service, the API, the CLI and the job file. */
class RetentionBondPositionTest {

    private static final ApiClient API = ApiClient.over(new Service(bondedJob(), Config.DEFAULT));

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static Job bondedJob() {
        Job job = sampleJob();
        job.setSettings(job.settings()
                .withRetentionBond(RetentionBond.build(
                        RetentionBondInput.of("RB-01", "Northgate Surety", gbp(9365), d("2024-11-01"), d("2026-11-01"))
                                .withRate(Percent.of(1.2)))));
        return job;
    }

    private static Job lapsedJob() {
        Job job = sampleJob();
        job.setSettings(job.settings()
                .withRetentionBond(RetentionBond.build(RetentionBondInput.of(
                        "RB-02", "Northgate Surety", gbp(9365), d("2024-11-01"), d("2025-02-01")))));
        return job;
    }

    private static Service bonded() {
        return new Service(bondedJob(), Config.DEFAULT);
    }

    private static Service cash() {
        return new Service(sampleJob(), Config.DEFAULT);
    }

    private static BondPosition bondOf(Certificate certificate) {
        return certificate.bond().orElseThrow();
    }

    @Nested
    class ACertificateWithARetentionBond {

        @Test
        void stillWorksTheRetentionOutTheSameWay() {
            assertThat(bonded().certificateFor(3).retention().held().major())
                    .isEqualTo(cash().certificateFor(3).retention().held().major());
        }

        @Test
        void holdsNoCashWhileTheBondCoversIt() {
            Certificate certificate = bonded().certificateFor(3);
            assertThat(certificate.retentionHeld().minor()).isZero();
            assertThat(certificate.bondReleased().major()).isEqualTo(807.63);
        }

        @Test
        void paysTheGrossValuationAcrossRatherThanTheNetOfRetention() {
            Certificate certificate = bonded().certificateFor(3);
            assertThat(certificate.netValuation().major())
                    .isEqualTo(certificate.gross().major());
            assertThat(certificate.amountDue().major()).isEqualTo(16_152.5);
        }

        @Test
        void paysMoreThanTheCashRetentionJobDoes() {
            assertThat(bonded().certificateFor(3).amountDue().major())
                    .isGreaterThan(cash().certificateFor(3).amountDue().major());
        }

        @Test
        void reportsHowLongTheBondHasLeft() {
            assertThat(bondOf(bonded().certificateFor(3)).daysToExpiry()).isGreaterThan(0);
            assertThat(bonded().certificateFor(3).bondHasLapsed()).isFalse();
        }
    }

    @Nested
    class ACertificateAfterTheBondHasLapsed {

        @Test
        void goesBackToHoldingTheCash() {
            Certificate certificate = new Service(lapsedJob(), Config.DEFAULT).certificateFor(3);
            assertThat(certificate.bondHasLapsed()).isTrue();
            assertThat(certificate.retentionHeld().major()).isEqualTo(807.63);
            assertThat(bondOf(certificate).released().minor()).isZero();
        }

        @Test
        void coveredTheCashWhileItWasStillInForce() {
            Certificate early = new Service(lapsedJob(), Config.DEFAULT).certificateFor(1);
            assertThat(early.bondHasLapsed()).isFalse();
        }
    }

    @Nested
    class AJobWithNoBondAtAll {

        @Test
        void reportsNoBondPositionAndHoldsTheCash() {
            Certificate certificate = cash().certificateFor(3);
            assertThat(certificate.bond()).isEmpty();
            assertThat(certificate.retentionHeld().major()).isEqualTo(807.63);
            assertThat(certificate.bondReleased().minor()).isZero();
        }
    }

    @Nested
    class ASeriesWithABond {

        @Test
        void carriesTheSameNetFigureForwardEachMonth() {
            List<Certificate> series = bonded().certificateSeries(4);
            for (int index = 0; index < series.size(); index++) {
                double previous =
                        index == 0 ? 0 : series.get(index - 1).netValuation().major();
                assertThat(series.get(index).previouslyCertified().major()).isEqualTo(previous);
            }
        }
    }

    @Nested
    class RenderCertificateWithABond {

        @Test
        void printsTheRetentionDueAndWhatTheBondCovers() {
            String text = PlainText.certificate(bonded().certificateFor(3));
            assertThat(text).contains("retention due");
            assertThat(text).contains("covered by bond RB-01");
            assertThat(text).containsPattern("less retention +£0\\.00");
        }

        @Test
        void leavesBothLinesOffACashRetentionJob() {
            String text = PlainText.certificate(cash().certificateFor(3));
            assertThat(text).doesNotContain("retention due");
            assertThat(text).doesNotContain("covered by bond");
        }
    }

    @Nested
    class GetValuationsNumberCertificateWithABond {

        @Test
        void carriesTheBondPosition() {
            JsonNode body = API.get("/valuations/3/certificate").json();
            JsonNode bond = body.get("bond");
            assertThat(bond.isObject()).isTrue();
            assertThat(bond.get("id").asText()).isEqualTo("RB-01");
            assertThat(bond.get("released").get("amount").doubleValue()).isEqualTo(807.63);
            assertThat(bond.get("expired").booleanValue()).isFalse();
            assertThat(body.get("retentionHeld").get("amount").doubleValue()).isZero();
        }
    }

    @Nested
    class TheBondThroughTheJobFile {

        @Test
        void roundTripsTheBondAndItsTerms() {
            Job again = JobDocuments.throughText(bondedJob());
            RetentionBond bond = again.settings().retentionBond().orElseThrow();
            assertThat(bond.id()).isEqualTo("RB-01");
            assertThat(bond.surety()).isEqualTo("Northgate Surety");
            assertThat(bond.maximum().major()).isEqualTo(9365);
            assertThat(bond.reducesTo()).isEmpty();
        }

        @Test
        void carriesAJobWithNoBondThroughAsNull() {
            Job again = JobDocuments.throughText(sampleJob());
            assertThat(again.settings().retentionBond()).isEmpty();
        }
    }

    @Nested
    class CwCertificateWithABond {

        @Test
        void printsTheBondLine() {
            Run run = CliHarness.runCommand(
                    List.of("certificate", "3"), Options.none().withJob(bondedJob()));
            assertThat(run.code()).isZero();
            assertThat(run.out()).contains("covered by bond RB-01");
        }
    }
}
