package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillDiff;
import io.github.rkaushikdev.chainageworks.domain.bill.BillDiff.ChangeKind;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemInput;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemKind;
import io.github.rkaushikdev.chainageworks.domain.bill.BillRevision;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.reporting.RevisionReport;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.BillFixtures;
import io.github.rkaushikdev.chainageworks.support.CliHarness;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The bill's revision history compared step by step, through the service, the HTTP API and the command line. */
class BillRevisionsTest {

    private static final ApiClient API = ApiClient.over(new Service(revisedJob(), Config.DEFAULT));

    /** The sample bill with the earthworks rate repriced and a star item added. */
    private static Job revisedJob() {
        Job job = sampleJob();
        Bill tendered = BillFixtures.sampleBill();

        List<BillItem> items = new ArrayList<>();
        for (BillItem item : tendered.items()) {
            items.add(
                    item.code().workClass().equals("E")
                                    && item.code().divisions().get(0) == 4
                            ? BillItem.build(new BillItemInput(
                                    item.code(),
                                    item.description(),
                                    Rate.of(6.4, Currency.GBP, Unit.M3),
                                    item.quantity(),
                                    item.kind(),
                                    item.chainage()))
                            : item);
        }
        items.add(BillItem.build(BillItemInput.of(
                        ItemCode.parse("E425.1"),
                        "excavation in rock, star rate",
                        Rate.of(24.5, Currency.GBP, Unit.M3),
                        Quantity.of(400, Unit.M3))
                .withKind(BillItemKind.STAR)));

        job.billRevisions()
                .add(new BillRevision(
                        "Rev B",
                        d("2025-03-01"),
                        Bill.build("Rev B", tendered.title(), tendered.currency(), items),
                        "rock rate agreed after the trial pits"));
        return job;
    }

    private static Service service() {
        return new Service(revisedJob(), Config.DEFAULT);
    }

    private static Service single() {
        return new Service(sampleJob(), Config.DEFAULT);
    }

    private static Run revisions(String... argv) {
        return CliHarness.runCommand(List.of(argv), Options.none().withJob(revisedJob()));
    }

    @Nested
    class RevisionsFor {

        @Test
        void comparesEachRevisionWithTheOneBeforeIt() {
            RevisionReport report = service().revisionsFor();
            assertThat(report.steps()).hasSize(1);
            assertThat(report.steps().get(0).from().reference()).isEqualTo("Rev A");
            assertThat(report.steps().get(0).to().reference()).isEqualTo("Rev B");
        }

        @Test
        void namesTheStepThatMovedARate() {
            RevisionReport report = service().revisionsFor();
            assertThat(report.ratesMoved()).hasSize(1);
        }

        @Test
        void pricesTheMovementTheRevisionMade() {
            RevisionReport report = service().revisionsFor();
            assertThat(report.steps().get(0).diff().netDelta().major()).isGreaterThan(0);
        }

        @Test
        void seesTheItemTheRevisionAdded() {
            List<ChangeKind> kinds = service().revisionsFor().steps().get(0).diff().changes().stream()
                    .map(BillDiff.Change::kind)
                    .toList();
            assertThat(kinds).contains(ChangeKind.ADDED);
            assertThat(kinds).contains(ChangeKind.RATE_CHANGED);
        }

        @Test
        void hasNoStepsOnAJobWithOneRevision() {
            RevisionReport report = single().revisionsFor();
            assertThat(report.steps()).isEmpty();
            assertThat(report.ratesMoved()).isEmpty();
        }
    }

    @Nested
    class RateChanges {

        @Test
        void namesOnlyTheRatesThatMovedWithTheRevisionThatMovedThem() {
            List<String> moves = service().revisionsFor().rateChanges();
            assertThat(moves).isNotEmpty();
            assertThat(moves.get(0)).contains("Rev B:");
            assertThat(moves.get(0)).contains(" to ");
        }

        @Test
        void namesNothingOnAJobWithOneRevision() {
            assertThat(single().revisionsFor().rateChanges()).isEmpty();
        }
    }

    @Nested
    class RenderRevisionReport {

        @Test
        void opensWithTheStepsAndThenSetsEachOneOut() {
            String text = service().revisionsFor().render();
            assertThat(Text.split(text, "\n").get(0)).isEqualTo("bill revisions");
            assertThat(text).containsPattern("steps that moved a rate +1");
            assertThat(text).contains("Rev A to Rev B, effective 2025-03-01");
            assertThat(text).contains("rate changed");
        }

        @Test
        void saysSoWhenThereIsNothingToCompare() {
            String text = single().revisionsFor().render();
            assertThat(text).containsPattern("steps +0");
            assertThat(text).doesNotContain("rate was");
        }
    }

    @Nested
    class GetRevisions {

        @Test
        void answersWithEachStepAndTheRateMovements() {
            JsonNode body = API.get("/revisions").json();
            assertThat(body.get("steps")).hasSize(1);
            assertThat(body.get("steps").get(0).get("from").asText()).isEqualTo("Rev A");
            assertThat(body.get("rateChanges")).isNotEmpty();
        }
    }

    @Nested
    class CwRevisions {

        @Test
        void printsTheReport() {
            Run run = revisions("revisions");
            assertThat(run.code()).isZero();
            assertThat(run.out()).contains("Rev A to Rev B");
        }

        @Test
        void printsOnlyTheRateMovementsWhenAsked() {
            Run run = revisions("revisions", "--rates");
            assertThat(run.out()).contains("Rev B:");
            assertThat(run.out()).doesNotContain("bill revisions");
        }

        @Test
        void printsTheStepCountAsJson() {
            JsonNode json = revisions("--json", "revisions").json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.asText()).isEqualTo("1");
        }
    }
}
