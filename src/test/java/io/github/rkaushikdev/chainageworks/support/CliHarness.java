package io.github.rkaushikdev.chainageworks.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.cli.CliDeps;
import io.github.rkaushikdev.chainageworks.cli.Program;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Run the command line with the disk and the terminal replaced.
 *
 * <p>Everything a command wrote is collected, so a test can assert on the exit code and the text without
 * a subprocess.
 */
public final class CliHarness {

    private CliHarness() {}

    /**
     * One save the command made.
     *
     * @param path the file it was saved to
     * @param job the job it saved
     */
    public record Saved(String path, Job job) {}

    /**
     * What one run did.
     *
     * @param code the exit code
     * @param out everything printed to the output, one print a line
     * @param err everything printed to the error stream, one print a line
     * @param saved every save, in order
     * @param written everything the run wrote, keyed by the path it was asked to write to, in the order
     *     the paths were first written
     */
    public record Run(int code, String out, String err, List<Saved> saved, Map<String, String> written) {

        public Run {
            saved = List.copyOf(saved);
            written = Collections.unmodifiableMap(new LinkedHashMap<>(written));
        }

        /** The output read as JSON, failing the test when it is not JSON. */
        public JsonNode json() {
            try {
                return Json.parse(out);
            } catch (Json.JsonFailure broken) {
                throw new AssertionError("the output is not json: " + out, broken);
            }
        }

        /** The output split into lines, keeping empty ones. */
        public List<String> outLines() {
            return Text.split(out, "\n");
        }
    }

    /**
     * What the run is given. A {@code null} takes the default.
     *
     * @param job the job every load answers, the sample job when null
     * @param env the environment, empty when null
     * @param loadError what every load fails with, to exercise the error path; loads succeed when null
     * @param files the files the import commands can read, keyed by the path they are given; none when
     *     null
     */
    public record Options(Job job, Map<String, String> env, RuntimeException loadError, Map<String, String> files) {

        /** Every default: the sample job, no environment, no files. */
        public static Options none() {
            return new Options(null, null, null, null);
        }

        public Options withJob(Job other) {
            return new Options(other, env, loadError, files);
        }

        public Options withEnv(Map<String, String> other) {
            return new Options(job, other, loadError, files);
        }

        public Options withLoadError(RuntimeException other) {
            return new Options(job, env, other, files);
        }

        public Options withFiles(Map<String, String> other) {
            return new Options(job, env, loadError, other);
        }
    }

    /** Run {@code argv} against the sample job, with no environment and no files. */
    public static Run runCommand(String... argv) {
        return runCommand(List.of(argv), Options.none());
    }

    /** Run {@code argv} against what {@code options} gives it. */
    public static Run runCommand(List<String> argv, Options options) {
        List<String> out = new ArrayList<>();
        List<String> err = new ArrayList<>();
        List<Saved> saved = new ArrayList<>();
        Map<String, String> written = new LinkedHashMap<>();
        Job job = options.job() == null ? JobFixtures.sampleJob() : options.job();
        Map<String, String> files = options.files() == null ? Map.of() : options.files();
        Map<String, String> env = options.env() == null ? Map.of() : Map.copyOf(options.env());

        CliDeps deps = new CliDeps() {
            @Override
            public Job loadJob(String path) {
                if (options.loadError() != null) {
                    throw options.loadError();
                }
                return job;
            }

            @Override
            public void saveJob(Job toSave, String path) {
                saved.add(new Saved(path, toSave));
            }

            @Override
            public String readText(String path) {
                String text = files.get(path);
                if (text == null) {
                    throw new BadInputException("no such file", Details.of("path", path));
                }
                return text;
            }

            @Override
            public void writeText(String path, String text) {
                written.put(path, text);
            }

            @Override
            public void out(String text) {
                out.add(text);
            }

            @Override
            public void err(String text) {
                err.add(text);
            }

            @Override
            public Map<String, String> env() {
                return env;
            }
        };

        int code = Program.run(argv, deps);
        return new Run(code, String.join("\n", out), String.join("\n", err), saved, written);
    }
}
