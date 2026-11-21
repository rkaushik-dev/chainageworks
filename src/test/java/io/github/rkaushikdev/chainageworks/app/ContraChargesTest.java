package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraCharge;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraChargeInput;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraReason;
import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.reporting.SubcontractReport.BarredContra;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.CliHarness;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.support.JobDocuments;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Contra charges set off against the subcontract payments, through the service, the API, the CLI and the job file. */
class ContraChargesTest {

    private static final ApiClient API = ApiClient.over(new Service(commercialJob(), Config.DEFAULT));

    private static Service service() {
        return new Service(commercialJob(), Config.DEFAULT);
    }

    private static Subcontract.Position order(Service service, int index) {
        return service.paymentsFor(3).positions().get(index);
    }

    private static Subcontract.Position earthworks(Job job) {
        return order(new Service(job, Config.DEFAULT), 0);
    }

    private static ContraCharge welfare(String id, String description, long minor, String incurredOn) {
        return ContraCharge.build(
                ContraChargeInput.of(id, "SC-EARTH", description, Money.of(minor, Currency.GBP), d(incurredOn))
                        .withReason(ContraReason.WELFARE));
    }

    @Nested
    class TheContraPositionOnAPaymentReport {

        @Test
        void setsOffWhatTheOrderCoversAndWhatWasNotifiedInTime() {
            Subcontract.Position earth = order(service(), 0);
            assertThat(earth.contra().raised().major()).isEqualTo(6950);
            assertThat(earth.contra().deductible().major()).isEqualTo(6000);
        }

        @Test
        void barsTheChargeNobodyNotifiedOnceItsWindowHasShut() {
            Subcontract.Position earth = order(service(), 0);
            assertThat(earth.contra().barred().major()).isEqualTo(950);
        }

        @Test
        void reportsTheDisputedPartOfWhatIsBeingTaken() {
            Subcontract.Position earth = order(service(), 0);
            assertThat(earth.contra().disputed().major()).isEqualTo(4200);
        }

        @Test
        void takesTheSetOffOutOfWhatActuallyLeavesTheBank() {
            Subcontract.Position earth = order(service(), 0);
            assertThat(earth.paidToDate().major()).isEqualTo(80_750);
            assertThat(earth.netOfContra().major()).isEqualTo(74_750);
            assertThat(earth.contraCarriedForward().minor()).isZero();
        }

        @Test
        void leavesTheCertifiedFigureAlone() {
            Subcontract.Position earth = order(service(), 0);
            assertThat(earth.certifiedToDate().major()).isEqualTo(80_750);
            assertThat(earth.remaining().major()).isEqualTo(169_250);
        }

        @Test
        void chargesNothingAgainstAnOrderNobodyHasCharged() {
            Subcontract.Position fence = order(service(), 1);
            assertThat(fence.contra().charges()).isEmpty();
            assertThat(fence.netOfContra().minor()).isZero();
        }

        @Test
        void namesTheOrdersCarryingBarredSetOff() {
            assertThat(service().paymentsFor(3).barredContra())
                    .containsExactly(new BarredContra("SC-EARTH", "£950.00"));
        }

        @Test
        void carriesTheRestForwardWhenTheSetOffIsLargerThanThePayment() {
            Job job = commercialJob();
            job.contraCharges()
                    .add(welfare("CC-99", "cabins and drying room for the whole gang", 12_000_000, "2025-03-02"));
            Subcontract.Position earth = earthworks(job);
            assertThat(earth.netOfContra().minor()).isZero();
            assertThat(earth.contraCarriedForward().major()).isEqualTo(45_250);
        }

        @Test
        void leavesOutAChargeIncurredAfterTheCutOff() {
            Job job = commercialJob();
            job.contraCharges().add(welfare("CC-98", "cabins for april", 100_000, "2025-04-02"));
            Subcontract.Position earth = earthworks(job);
            assertThat(earth.contra().raised().major()).isEqualTo(6950);
        }
    }

    @Nested
    class RenderSubcontractReportWithSetOff {

        @Test
        void tablesTheSetOffAndTheNetAlongsideThePaidFigure() {
            String text = service().paymentsFor(3).render();
            assertThat(text).containsPattern("set off barred +1");
            assertThat(text).contains("set off");
            assertThat(text).contains("£6,000.00");
        }
    }

    @Nested
    class GetValuationsNumberPaymentsWithSetOff {

        @Test
        void carriesTheContraPositionAndTheBarredList() {
            JsonNode body = API.get("/valuations/3/payments").json();
            assertThat(body.get("barredContra"))
                    .isEqualTo(Json.valueOf(List.of(Map.of("subcontractId", "SC-EARTH", "amount", "£950.00"))));
            JsonNode first = body.get("orders").get(0);
            assertThat(first.get("contra").get("deductible").get("amount").doubleValue())
                    .isEqualTo(6000);
            List<String> ids = new ArrayList<>();
            first.get("contra")
                    .get("charges")
                    .forEach(charge -> ids.add(charge.get("id").asText()));
            assertThat(ids).containsExactly("CC-01", "CC-03", "CC-02");
            assertThat(first.get("netOfContra").get("amount").doubleValue()).isEqualTo(74_750);
        }
    }

    @Nested
    class CwPaymentsWithSetOff {

        @Test
        void printsTheSetOffColumn() {
            Run run = CliHarness.runCommand(
                    List.of("payments", "3"), Options.none().withJob(commercialJob()));
            assertThat(run.code()).isZero();
            assertThat(run.out()).contains("£74,750.00");
        }
    }

    @Nested
    class TheContraChargesThroughTheJobFile {

        @Test
        void roundTripsTheChargesAndTheNoticeWindow() {
            Job again = JobDocuments.throughText(commercialJob());
            assertThat(again.contraCharges().count()).isEqualTo(3);
            assertThat(again.contraCharges().require("CC-02").disputed()).isTrue();
            assertThat(again.contraCharges().require("CC-03").notifiedOn()).isEmpty();
            assertThat(again.settings().contraNotifyDays()).isEqualTo(14);
        }

        @Test
        void givesTheSamePositionOffTheReloadedJob() {
            Job again = JobDocuments.throughText(commercialJob());
            assertThat(earthworks(again).netOfContra().major()).isEqualTo(74_750);
        }

        @Test
        void carriesAJobWithNoChargesThroughAsEmpty() {
            Job again = JobDocuments.throughText(sampleJob());
            assertThat(again.contraCharges().count()).isZero();
        }
    }
}
