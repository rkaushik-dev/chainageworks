package io.github.rkaushikdev.chainageworks.reporting.format;

import java.util.Optional;

/**
 * One column of a fixed width table.
 *
 * @param heading the text over the column
 * @param align which edge the cells sit against; left when null
 * @param width the most characters a cell may take, anything longer being cut with an ellipsis;
 *     empty means the column is as wide as its widest cell
 */
public record Column(String heading, Align align, Optional<Integer> width) {

    public Column {
        align = align == null ? Align.LEFT : align;
        width = width == null ? Optional.empty() : width;
    }

    /** A left aligned column as wide as its content. */
    public static Column of(String heading) {
        return new Column(heading, Align.LEFT, Optional.empty());
    }

    /** A right aligned column as wide as its content, which is how a column of figures is set. */
    public static Column right(String heading) {
        return new Column(heading, Align.RIGHT, Optional.empty());
    }

    public Column withAlign(Align other) {
        return new Column(heading, other, width);
    }

    public Column withWidth(int other) {
        return new Column(heading, align, Optional.of(other));
    }
}
