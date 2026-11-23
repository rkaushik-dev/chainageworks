package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.haulingJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulAllocation;
import io.github.rkaushikdev.chainageworks.domain.earthworks.SiteKind;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.reporting.HaulReport;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.CliHarness;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.support.JobDocuments;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The haul plan worked out from the job's sites, through the service, the reports, the API, the CLI and the job file. */
class HaulPlanTest {

    private static final ApiClient API = ApiClient.over(new Service(haulingJob(), Config.DEFAULT));

    private static Service service() {
        return new Service(haulingJob(), Config.DEFAULT);
    }

    private static HaulAllocation planFor(Job job) {
        return new Service(job, Config.DEFAULT).haulPlan();
    }

    private static Run hauling(String... argv) {
        return CliHarness.runCommand(List.of(argv), Options.none().withJob(haulingJob()));
    }

    @Nested
    class HaulPlan {

        @Test
        void splitsTheRegisterIntoSourcesAndDestinationsByKind() {
            HaulAllocation plan = service().haulPlan();
            assertThat(plan.movements())
                    .extracting(movement -> movement.fromId() + "->" + movement.toId())
                    .containsExactly("CUT-A->EMB-B", "BOR-1->EMB-B");
        }

        @Test
        void pricesThePlanOffTheJobHaulRate() {
            assertThat(service().haulPlan().cost().major()).isEqualTo(23_000);
        }

        @Test
        void leavesTheSurplusUnplacedRatherThanTippingBoughtMaterial() {
            HaulAllocation plan = service().haulPlan();
            assertThat(plan.unplaced().amount()).isEqualTo(3000);
            assertThat(plan.spareTipCapacity().amount()).isEqualTo(5000);
        }

        @Test
        void isAnEmptyPlanOnAJobWithNoSitesAtAll() {
            HaulAllocation plan = planFor(commercialJob());
            assertThat(plan.movements()).isEmpty();
            assertThat(plan.cost().minor()).isZero();
        }
    }

    @Nested
    class RenderHaulPlan {

        @Test
        void opensWithTheTotalsAndNamesTheSitesInFull() {
            HaulAllocation plan = service().haulPlan();
            String text = HaulReport.render(plan, haulingJob().haulSites().all());
            assertThat(Text.split(text, "\n").get(0)).isEqualTo("haul plan");
            assertThat(text).containsPattern("movements +2");
            assertThat(text).containsPattern("balanced +no");
            assertThat(text).contains("CUT-A cutting at 12+400");
        }

        @Test
        void putsTheDearestMovementFirst() {
            HaulAllocation plan = service().haulPlan();
            String text = HaulReport.render(plan, haulingJob().haulSites().all());
            List<String> rows = Text.split(text, "\n").stream()
                    .filter(line -> line.startsWith("CUT-A") || line.startsWith("BOR-1"))
                    .toList();
            assertThat(rows.get(0)).startsWith("CUT-A");
        }

        @Test
        void leavesTheTableOutWhenNothingMoves() {
            HaulAllocation plan = planFor(commercialJob());
            assertThat(HaulReport.render(plan, List.of())).doesNotContain("haul moment  0");
        }
    }

    @Nested
    class MuckAwaySchedule {

        @Test
        void saysWhatHasToLeaveEachSourceAndWhereItGoes() {
            List<String> schedule = HaulReport.muckAwaySchedule(
                    service().haulPlan(), haulingJob().haulSites().all());
            assertThat(schedule)
                    .containsExactly(
                            "BOR-1 borrow pit off the haul road, 1000 m3 to EMB-B",
                            "CUT-A cutting at 12+400, 6000 m3 to EMB-B");
        }

        @Test
        void saysSoWhenASourceHasNowhereToGo() {
            Job job = haulingJob();
            job.setSettings(job.settings().withHaulLinks(List.of()));
            List<String> schedule =
                    HaulReport.muckAwaySchedule(planFor(job), job.haulSites().all());
            assertThat(schedule).allMatch(line -> line.endsWith("nowhere to go"));
        }
    }

    @Nested
    class GetHaul {

        @Test
        void answersWithTheMovementsAndTheTotals() {
            JsonNode body = API.get("/haul").json();
            assertThat(body.get("movements")).hasSize(2);
            assertThat(body.get("cost").get("amount").doubleValue()).isEqualTo(23_000);
            assertThat(body.get("balanced").isBoolean()).isTrue();
            assertThat(body.get("balanced").booleanValue()).isFalse();
        }

        @Test
        void countsTheHaulSitesOnTheJob() {
            JsonNode counts = API.get("/job").json().get("counts");
            assertThat(counts.get("haulSites").doubleValue()).isEqualTo(4);
        }
    }

    @Nested
    class CwHaul {

        @Test
        void printsThePlan() {
            Run run = hauling("haul");
            assertThat(run.code()).isZero();
            assertThat(run.outLines().get(0)).isEqualTo("haul plan");
        }

        @Test
        void printsOnlyTheMuckAwayScheduleWhenAsked() {
            Run run = hauling("haul", "--muck-away");
            assertThat(run.outLines()).hasSize(2);
            assertThat(run.out()).contains("6000 m3 to EMB-B");
        }

        @Test
        void printsTheMovementCountAsJson() {
            JsonNode json = hauling("--json", "haul").json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.asText()).isEqualTo("2");
        }
    }

    @Nested
    class TheHaulRegisterThroughTheJobFile {

        @Test
        void roundTripsTheSitesTheLinksAndTheRate() {
            Job again = JobDocuments.throughText(haulingJob());
            assertThat(again.haulSites().count()).isEqualTo(4);
            assertThat(again.haulSites().require("BOR-1").kind()).isEqualTo(SiteKind.BORROW);
            assertThat(again.settings().haulLinks()).hasSize(3);
            assertThat(again.settings().haulRate().major()).isEqualTo(1);
        }

        @Test
        void givesTheSamePlanOffTheReloadedJob() {
            Job again = JobDocuments.throughText(haulingJob());
            assertThat(planFor(again).cost().major()).isEqualTo(23_000);
        }

        @Test
        void keepsTheFreeHaulAndPricesThePlanOnIt() {
            Job job = haulingJob();
            job.setSettings(job.settings().withFreeHaulKm(1));
            Job again = JobDocuments.throughText(job);
            assertThat(again.settings().freeHaulKm()).isEqualTo(1);
            assertThat(planFor(again).cost().minor())
                    .isLessThan(planFor(haulingJob()).cost().minor());
            assertThat(JobDocuments.throughText(haulingJob()).settings().freeHaulKm())
                    .isZero();
        }

        @Test
        void carriesAJobWithNoHaulSetUpThroughAsEmpty() {
            Job again = JobDocuments.throughText(sampleJob());
            assertThat(again.haulSites().count()).isZero();
            assertThat(again.settings().haulLinks()).isEmpty();
            assertThat(again.settings().haulRate().minor()).isZero();
        }
    }
}
