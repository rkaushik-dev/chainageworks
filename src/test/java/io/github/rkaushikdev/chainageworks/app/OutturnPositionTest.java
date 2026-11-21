package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.fluctuatingJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.reporting.OutturnReport;
import io.github.rkaushikdev.chainageworks.support.CliHarness;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.util.Text;
import io.github.rkaushikdev.chainageworks.valuation.Outturn;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The anticipated final account at a valuation, through the service, the report, the API and the command line. */
class OutturnPositionTest {

    private static final ApiClient API = ApiClient.over(new Service(fluctuatingJob(), Config.DEFAULT));

    private static final Pattern ITEM_ROW = Pattern.compile("^[A-Z]\\d");

    private static Service fluctuating() {
        return new Service(fluctuatingJob(), Config.DEFAULT);
    }

    private static Service plain() {
        return new Service(sampleJob(), Config.DEFAULT);
    }

    @Nested
    class OutturnFor {

        @Test
        void runsAtTheValuationCutOff() {
            Outturn outturn = plain().outturnFor(3);
            assertThat(outturn.contractSum().major()).isEqualTo(312_190);
            assertThat(outturn.partsAddUp()).isTrue();
        }

        @Test
        void forecastsAJobWithNothingElseOnItAtTheBillTotal() {
            assertThat(plain().outturnFor(3).total().major()).isEqualTo(312_190);
        }

        @Test
        void carriesEveryTermTheContractHasIntoTheAccount() {
            Outturn outturn = fluctuating().outturnFor(3);
            assertThat(outturn.priceAdjustment().factor().lines()).hasSize(2);
            assertThat(outturn.damages().minor()).isZero();
            assertThat(outturn.total().major()).isGreaterThan(312_190);
        }

        @Test
        void saysTheAccountRestsOnAProvisionalIndex() {
            assertThat(fluctuating().outturnFor(3).priceAdjustment().provisional())
                    .isTrue();
            assertThat(fluctuating().outturnFor(2).priceAdjustment().provisional())
                    .isFalse();
        }

        @Test
        void leavesTheFixedPriceJobUnadjustedWhateverElseIsOnIt() {
            Outturn outturn = new Service(commercialJob(), Config.DEFAULT).outturnFor(3);
            assertThat(outturn.priceAdjustment().amount().minor()).isZero();
            assertThat(outturn.total().major())
                    .isEqualTo(312_190 + outturn.variations().major());
            assertThat(outturn.variations().major()).isGreaterThan(0);
        }

        @Test
        void stillAddsUpOnceTheClauseAndTheDamagesAreOn() {
            assertThat(fluctuating().outturnFor(3).partsAddUp()).isTrue();
        }
    }

    @Nested
    class RenderOutturn {

        @Test
        void setsThePartsOutAndThenThePosition() {
            String text = OutturnReport.render(fluctuating().outturnFor(3));
            assertThat(Text.split(text, "\n").get(0)).isEqualTo("anticipated final account at 2025-03-25");
            assertThat(text).contains("measured works");
            assertThat(text).contains("liquidated damages");
            assertThat(text).containsPattern("parts add up +yes");
            assertThat(text).containsPattern("price adjustment +on a provisional index");
        }

        @Test
        void leavesTheMovingLinesOutWhenNothingHasMoved() {
            String text = OutturnReport.render(plain().outturnFor(1));
            assertThat(text).doesNotContain("items moving away from the bill");
        }

        @Test
        void showsOnlyTheWorstFewMovingLines() {
            Outturn outturn = fluctuating().outturnFor(3);
            String text = OutturnReport.render(outturn, 2);
            List<String> rows = Text.split(text, "\n").stream()
                    .filter(line -> ITEM_ROW.matcher(line).find())
                    .toList();
            assertThat(rows.size()).isLessThanOrEqualTo(2);
        }
    }

    @Nested
    class GetValuationsNumberOutturn {

        @Test
        void answersWithThePartsAndThePosition() {
            JsonNode body = API.get("/valuations/3/outturn").json();
            List<String> names = new ArrayList<>();
            body.get("parts").forEach(part -> names.add(part.get("name").asText()));
            assertThat(names)
                    .containsExactly(
                            "measured works", "variations", "daywork", "price adjustment", "liquidated damages");
            assertThat(body.get("overContractSum").booleanValue()).isTrue();
            assertThat(body.get("provisionalAdjustment").booleanValue()).isTrue();
            assertThat(body.get("total").get("amount").doubleValue()).isGreaterThan(312_190);
        }

        @Test
        void givesALineForEveryBillItem() {
            JsonNode lines = API.get("/valuations/3/outturn").json().get("lines");
            assertThat(lines).isNotEmpty();
            assertThat(lines).allMatch(line -> !line.get("code").asText().isEmpty());
        }
    }

    @Nested
    class CwOutturn {

        @Test
        void printsTheAccount() {
            Run run = CliHarness.runCommand(
                    List.of("outturn", "3"), Options.none().withJob(fluctuatingJob()));
            assertThat(run.code()).isZero();
            assertThat(run.outLines().get(0)).isEqualTo("anticipated final account at 2025-03-25");
        }

        @Test
        void printsTheTotalInMinorUnitsAsJson() {
            JsonNode json = CliHarness.runCommand(
                            List.of("--json", "outturn", "3"), Options.none().withJob(sampleJob()))
                    .json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.asText()).isEqualTo("31219000");
        }
    }
}
