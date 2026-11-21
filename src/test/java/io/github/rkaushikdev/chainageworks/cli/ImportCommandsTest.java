package io.github.rkaushikdev.chainageworks.cli;

import static io.github.rkaushikdev.chainageworks.support.CliHarness.runCommand;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Options;
import io.github.rkaushikdev.chainageworks.support.CliHarness.Run;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The import commands, run against a sheet handed in as {@code site.csv}. */
class ImportCommandsTest {

    private static final String RECORD_HEADER = "record,package,code,step,from,to,quantity,unit,measured on,booked by";
    private static final String GOOD_RECORD = "SR-20,WP-02,E423.1,excavate,12+800,13+000,900,m3,2025-03-24,J Foreman";
    private static final String BAD_RECORD = "SR-21,WP-02,nonsense,excavate,12+800,13+000,900,m3,2025-03-24,J Foreman";

    private static Options files(String... lines) {
        return Options.none().withFiles(Map.of("site.csv", String.join("\n", lines)));
    }

    @Nested
    class CwImportRecords {

        @Test
        void takesACleanSheetAndSavesTheJob() {
            Run run = runCommand(List.of("import-records", "site.csv"), files(RECORD_HEADER, GOOD_RECORD));
            assertThat(run.code()).isZero();
            assertThat(run.out()).isEqualTo("read 1 records, took 1, left out 0");
            assertThat(run.saved()).hasSize(1);
            assertThat(run.saved().get(0).path()).isEqualTo("chainageworks.json");
            assertThat(run.saved().get(0).job().records().count()).isEqualTo(4);
        }

        @Test
        void takesNothingAndSavesNothingWhenARowIsWrong() {
            Run run = runCommand(List.of("import-records", "site.csv"), files(RECORD_HEADER, GOOD_RECORD, BAD_RECORD));
            assertThat(run.code()).isZero();
            assertThat(run.out()).contains("read 2 records, took 1, left out 1");
            assertThat(run.out()).contains("error: line 3 (code): not a bill item code");
            assertThat(run.saved()).isEmpty();
        }

        @Test
        void takesTheCleanRowsAndSavesWhenToldTo() {
            Run run = runCommand(
                    List.of("import-records", "site.csv", "--partial"), files(RECORD_HEADER, GOOD_RECORD, BAD_RECORD));
            assertThat(run.saved()).hasSize(1);
            assertThat(run.saved().get(0).job().records().count()).isEqualTo(4);
        }

        @Test
        void savesNowhereWhenAutoSaveIsOff() {
            Run run = runCommand(
                    List.of("import-records", "site.csv"),
                    files(RECORD_HEADER, GOOD_RECORD).withEnv(Map.of("CHAINAGEWORKS_AUTO_SAVE", "off")));
            assertThat(run.code()).isZero();
            assertThat(run.saved()).isEmpty();
        }

        @Test
        void savesToTheFileTheFlagNames() {
            Run run = runCommand(
                    List.of("--file", "a1.json", "import-records", "site.csv"), files(RECORD_HEADER, GOOD_RECORD));
            assertThat(run.saved().get(0).path()).isEqualTo("a1.json");
        }

        @Test
        void reportsAFileItCannotRead() {
            Run run = runCommand(
                    List.of("import-records", "missing.csv"), Options.none().withFiles(Map.of()));
            assertThat(run.code()).isEqualTo(1);
            assertThat(run.err()).isEqualTo("bad_input: no such file");
        }

        @Test
        void printsJsonWhenAsked() {
            JsonNode json = runCommand(
                            List.of("--json", "import-records", "site.csv"), files(RECORD_HEADER, GOOD_RECORD))
                    .json();
            assertThat(json.path("rowsRead").isNumber()).isTrue();
            assertThat(json.path("rowsRead").asText()).isEqualTo("1");
            assertThat(json.path("accepted").asText()).isEqualTo("1");
            assertThat(json.path("rejected").asText()).isEqualTo("0");
        }
    }

    @Nested
    class CwImportSignOffs {

        @Test
        void addsThemToTheRegister() {
            Run run = runCommand(
                    List.of("import-sign-offs", "site.csv"),
                    files(
                            "sign off,point,package,from,to,signed on,by",
                            "SO-20,IP-E1,WP-02,12+800,13+000,2025-03-20,R Engineer"));
            assertThat(run.out()).isEqualTo("read 1 sign offs, took 1, left out 0");
            assertThat(run.saved().get(0).job().signOffs().count()).isEqualTo(3);
        }
    }

    @Nested
    class CwImportNcrs {

        @Test
        void addsThemToTheRegister() {
            Run run = runCommand(
                    List.of("import-ncrs", "site.csv"),
                    files(
                            "ncr,package,from,to,raised on,description",
                            "NCR-20,WP-02,12+800,12+900,2025-03-21,rutting under haul traffic"));
            assertThat(run.out()).isEqualTo("read 1 reports, took 1, left out 0");
            assertThat(run.saved().get(0).job().ncrs().count()).isEqualTo(2);
        }
    }

    @Nested
    class CwImportDiary {

        @Test
        void addsTheStanddownsAndWarnsAboutAReasonItDoesNotKnow() {
            Run run = runCommand(
                    List.of("import-diary", "site.csv"), files("date,reason", "2025-03-05,rain", "2025-03-06,badger"));
            assertThat(run.out()).contains("read 2 diary lines, took 2, left out 0");
            assertThat(run.out()).contains("warning: line 3 (reason)");
            assertThat(run.saved().get(0).job().standdowns()).hasSize(2);
        }
    }

    @Nested
    class CwImportHolidays {

        @Test
        void addsTheDates() {
            Run run = runCommand(List.of("import-holidays", "site.csv"), files("date", "2025-04-18", "2025-04-21"));
            assertThat(run.out()).isEqualTo("read 2 holidays, took 2, left out 0");
            assertThat(run.saved().get(0).job().holidays()).hasSize(2);
        }

        @Test
        void namesAMissingColumnAndSavesNothing() {
            Run run = runCommand(List.of("import-holidays", "site.csv"), files("name", "good friday"));
            assertThat(run.out()).contains("error: file (date): the file has no date column");
            assertThat(run.saved()).isEmpty();
        }
    }
}
