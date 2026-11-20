package io.github.rkaushikdev.chainageworks.ingest;

import io.github.rkaushikdev.chainageworks.io.Csv;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The row loops every importer shares.
 *
 * <p>Every importer had the same loop written out by hand, and they had drifted: two of them
 * stopped at the first bad row and the rest carried on, which meant the same site file behaved
 * differently depending on which register it was going into. These two loops are the one place the
 * rules live.
 */
public final class Rows {

    private Rows() {}

    /** Reads one record from one row, adding any warnings it wants to file to {@code issues}. */
    @FunctionalInterface
    public interface RowRead<T> {
        T read(Csv.RowReader reader, List<ImportIssue> issues);
    }

    /** Builds one record from the rows that share its key. */
    @FunctionalInterface
    public interface GroupBuild<R, T> {
        T build(String key, R head, List<R> rows);
    }

    /**
     * How to read a file of one record per row.
     *
     * @param columns the headings the file has to carry before any row is read
     * @param read how one row becomes a record
     * @param keyOf how a record names itself, when two rows carrying the same name is a mistake
     * @param duplicate what to say about the second row carrying a name already used
     * @param keyColumn the column the duplicate complaint is filed against
     */
    public record RowImport<T>(
            List<String> columns,
            RowRead<T> read,
            Optional<Function<T, String>> keyOf,
            Optional<Function<String, String>> duplicate,
            Optional<String> keyColumn) {

        public RowImport {
            columns = List.copyOf(columns);
        }

        /** Every row read, none refused for repeating a name. */
        public static <T> RowImport<T> of(List<String> columns, RowRead<T> read) {
            return new RowImport<>(columns, read, Optional.empty(), Optional.empty(), Optional.empty());
        }

        public RowImport<T> keyedBy(Function<T, String> key) {
            return new RowImport<>(columns, read, Optional.of(key), duplicate, keyColumn);
        }

        public RowImport<T> duplicateSays(Function<String, String> message) {
            return new RowImport<>(columns, read, keyOf, Optional.of(message), keyColumn);
        }

        public RowImport<T> keyColumn(String column) {
            return new RowImport<>(columns, read, keyOf, duplicate, Optional.of(column));
        }
    }

    /**
     * How to read a file where several rows make one record.
     *
     * @param columns the headings the file has to carry
     * @param read how one row is read
     * @param keyOf which record a row belongs to; rows sharing a key become one record
     * @param agrees whether a later row agrees with the first row of its group: the complaint when
     *     it does not, empty when it does
     * @param build how a group becomes a record
     * @param keyColumn the column a grouping complaint is filed against
     */
    public record GroupedImport<R, T>(
            List<String> columns,
            RowRead<R> read,
            Function<R, String> keyOf,
            Optional<BiFunction<R, R, Optional<String>>> agrees,
            GroupBuild<R, T> build,
            Optional<String> keyColumn) {

        public GroupedImport {
            columns = List.copyOf(columns);
        }

        /** Groups with no agreement check. */
        public static <R, T> GroupedImport<R, T> of(
                List<String> columns, RowRead<R> read, Function<R, String> keyOf, GroupBuild<R, T> build) {
            return new GroupedImport<>(columns, read, keyOf, Optional.empty(), build, Optional.empty());
        }

        public GroupedImport<R, T> agreeing(BiFunction<R, R, Optional<String>> check) {
            return new GroupedImport<>(columns, read, keyOf, Optional.of(check), build, keyColumn);
        }

        public GroupedImport<R, T> buildingWith(GroupBuild<R, T> other) {
            return new GroupedImport<>(columns, read, keyOf, agrees, other, keyColumn);
        }

        public GroupedImport<R, T> keyColumn(String column) {
            return new GroupedImport<>(columns, read, keyOf, agrees, build, Optional.of(column));
        }
    }

    private static String fileAgainst(Optional<String> keyColumn, List<String> columns) {
        return keyColumn.orElse(columns.isEmpty() ? "row" : columns.get(0));
    }

    /**
     * Read a file into records, one row at a time.
     *
     * <p>A row that cannot be read is left out with an issue against its line and the run
     * continues, because a survey book arrives three hundred rows at a time and two of them are
     * always wrong. A row that repeats a name already taken is left out rather than overwriting,
     * because an import that quietly replaced a record would change a certificate that has already
     * been issued.
     */
    public static <T> ImportReport<T> readTable(String text, RowImport<T> spec) {
        Csv.Table table = Csv.parse(text);
        Optional<ImportReport<T>> columnProblem = ImportReport.checkColumns(table, spec.columns());
        if (columnProblem.isPresent()) {
            return columnProblem.get();
        }
        List<T> imported = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Csv.Row row : table.rows()) {
            Csv.RowReader reader = table.readerFor(row);
            T item;
            try {
                item = spec.read().read(reader, issues);
            } catch (RuntimeException failure) {
                issues.add(ImportIssue.from(failure, reader.line()));
                continue;
            }
            if (spec.keyOf().isPresent()) {
                String key = spec.keyOf().get().apply(item);
                if (!seen.add(key)) {
                    String message = spec.duplicate().map(say -> say.apply(key)).orElse(key + " appears twice");
                    issues.add(ImportIssue.error(message, row.line(), fileAgainst(spec.keyColumn(), spec.columns())));
                    continue;
                }
            }
            imported.add(item);
        }
        return new ImportReport<>(imported, issues, table.rows().size());
    }

    /**
     * Read a file where several rows make one record.
     *
     * <p>A resource return is one sheet with many gang and plant lines on it, and a cost index is
     * one series with many months in it. Site systems export both flat, so the grouping happens
     * here, once.
     *
     * <p>Groups come back in the order their first row appeared, not sorted, because that order is
     * the order the file was written in and it is what a person reading the issues alongside the
     * file expects.
     *
     * <p>The report counts records rather than rows. A count of rows would say four when two
     * returns were loaded, and row level problems are still reported as issues against their own
     * line, so nothing is lost by counting the larger thing.
     */
    public static <R, T> ImportReport<T> readGrouped(String text, GroupedImport<R, T> spec) {
        ImportReport<R> rows = readTable(text, RowImport.of(spec.columns(), spec.read()));
        if (rows.imported().isEmpty()) {
            return new ImportReport<>(List.of(), rows.issues(), 0);
        }
        String against = fileAgainst(spec.keyColumn(), spec.columns());
        List<ImportIssue> issues = new ArrayList<>(rows.issues());
        Map<String, R> heads = new LinkedHashMap<>();
        Map<String, List<R>> grouped = new LinkedHashMap<>();
        for (R row : rows.imported()) {
            String key = spec.keyOf().apply(row);
            R head = heads.get(key);
            if (head == null) {
                heads.put(key, row);
                grouped.put(key, new ArrayList<>());
            } else {
                Optional<String> complaint = spec.agrees().flatMap(check -> check.apply(head, row));
                if (complaint.isPresent()) {
                    issues.add(ImportIssue.error(complaint.get(), null, against));
                    continue;
                }
            }
            grouped.get(key).add(row);
        }
        List<T> imported = new ArrayList<>();
        for (Map.Entry<String, R> head : heads.entrySet()) {
            try {
                imported.add(
                        spec.build().build(head.getKey(), head.getValue(), List.copyOf(grouped.get(head.getKey()))));
            } catch (RuntimeException failure) {
                String message = failure.getMessage() == null ? ImportIssue.describe(failure) : failure.getMessage();
                issues.add(ImportIssue.error(message, null, against));
            }
        }
        return new ImportReport<>(imported, issues, heads.size());
    }
}
