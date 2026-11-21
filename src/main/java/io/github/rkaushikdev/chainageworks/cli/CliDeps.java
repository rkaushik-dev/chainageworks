package io.github.rkaushikdev.chainageworks.cli;

import io.github.rkaushikdev.chainageworks.store.Job;
import java.io.IOException;
import java.util.Map;

/**
 * Everything the command line touches that is not pure.
 *
 * <p>Handed in rather than reached for, so the whole command surface can be exercised without a disk
 * or a terminal. A command line that can only be tested by running it is a command line whose error
 * paths are never tested at all.
 */
public interface CliDeps {

    /**
     * The job in the file at {@code path}.
     *
     * @throws io.github.rkaushikdev.chainageworks.BadInputException when there is no job there to read
     */
    Job loadJob(String path);

    /** Write {@code job} to the file at {@code path}. */
    void saveJob(Job job, String path) throws IOException;

    /**
     * The text of the file at {@code path}.
     *
     * @throws io.github.rkaushikdev.chainageworks.BadInputException with the message {@code no such
     *     file} when there is no file there
     */
    String readText(String path) throws IOException;

    /** Write {@code text} to the file at {@code path}, making its directory when it is not there. */
    void writeText(String path, String text) throws IOException;

    /** Print one block of output: a report, a listing, or the help. */
    void out(String text);

    /** Print one block of complaint: what went wrong, or the help when no command was named. */
    void err(String text);

    /** The environment the config is read from. */
    Map<String, String> env();
}
