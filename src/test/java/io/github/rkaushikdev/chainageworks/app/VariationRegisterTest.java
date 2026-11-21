package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.variation.ValuationBasis;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.reporting.VariationReport;
import io.github.rkaushikdev.chainageworks.support.CliHarness;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The variation register at a valuation, through the service, the report, the API and the command line. */
class VariationRegisterTest {

    private static final ApiClient API = ApiClient.over(new Service(commercialJob(), Config.DEFAULT));

    private static Service service() {
        return new Service(commercialJob(), Config.DEFAULT);
    }

    private static Run commercial(String... argv) {
        return CliHarness.runCommand(List.of(argv), Options.none().withJob(commercialJob()));
    }

    @Nested
    class VariationsFor {

        private final VariationReport report = service().variationsFor(3);

        @Test
        void listsEveryOrderOnTheRegisterInForceOrNot() {
            assertThat(report.lines())
                    .extracting(line -> line.variation().id())
                    .containsExactly("VO-01", "VO-02", "VO-03");
        }

        @Test
        void valuesOnlyTheOnesInForce() {
            assertThat(report.lines().get(0).inForce()).isTrue();
            assertThat(report.lines().get(2).inForce()).isFalse();
            assertThat(report.lines().get(2).value().minor()).isZero();
        }

        @Test
        void valuesEachOrderAgainstTheUnvariedBill() {
            assertThat(report.lines().get(0).value().major()).isEqualTo(2425);
        }

        @Test
        void repricesTheWholeItemWhenAnOrderIsPricedProRata() {
            // The pro rata rate applies to everything under that code, not only to
            // the extra quantity, which is what makes the second order so much
            // larger than the first.
            assertThat(report.lines().get(1).value().major()).isEqualTo(63_130);
        }

        @Test
        void addsTheOnesInForceUp() {
            assertThat(report.inForce().major()).isEqualTo(65_555);
        }

        @Test
        void keepsWhatNobodyHasAgreedApartFromTheTotal() {
            assertThat(report.unagreed().major()).isEqualTo(2425);
        }

        @Test
        void countsTheProposedOrdersRatherThanValuingThem() {
            assertThat(report.proposed()).isEqualTo(1);
        }

        @Test
        void namesTheWaysEachOrderIsPriced() {
            assertThat(report.lines().get(0).bases()).containsExactly(ValuationBasis.BILL_RATES);
            assertThat(report.lines().get(1).bases()).containsExactly(ValuationBasis.PRO_RATA);
        }

        @Test
        void isEmptyOnAJobWithNoOrders() {
            VariationReport bare = new Service(sampleJob(), Config.DEFAULT).variationsFor(3);
            assertThat(bare.lines()).isEmpty();
            assertThat(bare.inForce().minor()).isZero();
            assertThat(bare.proposed()).isZero();
        }

        @Test
        void leavesOutAnOrderInstructedAfterTheCutOff() {
            VariationReport early = service().variationsFor(1);
            assertThat(early.lines()).allMatch(line -> !line.inForce());
            assertThat(early.inForce().minor()).isZero();
        }
    }

    @Nested
    class ChaseAgreement {

        @Test
        void namesTheOrdersInForceWithNoAgreedPriceDearestFirst() {
            List<String> chase = service().variationsFor(3).chaseAgreement();
            assertThat(chase).hasSize(1);
            assertThat(chase.get(0)).contains("VO/2025/001");
            assertThat(chase.get(0)).contains("not agreed");
        }
    }

    @Nested
    class TouchedItems {

        @Test
        void listsWhatTheOrdersInForceMoveForTheRemeasure() {
            assertThat(service().variationsFor(3).touchedItems())
                    .containsExactly("E423.1 500.00 m3", "E532.1 200.00 m3");
        }
    }

    @Nested
    class RenderVariationReport {

        @Test
        void opensWithTheTotalsAndTablesTheRegister() {
            String text = service().variationsFor(3).render();
            assertThat(Text.split(text, "\n").get(0)).isEqualTo("variations at 2025-03-25");
            assertThat(text).containsPattern("orders on the register +3");
            assertThat(text).containsPattern("proposed, not instructed +1");
            assertThat(text).contains("12+300.000 to 12+500.000");
        }

        @Test
        void leavesTheTableOutOnAJobWithNoOrders() {
            String text =
                    new Service(sampleJob(), Config.DEFAULT).variationsFor(3).render();
            assertThat(text).doesNotContainPattern(Pattern.compile("^order +title +status", Pattern.MULTILINE));
            assertThat(text).containsPattern("orders on the register +0");
        }
    }

    @Nested
    class GetValuationsNumberVariations {

        @Test
        void answersWithTheRegisterAndTheChaseList() {
            JsonNode body = API.get("/valuations/3/variations").json();
            assertThat(body.get("orders")).hasSize(3);
            assertThat(body.get("inForce").get("amount").doubleValue()).isEqualTo(65_555);
            assertThat(body.get("proposed").doubleValue()).isEqualTo(1);
            assertThat(body.get("chase")).hasSize(1);
            assertThat(body.get("touchedItems")).hasSize(2);
        }
    }

    @Nested
    class CwVariations {

        @Test
        void printsTheRegister() {
            Run run = commercial("variations", "3");
            assertThat(run.code()).isZero();
            assertThat(run.out()).contains("VO/2025/002");
        }

        @Test
        void printsTheOrderCountAsJson() {
            JsonNode json = commercial("--json", "variations", "3").json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.asText()).isEqualTo("3");
        }
    }
}
