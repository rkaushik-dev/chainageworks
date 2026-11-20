package io.github.rkaushikdev.chainageworks.io;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.Result;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A small RFC 4180 reader and writer.
 *
 * <p>Written rather than taken off the shelf because the job needs the line numbers and needs to
 * keep going after a bad row, and because the quoting rules are the only genuinely tricky part: a
 * quoted field may hold the delimiter, a newline, and a doubled quote standing for one.
 */
public final class Csv {

    private Csv() {}

    /**
     * A row, with where it came from.
     *
     * <p>Site paperwork arrives as a spreadsheet export and it is always a bit wrong. Carrying the
     * line through the parser is what lets an import report say "line 47, column 'quantity'"
     * instead of "invalid input", which is the difference between a surveyor fixing it and a
     * surveyor emailing about it.
     *
     * @param line one based, counting the header as line 1
     * @param values the trimmed cells
     */
    public record Row(int line, List<String> values) {

        public Row {
            values = List.copyOf(values);
        }
    }

    /**
     * A parsed file.
     *
     * @param headings the first line's cells, or none when the file has no header
     * @param rows the data rows
     */
    public record Table(List<String> headings, List<Row> rows) {

        public Table {
            headings = List.copyOf(headings);
            rows = List.copyOf(rows);
        }

        /** Headings this table needs and does not have, matched without regard to case. */
        public List<String> missingColumns(List<String> required) {
            Set<String> have = new HashSet<>();
            headings.forEach(h -> have.add(Text.lower(h)));
            return required.stream().filter(c -> !have.contains(Text.lower(c))).toList();
        }

        /** A reader over one of this table's rows. */
        public RowReader readerFor(Row row) {
            return new RowReader(this, row);
        }
    }

    /**
     * How a file is read.
     *
     * @param delimiter the one character between cells
     * @param header whether the first line holds the headings
     * @param skipBlank whether rows that are entirely empty are dropped
     */
    public record ReadOptions(char delimiter, boolean header, boolean skipBlank) {

        /** Comma separated, a header line, blank rows dropped. */
        public static final ReadOptions DEFAULT = new ReadOptions(',', true, true);

        public ReadOptions withDelimiter(char other) {
            return new ReadOptions(other, header, skipBlank);
        }

        public ReadOptions withHeader(boolean other) {
            return new ReadOptions(delimiter, other, skipBlank);
        }

        public ReadOptions withSkipBlank(boolean other) {
            return new ReadOptions(delimiter, header, other);
        }
    }

    /** A row bound to its headings, so fields are read by name rather than by position. */
    public static final class RowReader {

        private final Map<String, Integer> index = new HashMap<>();
        private final Row row;

        RowReader(Table table, Row row) {
            this.row = row;
            for (int i = 0; i < table.headings().size(); i++) {
                index.put(Text.lower(table.headings().get(i)), i);
            }
        }

        /** The line the row came from. */
        public int line() {
            return row.line();
        }

        /** The cells as they were read. */
        public List<String> raw() {
            return row.values();
        }

        /** The value under a heading, absent when the column is missing or blank. */
        public Optional<String> get(String column) {
            Integer at = index.get(Text.lower(column));
            if (at == null || at >= row.values().size()) {
                return Optional.empty();
            }
            String value = row.values().get(at);
            return value.isEmpty() ? Optional.empty() : Optional.of(value);
        }

        /** The value under a heading, failing with the line when it is missing or blank. */
        public Result<String, CwException> require(String column) {
            Optional<String> value = get(column);
            if (value.isEmpty()) {
                return Result.err(new BadInputException(
                        "line " + row.line() + ": " + column + " is missing",
                        Details.of("line", row.line(), "column", column)));
            }
            return Result.ok(value.get());
        }
    }

    /** Read comma separated text with a header line, dropping blank rows. */
    public static Table parse(String text) {
        return parse(text, ReadOptions.DEFAULT);
    }

    /** Read separated text with a single character delimiter. */
    public static Table parse(String text, String delimiter, boolean header, boolean skipBlank) {
        if (delimiter.length() != 1) {
            throw new BadInputException("a csv delimiter is one character", Details.of("delimiter", delimiter));
        }
        return parse(text, new ReadOptions(delimiter.charAt(0), header, skipBlank));
    }

    /** Read separated text. */
    public static Table parse(String text, ReadOptions options) {
        char delimiter = options.delimiter();
        List<List<String>> lines = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        List<String> row = new ArrayList<>();
        boolean quoted = false;
        String body = text.startsWith("﻿") ? text.substring(1) : text;
        int i = 0;
        while (i < body.length()) {
            char ch = body.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < body.length() && body.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    quoted = false;
                    i += 1;
                    continue;
                }
                field.append(ch);
                i += 1;
                continue;
            }
            if (ch == '"' && field.isEmpty()) {
                quoted = true;
            } else if (ch == delimiter) {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                row.add(field.toString());
                lines.add(row);
                field.setLength(0);
                row = new ArrayList<>();
            } else if (ch != '\r') {
                field.append(ch);
            }
            i += 1;
        }
        if (quoted) {
            throw new BadInputException("the file ends inside a quoted field");
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            lines.add(row);
        }

        List<List<String>> trimmed = new ArrayList<>();
        for (List<String> cells : lines) {
            trimmed.add(cells.stream().map(Text::trim).toList());
        }
        List<String> headings = options.header() && !trimmed.isEmpty() ? trimmed.remove(0) : List.of();
        int offset = options.header() ? 2 : 1;
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < trimmed.size(); index++) {
            List<String> values = trimmed.get(index);
            if (options.skipBlank() && values.stream().allMatch(String::isEmpty)) {
                continue;
            }
            rows.add(new Row(index + offset, values));
        }
        return new Table(headings, rows);
    }

    /** Write comma separated text, one line per row, each line ended by a newline. */
    public static String write(List<String> headings, List<List<String>> rows) {
        return write(headings, rows, ",", "\n");
    }

    /** Write separated text, quoting any cell holding the delimiter, a quote or a line break. */
    public static String write(List<String> headings, List<List<String>> rows, String delimiter, String eol) {
        List<List<String>> all = new ArrayList<>();
        all.add(headings);
        all.addAll(rows);
        return all.stream()
                        .map(cells -> cells.stream()
                                .map(cell -> quoteCell(cell, delimiter))
                                .collect(Collectors.joining(delimiter)))
                        .collect(Collectors.joining(eol))
                + eol;
    }

    private static String quoteCell(String cell, String delimiter) {
        if (cell.contains(delimiter) || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
            return "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }
}
