package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.resource.HourKind;
import io.github.rkaushikdev.chainageworks.http.ApiClient;
import io.github.rkaushikdev.chainageworks.progress.OutputAllowance;
import io.github.rkaushikdev.chainageworks.progress.PackageOutput;
import io.github.rkaushikdev.chainageworks.reporting.OutputReport;
import io.github.rkaushikdev.chainageworks.reporting.OutputReport.UnallocatedHours;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.CliHarness;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.support.JobDocuments;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** What the job produced for the hours it booked, through the service, the report, the API, the CLI and the job file. */
class OutputPositionTest {

    private static final ApiClient API = ApiClient.over(new Service(commercialJob(), Config.DEFAULT));

    private static Service service() {
        return new Service(commercialJob(), Config.DEFAULT);
    }

    /** A JSON value as plain lists, maps, strings and numbers, so it can be compared whole. */
    private static Object plain(JsonNode value) {
        return Json.mapper().convertValue(value, Object.class);
    }

    @Nested
    class OutputFor {

        @Test
        void reportsEveryPackageAtTheValuationCutOff() {
            OutputReport report = service().outputFor(3);
            assertThat(report.packages())
                    .extracting(PackageOutput::packageId)
                    .containsExactly("WP-01", "WP-02", "WP-03");
            assertThat(report.totals().returns()).isEqualTo(4);
            assertThat(report.totals().labourHours()).isEqualTo(152);
        }

        @Test
        void namesTheStepRunningBehindItsAllowance() {
            OutputReport report = service().outputFor(3);
            assertThat(report.behind()).hasSize(1);
            assertThat(report.behind().get(0).packageId()).isEqualTo("WP-01");
            assertThat(report.behind().get(0).step().stepId()).isEqualTo("strip");
            assertThat(report.behind().get(0).step().ratio().getAsDouble()).isCloseTo(0.8333, byLessThan(5e-5));
        }

        @Test
        void holdsBackTheHoursNobodyBookedToAStep() {
            assertThat(service().outputFor(3).unallocatedHours()).containsExactly(new UnallocatedHours("WP-01", 16));
        }

        @Test
        void pricesTheStandingPlantAsTimeThatBoughtNothing() {
            OutputReport report = service().outputFor(3);
            assertThat(report.totals().standingHours()).isEqualTo(4);
            assertThat(report.lostFraction()).isCloseTo(4.0 / 164, byLessThan(5e-6));
        }

        @Test
        void isAnHonestSetOfZeroesOnAJobWithNoReturns() {
            OutputReport report = new Service(sampleJob(), Config.DEFAULT).outputFor(3);
            assertThat(report.totals().labourHours()).isZero();
            assertThat(report.behind()).isEmpty();
            assertThat(report.barren()).isEmpty();
        }
    }

    @Nested
    class RenderOutputReport {

        @Test
        void opensWithTheTotalsAndTablesTheStepsThatWereWorked() {
            String text = service().outputFor(3).render();
            assertThat(Text.split(text, "\n").get(0)).isEqualTo("output at 2025-03-25");
            assertThat(text).containsPattern("labour hours +152");
            assertThat(text).containsPattern("steps behind the allowance +1");
            assertThat(text).contains("m per hr");
        }

        @Test
        void leavesTheTableOutWhenNothingHasBeenWorkedOrMeasured() {
            Service clean = new Service(sampleJob(), Config.DEFAULT);
            String text = clean.outputFor(1).render();
            assertThat(text).doesNotContain("m per hr");
            assertThat(text).containsPattern("labour hours +0");
        }
    }

    @Nested
    class GetValuationsNumberOutput {

        @Test
        void answersWithTheTotalsAndTheStepsBehind() {
            JsonNode body = API.get("/valuations/3/output").json();
            assertThat(body.get("asAt").asText()).isEqualTo("2025-03-25");
            assertThat(plain(body.get("behind")))
                    .isEqualTo(List.of(Map.of("packageId", "WP-01", "stepId", "strip", "ratio", 800.0 / 96 / 10)));
            assertThat(plain(body.get("unallocated"))).isEqualTo(List.of(Map.of("packageId", "WP-01", "hours", 16)));
        }

        @Test
        void countsTheResourceReturnsOnTheJob() {
            JsonNode counts = API.get("/job").json().get("counts");
            assertThat(counts.get("resourceReturns").doubleValue()).isEqualTo(4);
        }
    }

    @Nested
    class CwOutput {

        @Test
        void printsTheOutputReport() {
            Run run =
                    CliHarness.runCommand(List.of("output", "3"), Options.none().withJob(commercialJob()));
            assertThat(run.code()).isZero();
            assertThat(run.out()).containsPattern("plant hours +12");
        }

        @Test
        void printsTheLabourHoursAsJson() {
            JsonNode json = CliHarness.runCommand(
                            List.of("--json", "output", "3"), Options.none().withJob(commercialJob()))
                    .json();
            assertThat(json.isNumber()).isTrue();
            assertThat(json.doubleValue()).isEqualTo(152);
        }
    }

    @Nested
    class CwImportReturns {

        private final String csv = String.join(
                "\n",
                "return,package,step,date,shift,kind,resource,description,count,hours,hour kind,returned by",
                "RR-10,WP-02,strip,2025-03-17,day,labour,GANG-EARTH,earthworks gang,5,9,,D Illingworth",
                "RR-10,WP-02,strip,2025-03-17,day,plant,PL-D6,dozer,1,9,,D Illingworth");

        @Test
        void loadsAReturnExportAndSavesTheJob() {
            Run run = CliHarness.runCommand(
                    List.of("import-returns", "returns.csv"),
                    Options.none().withJob(commercialJob()).withFiles(Map.of("returns.csv", csv)));
            assertThat(run.code()).isZero();
            assertThat(run.out()).contains("read 1 resource returns, took 1");
            assertThat(run.saved()).hasSize(1);
            assertThat(run.saved().get(0).job().resourceReturns().count()).isEqualTo(5);
        }

        @Test
        void putsTheLoadedHoursIntoTheNextOutputReport() {
            Service loaded = service();
            assertThat(loaded.loadResourceReturns(csv).rowsAccepted()).isEqualTo(1);
            OutputReport report = loaded.outputFor(3);
            assertThat(report.packages().get(1).labourHours()).isEqualTo(45);
            assertThat(report.totals().plantHours()).isEqualTo(21);
        }
    }

    @Nested
    class TheResourceReturnsThroughTheJobFile {

        @Test
        void roundTripsTheReturnsAndTheOutputAllowances() {
            Job again = JobDocuments.throughText(commercialJob());
            assertThat(again.resourceReturns().count()).isEqualTo(4);
            assertThat(again.resourceReturns().require("RR-03").plant().get(0).kind())
                    .isEqualTo(HourKind.STANDING);
            assertThat(again.resourceReturns().require("RR-04").stepId()).isEmpty();
            assertThat(again.settings().outputAllowances())
                    .containsExactly(new OutputAllowance("strip", 10), new OutputAllowance("excavate", 12));
        }

        @Test
        void carriesAJobWithNoReturnsThroughAsEmpty() {
            Job again = JobDocuments.throughText(sampleJob());
            assertThat(again.resourceReturns().count()).isZero();
            assertThat(again.settings().outputAllowances()).isEmpty();
        }
    }
}
