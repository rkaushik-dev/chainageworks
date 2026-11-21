package io.github.rkaushikdev.chainageworks.cli;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.store.FileStore;
import io.github.rkaushikdev.chainageworks.store.Job;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * The real disk, terminal and environment, and nothing else.
 *
 * <p>Every method is a one line adapter onto the JDK or the file store, which is what keeps the command
 * surface itself testable: there is nothing here to get wrong that a test of the commands would miss.
 * Text is read and written as UTF-8 whatever the platform's own encoding is, so a report prints the
 * same pound signs everywhere.
 *
 * @param stdout where output goes
 * @param stderr where complaints go
 * @param env the environment the config is read from
 */
record SystemDeps(PrintStream stdout, PrintStream stderr, Map<String, String> env) implements CliDeps {

    SystemDeps {
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
        env = Map.copyOf(env);
    }

    /** The process's own standard output and error, written as UTF-8, and its environment. */
    static SystemDeps ofProcess() {
        return new SystemDeps(utf8(FileDescriptor.out), utf8(FileDescriptor.err), System.getenv());
    }

    private static PrintStream utf8(FileDescriptor stream) {
        return new PrintStream(new FileOutputStream(stream), false, StandardCharsets.UTF_8);
    }

    @Override
    public Job loadJob(String path) {
        return FileStore.load(Path.of(path));
    }

    @Override
    public void saveJob(Job job, String path) throws IOException {
        FileStore.save(job, Path.of(path));
    }

    /** The file's text, refusing a file that is not there the way a bad argument is refused. */
    @Override
    public String readText(String path) throws IOException {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (NoSuchFileException missing) {
            throw new BadInputException("no such file", Details.of("path", path));
        }
    }

    @Override
    public void writeText(String path, String text) throws IOException {
        Path file = Path.of(path);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @Override
    public void out(String text) {
        stdout.print(text + "\n");
        stdout.flush();
    }

    @Override
    public void err(String text) {
        stderr.print(text + "\n");
        stderr.flush();
    }
}
