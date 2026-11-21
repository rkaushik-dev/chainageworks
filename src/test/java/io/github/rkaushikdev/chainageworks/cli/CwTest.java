package io.github.rkaushikdev.chainageworks.cli;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.emptyJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.store.FileStore;
import io.github.rkaushikdev.chainageworks.store.Job;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The process wiring: the commands on a real disk, the terminal as UTF-8, and {@code serve} handed on. */
class CwTest {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private SystemDeps deps(Map<String, String> env) {
        return new SystemDeps(
                new PrintStream(out, false, StandardCharsets.UTF_8),
                new PrintStream(err, false, StandardCharsets.UTF_8),
                env);
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private Path saved(Job job) throws IOException {
        Path file = dir.resolve("job.json");
        FileStore.save(job, file);
        return file;
    }

    private int run(String... argv) throws InterruptedException {
        return Cw.run(List.of(argv), deps(Map.of()));
    }

    @Test
    void runsACommandAgainstTheJobFileOnDiskAndPrintsUtf8() throws Exception {
        Path job = saved(commercialJob());
        assertThat(run("--file", job.toString(), "status")).isZero();
        assertThat(out()).contains("contract sum    £312,190.00\n");
        assertThat(out.toByteArray()).containsSequence("£".getBytes(StandardCharsets.UTF_8));
        assertThat(out()).endsWith("\n");
        assertThat(err()).isEmpty();
    }

    @Test
    void readsTheJobFileTheEnvironmentNames() throws Exception {
        Path job = saved(sampleJob());
        int code = Cw.run(List.of("--json", "status"), deps(Map.of("CHAINAGEWORKS_JOB_FILE", job.toString())));
        assertThat(code).isZero();
        assertThat(out()).startsWith("{\n  \"id\": \"A1-IMP\",");
    }

    @Test
    void savesAnImportBackToTheJobFileKeepingTheOldOne() throws Exception {
        Path job = saved(sampleJob());
        Path sheet = dir.resolve("site.csv");
        Files.writeString(
                sheet,
                "record,package,code,step,from,to,quantity,unit,measured on,booked by\n"
                        + "SR-20,WP-02,E423.1,excavate,12+800,13+000,900,m3,2025-03-24,J Foreman\n");

        assertThat(run("--file", job.toString(), "import-records", sheet.toString()))
                .isZero();

        assertThat(out()).isEqualTo("read 1 records, took 1, left out 0\n");
        assertThat(FileStore.load(job).records().count()).isEqualTo(4);
        assertThat(FileStore.load(dir.resolve("job.json.bak")).records().count())
                .isEqualTo(3);
    }

    @Test
    void writesTheMonthEndPackIntoADirectoryItMakes() throws Exception {
        Path job = saved(commercialJob());
        Path pack = dir.resolve("out").resolve("val-03");

        assertThat(run("--file", job.toString(), "month-end", "3", pack.toString()))
                .isZero();

        try (Stream<Path> files = Files.list(pack)) {
            assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(13);
        }
        assertThat(Files.readString(pack.resolve("certificate.md"), StandardCharsets.UTF_8))
                .startsWith("# Payment certificate 3");
    }

    @Test
    void refusesASheetThatIsNotThere() throws Exception {
        Path job = saved(sampleJob());
        int code = run(
                "--file",
                job.toString(),
                "import-records",
                dir.resolve("missing.csv").toString());
        assertThat(code).isEqualTo(1);
        assertThat(err()).isEqualTo("bad_input: no such file\n");
        assertThat(out()).isEmpty();
    }

    @Test
    void refusesAJobFileThatIsNotThere() throws Exception {
        assertThat(run("--file", dir.resolve("missing.json").toString(), "status"))
                .isEqualTo(1);
        assertThat(err()).isEqualTo("bad_input: no job file at that path\n");
    }

    @Test
    void handsServeAndEverythingAfterItToTheServer() throws Exception {
        Path job = saved(emptyJob());
        int code = Cw.run(List.of("serve", "--server.port=0"), deps(Map.of("CHAINAGEWORKS_JOB_FILE", job.toString())));
        assertThat(code).isEqualTo(1);
        assertThat(err()).startsWith("chainageworks serving " + job + "\n");
        assertThat(err()).endsWith("refusing to serve, the job cannot be valued\n");
        assertThat(out()).isEmpty();
    }

    @Test
    void saysWhyTheServerCannotStartWithoutAJobFile() throws Exception {
        String missing = dir.resolve("missing.json").toString();
        int code = Cw.run(List.of("serve"), deps(Map.of("CHAINAGEWORKS_JOB_FILE", missing)));
        assertThat(code).isEqualTo(1);
        assertThat(err()).isEqualTo("bad_input: no job file at that path\n");
    }
}
