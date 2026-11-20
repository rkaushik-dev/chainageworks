package io.github.rkaushikdev.chainageworks.ingest;

import io.github.rkaushikdev.chainageworks.CwException;
import java.util.Optional;

/**
 * Something wrong with one row, or with the file as a whole.
 *
 * <p>A warning is a row that was taken and is worth a look. An error is a row that was left out.
 * Both carry the line so the surveyor can go and fix the sheet rather than guess.
 *
 * @param line the line, absent when the problem is with the file rather than a row
 * @param column the column, when one is to blame
 * @param message what is wrong
 * @param severity whether the row was left out
 */
public record ImportIssue(Optional<Integer> line, Optional<String> column, String message, IssueSeverity severity) {

    public ImportIssue {
        line = line == null ? Optional.empty() : line;
        column = column == null ? Optional.empty() : column;
    }

    /** A file level error. */
    public static ImportIssue of(String message) {
        return new ImportIssue(Optional.empty(), Optional.empty(), message, IssueSeverity.ERROR);
    }

    /**
     * An issue.
     *
     * @param line the line, or null for the file
     * @param column the column, or null for none
     */
    public static ImportIssue of(String message, Integer line, String column, IssueSeverity severity) {
        return new ImportIssue(Optional.ofNullable(line), Optional.ofNullable(column), message, severity);
    }

    /** An error against a line and a column, either of which may be null. */
    public static ImportIssue error(String message, Integer line, String column) {
        return of(message, line, column, IssueSeverity.ERROR);
    }

    /** A warning against a line and a column, either of which may be null. */
    public static ImportIssue warning(String message, Integer line, String column) {
        return of(message, line, column, IssueSeverity.WARNING);
    }

    /** Turn a thrown or returned failure into an issue, keeping its line if it has one. */
    public static ImportIssue from(Object failure) {
        return from(failure, null);
    }

    /**
     * Turn a thrown or returned failure into an issue, keeping its line if it has one.
     *
     * @param line the line to fall back on, or null
     */
    public static ImportIssue from(Object failure, Integer line) {
        if (failure instanceof CwException known) {
            Object fromDetails = known.details().get("line");
            Object column = known.details().get("column");
            Integer at = fromDetails instanceof Number n ? Integer.valueOf(n.intValue()) : line;
            return of(known.getMessage(), at, column instanceof String s ? s : null, IssueSeverity.ERROR);
        }
        return error(describe(failure), line, null);
    }

    /** The text of a failure that is not one of this package's: its kind and its message. */
    static String describe(Object failure) {
        if (failure instanceof Throwable thrown) {
            String message = thrown.getMessage();
            return message == null || message.isEmpty()
                    ? thrown.getClass().getSimpleName()
                    : thrown.getClass().getSimpleName() + ": " + message;
        }
        return String.valueOf(failure);
    }
}
