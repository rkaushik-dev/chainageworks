package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.CliHarness.runCommand;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.range;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.fluctuatingJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOffInput;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The month end pack, and the command that writes it out. */
class MonthEndPackTest {

    private static Service service() {
        return new Service(commercialJob(), Config.DEFAULT);
    }

    private static Run commercial(String... argv) {
        return runCommand(List.of(argv), Options.none().withJob(commercialJob()));
    }

    @Nested
    class Build {

        private final MonthEndPack pack = MonthEndPack.build(service(), 3);

        @Test
        void isBuiltToTheValuationCutOff() {
            assertThat(pack.valuation()).isEqualTo(3);
            assertThat(pack.cutOff()).isEqualTo("2025-03-25");
        }

        @Test
        void producesTheWholePackInOneCall() {
            assertThat(pack.files())
                    .extracting(PackFile::name)
                    .containsExactly(
                            "certificate.md",
                            "certificate.txt",
                            "certificate.csv",
                            "certificate-totals.csv",
                            "progress.md",
                            "payments.md",
                            "variations.txt",
                            "outturn.txt",
                            "outturn.csv",
                            "output.txt",
                            "risks.txt",
                            "delay.txt",
                            "defects.txt");
        }

        @Test
        void writesSomethingIntoEveryFile() {
            for (PackFile file : pack.files()) {
                assertThat(List.of(file.name(), !file.text().isEmpty())).isEqualTo(List.of(file.name(), true));
            }
        }

        @Test
        void carriesTheAmountDueSomewhereAPersonCanRead() {
            assertThat(pack.amountDue()).isEqualTo("15344.87");
        }

        @Test
        void checksTheJobBeforeItBuildsAnything() {
            assertThat(pack.isSound()).isTrue();
            assertThat(pack.diagnosis().errors()).isZero();
        }

        @Test
        void leavesTheDelayAndDefectsFilesOutOnAJobWithNoSuchTerms() {
            MonthEndPack bare = MonthEndPack.build(new Service(sampleJob(), Config.DEFAULT), 3);
            assertThat(bare.files()).extracting(PackFile::name).doesNotContain("delay.txt");
            assertThat(bare.files()).extracting(PackFile::name).doesNotContain("defects.txt");
            assertThat(bare.files()).hasSize(11);
        }

        @Test
        void carriesTheFluctuationClauseIntoTheCertificateItWrites() {
            MonthEndPack adjusted = MonthEndPack.build(new Service(fluctuatingJob(), Config.DEFAULT), 3);
            assertThat(adjusted.file("certificate.md").orElseThrow().text()).contains("Price adjustment at");
        }

        @Test
        void buildsThePackAnywayOnAJobWithABrokenReference() {
            Job job = commercialJob();
            job.signOffs()
                    .add(SignOff.build(SignOffInput.of(
                            "SO-99", "IP-NOWHERE", "WP-01", range("12+000", "12+400"), d("2025-03-12"), "R Engineer")));
            MonthEndPack broken = MonthEndPack.build(new Service(job, Config.DEFAULT), 3);
            assertThat(broken.isSound()).isFalse();
            assertThat(broken.diagnosis().errors()).isEqualTo(1);
            assertThat(broken.files().size()).isGreaterThan(10);
        }
    }

    @Nested
    class File {

        @Test
        void findsAFileByName() {
            MonthEndPack pack = MonthEndPack.build(service(), 3);
            assertThat(Text.split(pack.file("progress.md").orElseThrow().text(), "\n")
                            .get(0))
                    .isEqualTo("# Progress at 2025-03-25");
        }

        @Test
        void isNullForAFileThePackDoesNotCarry() {
            assertThat(MonthEndPack.build(service(), 3).file("insurances.txt")).isEmpty();
        }
    }

    @Nested
    class Describe {

        @Test
        void saysWhatWasBuiltAndWhetherTheJobChecksOut() {
            assertThat(MonthEndPack.build(service(), 3).describe())
                    .isEqualTo("valuation 3 to 2025-03-25, 13 files, the job checks out");
        }
    }

    @Nested
    class CwMonthEnd {

        @Test
        void writesEveryFileInThePack() {
            Run run = commercial("month-end", "3", "out/val-03");
            assertThat(run.code()).isZero();
            assertThat(run.written()).hasSize(13);
            assertThat(run.written()).containsKey("out/val-03/certificate.md");
            assertThat(run.written()).containsKey("out/val-03/defects.txt");
        }

        @Test
        void opensWithTheSummaryAndThenListsWhatItWrote() {
            List<String> lines = commercial("month-end", "3", "out").outLines();
            assertThat(lines.get(0)).isEqualTo("valuation 3 to 2025-03-25, 13 files, the job checks out");
            assertThat(lines).hasSize(14);
        }

        @Test
        void reportsTheAmountDueAndTheCheckAsJson() {
            JsonNode body = commercial("--json", "month-end", "3", "out").json();
            assertThat(body.path("amountDue").isTextual()).isTrue();
            assertThat(body.path("amountDue").asText()).isEqualTo("15344.87");
            assertThat(body.path("sound").isBoolean()).isTrue();
            assertThat(body.path("sound").asBoolean()).isTrue();
            assertThat(body.path("errors").isNumber()).isTrue();
            assertThat(body.path("errors").asInt()).isZero();
            assertThat(body.path("files")).hasSize(13);
        }

        @Test
        void savesNothingBackToTheJobFile() {
            assertThat(commercial("month-end", "3", "out").saved()).isEmpty();
        }

        @Test
        void refusesARunWithNoDirectoryNamed() {
            Run run = commercial("month-end", "3");
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.written()).isEmpty();
        }
    }
}
