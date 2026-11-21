package io.github.rkaushikdev.chainageworks.reporting.format;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Fixed width text layout for a terminal and for a plain text report: tables, section headings,
 * aligned label and value pairs, and indented blocks.
 *
 * <p>Money and quantities are right aligned because that is the only way a column of figures can
 * be read down, and the widths come from the content rather than being declared, so a long package
 * name does not silently lose its tail.
 */
public final class Table {

    private Table() {}

    /** A table with a rule under the headings and two spaces between columns. */
    public static String render(List<Column> columns, List<List<String>> rows) {
        return render(columns, rows, TableOptions.DEFAULT);
    }

    /**
     * A table with every column as wide as its widest cell or heading, after clipping. Each line has
     * its trailing spaces taken off.
     *
     * @throws BadInputException when there are no columns, or a row has a different number of cells
     */
    public static String render(List<Column> columns, List<List<String>> rows, TableOptions options) {
        if (columns.isEmpty()) {
            throw new BadInputException("a table needs at least one column");
        }
        for (int index = 0; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            if (row.size() != columns.size()) {
                throw new BadInputException(
                        "a table row has the wrong number of cells",
                        Details.of("row", index, "cells", row.size(), "columns", columns.size()));
            }
        }
        List<List<String>> cells = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            List<String> clipped = new ArrayList<>(row.size());
            for (int i = 0; i < row.size(); i++) {
                clipped.add(clip(row.get(i), columns.get(i).width()));
            }
            cells.add(clipped);
        }
        List<String> headings =
                columns.stream().map(c -> clip(c.heading(), c.width())).toList();
        int[] widths = new int[columns.size()];
        for (int i = 0; i < widths.length; i++) {
            int widest = headings.get(i).length();
            for (List<String> row : cells) {
                widest = Math.max(widest, row.get(i).length());
            }
            widths[i] = widest;
        }

        String gap = " ".repeat(options.gap());
        List<String> lines = new ArrayList<>(cells.size() + 2);
        lines.add(pad(headings, widths, columns, gap));
        if (options.rule()) {
            List<String> rule = new ArrayList<>(widths.length);
            for (int width : widths) {
                rule.add("-".repeat(width));
            }
            lines.add(String.join(gap, rule));
        }
        for (List<String> row : cells) {
            lines.add(pad(row, widths, columns, gap));
        }
        return String.join("\n", lines);
    }

    private static String pad(List<String> row, int[] widths, List<Column> columns, String gap) {
        List<String> padded = new ArrayList<>(row.size());
        for (int i = 0; i < row.size(); i++) {
            String cell = row.get(i);
            padded.add(
                    columns.get(i).align() == Align.RIGHT
                            ? Text.padStart(cell, widths[i])
                            : Text.padEnd(cell, widths[i]));
        }
        return Text.trimEnd(String.join(gap, padded));
    }

    private static String clip(String text, Optional<Integer> limit) {
        if (limit.isEmpty() || text.length() <= limit.get()) {
            return text;
        }
        int width = limit.get();
        if (width <= 1) {
            // A negative width counts back from the end of the text.
            return text.substring(0, Math.max(0, width < 0 ? text.length() + width : width));
        }
        return text.substring(0, width - 1) + "…";
    }

    /** A heading with a rule of {@code =} under it, for the top of a report section. */
    public static String heading(String text) {
        return heading(text, "=");
    }

    /** A heading with {@code underline} repeated under it to the width of the text. */
    public static String heading(String text, String underline) {
        return text + "\n" + underline.repeat(text.length());
    }

    /**
     * Label and value pairs, one to a line, the values lined up two spaces past the longest label.
     * An empty list is an empty string.
     */
    public static String pairs(List<Map.Entry<String, String>> pairs) {
        int width = 0;
        for (Map.Entry<String, String> pair : pairs) {
            width = Math.max(width, pair.getKey().length());
        }
        int labelWidth = width;
        return pairs.stream()
                .map(pair -> Text.padEnd(pair.getKey(), labelWidth) + "  " + pair.getValue())
                .collect(Collectors.joining("\n"));
    }

    /** Every line of {@code text} shifted right by two spaces, empty lines left empty. */
    public static String indent(String text) {
        return indent(text, 2);
    }

    /** Every line of {@code text} shifted right by {@code spaces} spaces, empty lines left empty. */
    public static String indent(String text, int spaces) {
        String prefix = " ".repeat(spaces);
        return Text.split(text, "\n").stream()
                .map(line -> line.isEmpty() ? line : prefix + line)
                .collect(Collectors.joining("\n"));
    }
}
