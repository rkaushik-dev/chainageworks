package io.github.rkaushikdev.chainageworks.app;

import java.util.Objects;

/**
 * One thing the doctor found wrong with a job.
 *
 * @param level how badly it breaks the job
 * @param register the register the problem is in, so it can be sorted by whose job it is
 * @param subject the record the problem is on
 * @param message what is wrong with it
 */
public record Finding(FindingLevel level, String register, String subject, String message) {

    public Finding {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(register, "register");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(message, "message");
    }

    static Finding error(String register, String subject, String message) {
        return new Finding(FindingLevel.ERROR, register, subject, message);
    }

    static Finding warning(String register, String subject, String message) {
        return new Finding(FindingLevel.WARNING, register, subject, message);
    }

    /** {@code error sign offs SO-99: no inspection plan carries ...}, as the doctor lists it. */
    public String describe() {
        return level + " " + register + " " + subject + ": " + message;
    }
}
