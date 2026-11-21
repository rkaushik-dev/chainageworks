package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.config.Config;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The month end pack written out to a directory, as the month end command does. */
class MonthEndPackFilesTest {

    private static final List<String> COMMERCIAL_FILES = List.of(
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

    @TempDir
    Path dir;

    private static List<String> namesIn(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    @Test
    void writesEveryFileOfThePackInPackOrderCreatingTheDirectory() throws IOException {
        MonthEndPack pack = MonthEndPack.build(new Service(commercialJob(), Config.DEFAULT), 3);
        Path out = dir.resolve("out").resolve("val-03");

        List<Path> written = pack.writeTo(out);

        assertThat(written).extracting(path -> path.getFileName().toString()).isEqualTo(COMMERCIAL_FILES);
        assertThat(written).allMatch(path -> path.getParent().equals(out));
        assertThat(namesIn(out)).isEqualTo(COMMERCIAL_FILES.stream().sorted().toList());
        assertThat(pack.describe()).isEqualTo("valuation 3 to 2025-03-25, 13 files, the job checks out");
    }

    @Test
    void writesEachFileAsUtf8TextExactlyAsThePackHoldsIt() throws IOException {
        MonthEndPack pack = MonthEndPack.build(new Service(commercialJob(), Config.DEFAULT), 3);
        pack.writeTo(dir);

        assertThat(Files.readString(dir.resolve("certificate-totals.csv"), StandardCharsets.UTF_8))
                .isEqualTo(String.join(
                        "\n",
                        "item,amount",
                        "measured work,16152.50",
                        "materials on site,0.00",
                        "daywork,0.00",
                        "gross valuation,16152.50",
                        "less retention,807.63",
                        "net valuation,15344.87",
                        "less previously certified,0.00",
                        "amount due this certificate,15344.87",
                        ""));
        for (PackFile file : pack.files()) {
            assertThat(Files.readString(dir.resolve(file.name()), StandardCharsets.UTF_8))
                    .as(file.name())
                    .isEqualTo(file.text());
        }
    }

    @Test
    void replacesAnEarlierRunAndLeavesOutTheTermsTheContractDoesNotFix() throws IOException {
        Files.writeString(dir.resolve("certificate.md"), "last month's draft");
        MonthEndPack pack = MonthEndPack.build(new Service(sampleJob(), Config.DEFAULT), 3);

        List<Path> written = pack.writeTo(dir);

        assertThat(written).hasSize(11);
        assertThat(namesIn(dir)).doesNotContain("delay.txt", "defects.txt");
        assertThat(Files.readString(dir.resolve("certificate.md")))
                .isEqualTo(pack.file("certificate.md").orElseThrow().text());
    }
}
