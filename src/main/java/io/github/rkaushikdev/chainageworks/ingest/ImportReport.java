package io.github.rkaushikdev.chainageworks.ingest;

import io.github.rkaushikdev.chainageworks.io.Csv;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What an import took, what it left out, and why.
 *
 * @param imported the records taken
 * @param issues every problem found, errors and warnings
 * @param rowsRead how many rows (or records) the file held
 * @param <T> what the import reads
 */
public record ImportReport<T>(List<T> imported, List<ImportIssue> issues, int rowsRead) {

    public ImportReport {
        imported = List.copyOf(imported);
        issues = List.copyOf(issues);
    }

    /** A report of nothing read. */
    public static <T> ImportReport<T> empty() {
        return new ImportReport<>(List.of(), List.of(), 0);
    }

    public List<ImportIssue> errors() {
        return issues.stream().filter(i -> i.severity() == IssueSeverity.ERROR).toList();
    }

    public List<ImportIssue> warnings() {
        return issues.stream()
                .filter(i -> i.severity() == IssueSeverity.WARNING)
                .toList();
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == IssueSeverity.ERROR);
    }

    public int rowsAccepted() {
        return imported.size();
    }

    public int rowsRejected() {
        return rowsRead - imported.size();
    }

    /** The same report with other issues. */
    public ImportReport<T> withIssues(List<ImportIssue> other) {
        return new ImportReport<>(imported, other, rowsRead);
    }

    /**
     * Check the headings before reading a row.
     *
     * <p>A file with the wrong columns produces one issue rather than one per row, which is the
     * difference between a report somebody reads and four hundred lines of the same complaint.
     *
     * @return a report of the missing columns, or empty when every column is there
     */
    public static <T> Optional<ImportReport<T>> checkColumns(Csv.Table table, List<String> required) {
        List<String> missing = table.missingColumns(required);
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ImportReport<>(
                List.of(),
                missing.stream()
                        .map(column -> ImportIssue.error("the file has no " + column + " column", null, column))
                        .toList(),
                table.rows().size()));
    }

    /** Several reports added together. */
    public static <T> ImportReport<T> merge(List<ImportReport<T>> reports) {
        List<T> imported = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();
        int rowsRead = 0;
        for (ImportReport<T> report : reports) {
            imported.addAll(report.imported());
            issues.addAll(report.issues());
            rowsRead += report.rowsRead();
        }
        return new ImportReport<>(imported, issues, rowsRead);
    }

    /** {@link #format(String)} counting rows. */
    public String format() {
        return format("rows");
    }

    /** A plain text summary, which is what the command line prints and the API returns. */
    public String format(String what) {
        List<String> lines = new ArrayList<>();
        lines.add("read " + rowsRead + " " + what + ", took " + rowsAccepted() + ", left out " + rowsRejected());
        for (ImportIssue problem : issues) {
            String where = problem.line().map(line -> "line " + line).orElse("file");
            String column = problem.column().map(c -> " (" + c + ")").orElse("");
            lines.add("  " + problem.severity().wire() + ": " + where + column + ": " + problem.message());
        }
        return String.join("\n", lines);
    }
}
