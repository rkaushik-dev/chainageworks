package io.github.rkaushikdev.chainageworks.store;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.progress.PackageEarning;
import io.github.rkaushikdev.chainageworks.store.FileStore.SaveOptions;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStoreTest {

    @TempDir
    Path dir;

    private Path path;

    @BeforeEach
    void setUp() {
        path = dir.resolve("job.json");
    }

    private Path backup() {
        return dir.resolve("job.json.bak");
    }

    @Nested
    class SaveJob {

        @Test
        void writesAFileThatReadsBackAsTheSameJob() throws IOException {
            FileStore.save(sampleJob(), path);
            assertThat(FileStore.exists(path)).isTrue();
            Job again = FileStore.load(path);
            assertThat(again.settings().id()).isEqualTo("A1-IMP");
            assertThat(again.records().count()).isEqualTo(3);
        }

        @Test
        void valuesTheSameAfterATripThroughTheDisk() throws IOException {
            FileStore.save(sampleJob(), path);
            Job again = FileStore.load(path);
            assertThat(PackageEarning.earnAll(again.context(), d("2025-03-31"))
                            .get(0)
                            .earnedValue()
                            .major())
                    .isEqualTo(16_152.5);
        }

        @Test
        void writesReadableJsonByDefaultAndOneLineOnRequest() throws IOException {
            FileStore.save(sampleJob(), path);
            assertThat(Text.split(Files.readString(path, StandardCharsets.UTF_8), "\n"))
                    .hasSizeGreaterThan(50);
            FileStore.save(sampleJob(), path, SaveOptions.DEFAULT.withPretty(false));
            assertThat(Text.split(Text.trimEnd(Files.readString(path, StandardCharsets.UTF_8)), "\n"))
                    .hasSize(1);
        }

        @Test
        void makesTheDirectoryItIsGiven() throws IOException {
            Path nested = dir.resolve("a").resolve("b").resolve("job.json");
            FileStore.save(sampleJob(), nested);
            assertThat(FileStore.exists(nested)).isTrue();
        }

        @Test
        void leavesNoTemporaryFilesBehind() throws IOException {
            FileStore.save(sampleJob(), path);
            try (Stream<Path> files = Files.list(dir)) {
                assertThat(files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".tmp")))
                        .isEmpty();
            }
        }

        @Test
        void keepsThePreviousFileAsABackup() throws IOException {
            Job first = sampleJob();
            FileStore.save(first, path);
            Job second = sampleJob();
            second.records().remove("SR-03");
            FileStore.save(second, path);
            assertThat(FileStore.load(path).records().count()).isEqualTo(2);
            assertThat(FileStore.exists(backup())).isTrue();
            FileStore.restoreBackup(path);
            assertThat(FileStore.load(path).records().count()).isEqualTo(3);
        }

        @Test
        void writesNoBackupOnTheFirstSaveOrWhenAskedNotTo() throws IOException {
            FileStore.save(sampleJob(), path);
            assertThat(FileStore.exists(backup())).isFalse();
            FileStore.save(sampleJob(), path, SaveOptions.DEFAULT.withBackup(false));
            assertThat(FileStore.exists(backup())).isFalse();
        }

        @Test
        void refusesToRestoreABackupThatIsNotThere() {
            assertThatThrownBy(() -> FileStore.restoreBackup(path)).hasMessageContaining("no backup to restore");
        }
    }

    @Nested
    class LoadJob {

        @Test
        void saysSoWhenThereIsNoFile() {
            assertThatThrownBy(() -> FileStore.load(dir.resolve("missing.json")))
                    .hasMessageContaining("no job file at that path");
        }

        @Test
        void separatesAFileThatIsNotJsonFromAFileThatIsNotAJob() throws IOException {
            Files.writeString(path, "not json at all", StandardCharsets.UTF_8);
            assertThatThrownBy(() -> FileStore.load(path)).hasMessageContaining("not valid json");

            Files.writeString(path, "{\"version\":99}", StandardCharsets.UTF_8);
            assertThatThrownBy(() -> FileStore.load(path)).hasMessageContaining("different version");
        }

        @Test
        void namesTheFileInTheMessage() throws IOException {
            Files.writeString(path, "{\"version\":99}", StandardCharsets.UTF_8);
            assertThatThrownBy(() -> FileStore.load(path)).hasMessageContaining("job.json");
        }
    }

    @Nested
    class ParseJob {

        @Test
        void readsTextWithoutTouchingTheDisk() {
            String text = Json.stringify(Json.valueOf(Map.of("version", 1)));
            assertThatThrownBy(() -> FileStore.parse(text)).isInstanceOf(BadInputException.class);
        }

        @Test
        void reportsThePlaceholderPathWhenItWasGivenNone() {
            assertThatThrownBy(() -> FileStore.parse("{\"version\":99}")).hasMessageContaining("<memory>");
        }
    }

    @Nested
    class SaveAndVerify {

        @Test
        void savesAndReadsTheJobStraightBack() throws IOException {
            FileStore.saveAndVerify(sampleJob(), path);
            assertThat(FileStore.load(path).records().count()).isEqualTo(3);
        }
    }
}
