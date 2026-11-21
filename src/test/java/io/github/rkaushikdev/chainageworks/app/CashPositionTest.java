package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.CliHarness.runCommand;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.http.ApiClient.Answer;
import io.github.rkaushikdev.chainageworks.reporting.format.PlainText;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.util.Text;
import io.github.rkaushikdev.chainageworks.valuation.CashFlow;
import io.github.rkaushikdev.chainageworks.valuation.CashPoint;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The certificate series and the cash forecast run off it, through the service, the HTTP API and the command line. */
class CashPositionTest {

    private static final ApiClient API = ApiClient.over(new Service(sampleJob(), Config.DEFAULT));

    private static Service service() {
        return new Service(sampleJob(), Config.DEFAULT);
    }

    @Nested
    class CertificateSeries {

        @Test
        void runsFromPeriodOneWhateverIsAskedFor() {
            List<Certificate> series = service().certificateSeries(4);
            assertThat(series)
                    .extracting(certificate -> certificate.period().number())
                    .containsExactly(1, 2, 3, 4);
        }

        @Test
        void givesTheSameCertificateTheSingleCallGives() {
            Certificate fromSeries = service().certificateSeries(3).get(2);
            Certificate alone = service().certificateFor(3);
            assertThat(fromSeries.amountDue().major())
                    .isEqualTo(alone.amountDue().major());
            assertThat(fromSeries.netValuation().major())
                    .isEqualTo(alone.netValuation().major());
        }
    }

    @Nested
    class CashFlowFor {

        @Test
        void forecastsOnePointForEveryPeriodAskedFor() {
            List<CashPoint> points = service().cashFlowFor(6);
            assertThat(points).hasSize(6);
            assertThat(SiteDates.format(points.get(2).cutOff())).isEqualTo("2025-03-25");
        }

        @Test
        void takesTheCertifiedSeriesOffTheCertificatesThemselves() {
            List<CashPoint> points = service().cashFlowFor(4);
            List<Certificate> series = service().certificateSeries(4);
            assertThat(points.stream().map(point -> point.certified().major()).toList())
                    .isEqualTo(series.stream()
                            .map(certificate -> certificate.netValuation().major())
                            .toList());
        }

        @Test
        void owesNothingAlongTheWayWhenThePayerIsOnTime() {
            assertThat(service().cashFlowFor(6))
                    .allMatch(point -> point.outstanding().minor() == 0);
        }

        @Test
        void holdsAPeriodOfWorkBackWhenThePayerRunsAPeriodBehind() {
            List<CashPoint> points = service().cashFlowFor(6, 1);
            assertThat(points.get(2).outstanding().major())
                    .isEqualTo(points.get(2).certificate().major());
            assertThat(points.get(0).receipt().minor()).isZero();
        }

        @Test
        void neverBringsCashInEarlierThanTheOnTimeForecast() {
            List<CashPoint> onTime = service().cashFlowFor(6);
            List<CashPoint> behind = service().cashFlowFor(6, 1);
            for (int index = 0; index < behind.size(); index++) {
                assertThat(behind.get(index).received().minor())
                        .isLessThanOrEqualTo(onTime.get(index).received().minor());
            }
            assertThat(behind.get(2).received().major())
                    .isLessThan(onTime.get(2).received().major());
        }

        @Test
        void bringsTheSameCashInEventuallyOnceTheWorksStopMoving() {
            double onTime = CashFlow.totalReceipts(service().cashFlowFor(6), Currency.GBP)
                    .major();
            double behind = CashFlow.totalReceipts(service().cashFlowFor(6, 1), Currency.GBP)
                    .major();
            assertThat(behind).isEqualTo(onTime);
        }
    }

    @Nested
    class RenderCashFlow {

        @Test
        void tablesThePeriodsAndNamesTheWorstMonth() {
            String text = PlainText.cashFlow(service().cashFlowFor(6, 1));
            assertThat(Text.split(text, "\n").get(0)).containsPattern("^period +cut off +due +certificate");
            assertThat(text).contains("most owed at once");
        }

        @Test
        void leavesThePeakLineOutWhenNothingIsEverOwed() {
            assertThat(PlainText.cashFlow(service().cashFlowFor(6))).doesNotContain("most owed at once");
        }
    }

    @Nested
    class GetCash {

        @Test
        void givesTwelvePeriodsByDefault() {
            JsonNode body = API.get("/cash").json();
            assertThat(body.get("points")).hasSize(12);
        }

        @Test
        void takesThePeriodsAndTheLateFlagOffTheQuery() {
            JsonNode body = API.get("/cash?periods=4&late=1").json();
            assertThat(body.get("points")).hasSize(4);
            assertThat(body.get("peak").isObject()).isTrue();
            assertThat(body.get("points")
                            .get(0)
                            .get("outstanding")
                            .get("amount")
                            .doubleValue())
                    .isGreaterThanOrEqualTo(0);
        }

        @Test
        void reportsNoPeakAtAllWhenNothingIsEverOwed() {
            JsonNode body = API.get("/cash?periods=4&late=0").json();
            assertThat(body.has("peak")).isTrue();
            assertThat(body.get("peak").isNull()).isTrue();
        }

        @Test
        void refusesALateCountThatIsNotAWholeNumberFromZero() {
            Answer response = API.get("/cash?late=soon");
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.json().get("message").asText()).contains("whole number from zero");
        }
    }

    @Nested
    class CwCash {

        @Test
        void printsTheForecast() {
            Run run = runCommand("cash", "-n", "6");
            assertThat(run.code()).isZero();
            assertThat(run.out()).containsPattern("^period +cut off +due ");
        }

        @Test
        void takesTheLatePeriodsOffTheFlag() {
            Run run = runCommand("cash", "-n", "6", "--late", "1");
            assertThat(run.out()).contains("most owed at once");
        }

        @Test
        void printsThePointCountAsJson() {
            JsonNode json = runCommand("--json", "cash", "-n", "5").json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.asText()).isEqualTo("5");
        }
    }

    @Nested
    class PeakExposureOverTheRealSeries {

        @Test
        void isThePeriodTheJobHasMostWorkCertifiedAndUnpaid() {
            CashPoint peak = CashFlow.peakExposure(service().cashFlowFor(6, 1)).orElseThrow();
            assertThat(peak.period()).isGreaterThan(1);
            assertThat(peak.outstanding().minor()).isGreaterThan(0);
        }
    }
}
