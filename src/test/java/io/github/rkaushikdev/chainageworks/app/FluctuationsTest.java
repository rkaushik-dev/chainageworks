package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.fluctuatingJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentFactor;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexSeries;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.ingest.ImportReport;
import io.github.rkaushikdev.chainageworks.reporting.format.PlainText;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.store.codec.JobCodec;
import io.github.rkaushikdev.chainageworks.support.CliHarness;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.support.JobDocuments;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A fluctuation clause moving the certificate, through the service, the API, the index import and the job file.
 */
class FluctuationsTest {

    private static final ApiClient API = ApiClient.over(new Service(fluctuatingJob(), Config.DEFAULT));

    private static final String BULLETIN = String.join(
            "\n",
            "index,name,year,month,value,provisional",
            "IDX-LAB,civil engineering labour,2025,4,124,yes",
            "IDX-LAB,civil engineering labour,2025,5,126,yes",
            "IDX-STL,structural steel,2025,4,88,no");

    private static Service fluctuating() {
        return new Service(fluctuatingJob(), Config.DEFAULT);
    }

    private static Service fixed() {
        return new Service(commercialJob(), Config.DEFAULT);
    }

    @Nested
    class AJobWithAFluctuationClause {

        @Test
        void movesTheCertificateAboveTheFixedPriceOne() {
            Certificate adjusted = fluctuating().certificateFor(3);
            Certificate plain = fixed().certificateFor(3);
            assertThat(adjusted.measuredWork().major())
                    .isEqualTo(plain.measuredWork().major());
            assertThat(adjusted.gross().major()).isGreaterThan(plain.gross().major());
        }

        @Test
        void worksTheFactorOutOfBothIndices() {
            Certificate certificate = fluctuating().certificateFor(3);
            assertThat(certificate.priceAdjustment().factor().lines())
                    .extracting(AdjustmentFactor.Line::indexId)
                    .containsExactly("IDX-LAB", "IDX-PLA");
            assertThat(certificate.priceAdjustment().factor().factor())
                    .isCloseTo(0.6 * 0.2 + 0.3 * 0.025, byLessThan(5e-11));
        }

        @Test
        void saysTheCertificateRestsOnAProvisionalFigure() {
            assertThat(fluctuating().certificateFor(3).priceAdjustment().provisional())
                    .isTrue();
            assertThat(fluctuating().certificateFor(2).priceAdjustment().provisional())
                    .isFalse();
        }

        @Test
        void namesEveryCertificateThatWillNeedReworking() {
            List<Certificate> series = fluctuating().certificateSeries(4);
            assertThat(Certificate.provisional(series))
                    .extracting(certificate -> certificate.period().number())
                    .containsExactly(3, 4);
        }

        @Test
        void leavesTheFixedPriceJobEntirelyAlone() {
            Certificate certificate = fixed().certificateFor(3);
            assertThat(certificate.priceAdjustment().amount().minor()).isZero();
            assertThat(certificate.priceAdjustment().factor().lines()).isEmpty();
        }
    }

    @Nested
    class RenderCertificateWithAFluctuationClause {

        @Test
        void printsTheAdjustmentLineAndTheProvisionalWarning() {
            String text = PlainText.certificate(fluctuating().certificateFor(3));
            assertThat(text).contains("price adjustment at 12.750%");
            assertThat(text).contains("rests on a provisional index figure");
        }

        @Test
        void leavesTheLineOffAFixedPriceCertificate() {
            String text = PlainText.certificate(fixed().certificateFor(3));
            assertThat(text).doesNotContain("price adjustment");
            assertThat(text).doesNotContain("provisional");
        }
    }

    @Nested
    class GetValuationsNumberCertificateWithAClause {

        @Test
        void carriesTheFactorAndTheIndicesBehindIt() {
            JsonNode adjustment = API.get("/valuations/3/certificate").json().get("priceAdjustment");
            assertThat(adjustment.get("provisional").booleanValue()).isTrue();
            assertThat(adjustment.get("indices")).hasSize(2);
            assertThat(adjustment.get("indices").get(0).get("movement").doubleValue())
                    .isCloseTo(0.2, byLessThan(5e-11));
        }

        @Test
        void countsTheIndexSeriesOnTheJob() {
            JsonNode counts = API.get("/job").json().get("counts");
            assertThat(counts.get("indices").doubleValue()).isEqualTo(2);
        }
    }

    @Nested
    class ImportingABulletin {

        @Test
        void groupsTheRowsIntoOneSeriesEach() {
            Service service = new Service(sampleJob(), Config.DEFAULT);
            ImportReport<IndexSeries> report = service.loadIndices(BULLETIN);
            assertThat(report.rowsAccepted()).isEqualTo(2);
            assertThat(service.job().indices().require("IDX-LAB").readings()).hasSize(2);
            assertThat(service.job().indices().require("IDX-STL").name()).isEqualTo("structural steel");
        }

        @Test
        void takesTheProvisionalFlagOffTheRow() {
            Service service = new Service(sampleJob(), Config.DEFAULT);
            service.loadIndices(BULLETIN);
            assertThat(service.job()
                            .indices()
                            .require("IDX-LAB")
                            .readings()
                            .get(0)
                            .provisional())
                    .isTrue();
            assertThat(service.job()
                            .indices()
                            .require("IDX-STL")
                            .readings()
                            .get(0)
                            .provisional())
                    .isFalse();
        }

        @Test
        void refusesABulletinWithTwoFiguresForOneMonth() {
            Service service = new Service(sampleJob(), Config.DEFAULT);
            ImportReport<IndexSeries> report = service.loadIndices(
                    String.join("\n", "index,year,month,value", "IDX-LAB,2025,4,124", "IDX-LAB,2025,4,125"));
            assertThat(report.errors().get(0).message()).contains("two figures for one month");
            assertThat(service.job().indices().count()).isZero();
        }

        @Test
        void loadsThroughTheCliAndSavesTheJob() {
            Run run = CliHarness.runCommand(
                    List.of("import-indices", "idx.csv"),
                    Options.none().withJob(fluctuatingJob()).withFiles(Map.of("idx.csv", BULLETIN)));
            assertThat(run.code()).isZero();
            assertThat(run.out()).contains("read 2 index series, took 2");
            assertThat(run.saved().get(0).job().indices().count()).isEqualTo(3);
        }
    }

    @Nested
    class TheFluctuationClauseThroughTheJobFile {

        @Test
        void roundTripsTheTermsAndTheBulletins() {
            Job again = JobDocuments.throughText(fluctuatingJob());
            assertThat(again.indices().count()).isEqualTo(2);
            assertThat(again.indices().require("IDX-LAB").readings()).hasSize(6);
            AdjustmentTerms terms = again.settings().adjustment().orElseThrow();
            assertThat(terms.weightings()).hasSize(2);
            assertThat(terms.nonAdjustable().basisPoints()).isEqualTo(1000);
        }

        @Test
        void givesTheSameCertificateOffTheReloadedJob() {
            Job again = JobDocuments.throughText(fluctuatingJob());
            Certificate reloaded = new Service(again, Config.DEFAULT).certificateFor(3);
            assertThat(reloaded.gross().major())
                    .isEqualTo(fluctuating().certificateFor(3).gross().major());
        }

        @Test
        void keepsTheIndexLagAndValuesAgainstTheEarlierMonth() {
            Job lagged = fluctuatingJob();
            AdjustmentTerms terms = lagged.settings().adjustment().orElseThrow();
            lagged.setSettings(lagged.settings().withAdjustment(terms.withLagMonths(1)));
            Job again = JobDocuments.throughText(lagged);
            assertThat(again.settings().adjustment().orElseThrow().lagMonths()).isEqualTo(1);
            Certificate certificate = new Service(again, Config.DEFAULT).certificateFor(3);
            assertThat(certificate.priceAdjustment().factor().indicesReadAt())
                    .isEqualTo(SiteDates.addMonths(certificate.period().cutOff(), -1));
            assertThat(JobDocuments.throughText(fluctuatingJob())
                            .settings()
                            .adjustment()
                            .orElseThrow()
                            .lagMonths())
                    .isZero();
        }

        @Test
        void keepsAClauseAppliedPeriodByPeriod() {
            Job perPeriod = fluctuatingJob();
            AdjustmentTerms terms = perPeriod.settings().adjustment().orElseThrow();
            perPeriod.setSettings(perPeriod.settings().withAdjustment(terms.withPerPeriod(true)));
            assertThat(clauseJson(perPeriod).path("appliedTo").asText()).isEqualTo("period");
            Job again = JobDocuments.throughText(perPeriod);
            assertThat(again.settings().adjustment().orElseThrow().perPeriod()).isTrue();
            assertThat(clauseJson(fluctuatingJob()).has("appliedTo")).isFalse();
        }

        @Test
        void readsACumulativeClauseAndRefusesAnythingElse() {
            ObjectNode doc = JobCodec.toJson(fluctuatingJob());
            ObjectNode clause = (ObjectNode) doc.path("settings").path("adjustment");
            clause.put("appliedTo", "cumulative");
            assertThat(JobCodec.fromJson(doc)
                            .settings()
                            .adjustment()
                            .orElseThrow()
                            .perPeriod())
                    .isFalse();
            clause.put("appliedTo", "quarter");
            assertThatThrownBy(() -> JobCodec.fromJson(doc)).hasMessageContaining("each period");
        }

        @Test
        void carriesAJobWithNoClauseThroughAsNull() {
            Job again = JobDocuments.throughText(sampleJob());
            assertThat(again.settings().adjustment()).isEmpty();
            assertThat(again.indices().count()).isZero();
        }
    }

    private static JsonNode clauseJson(Job job) {
        return JobCodec.toJson(job).path("settings").path("adjustment");
    }
}
