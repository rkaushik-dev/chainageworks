package io.github.rkaushikdev.chainageworks.cli;

import java.io.IOException;

/** What one command does once its command line has been read. */
@FunctionalInterface
interface Action {

    /** Do the command's work, printing through {@code call} and failing with one of the job's exceptions. */
    void run(Invocation call) throws IOException;
}
