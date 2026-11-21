package io.github.rkaushikdev.chainageworks.reporting.format;

/**
 * How a table is drawn.
 *
 * @param rule draw a rule under the headings
 * @param gap spaces between columns
 */
public record TableOptions(boolean rule, int gap) {

    /** A rule under the headings and two spaces between columns. */
    public static final TableOptions DEFAULT = new TableOptions(true, 2);

    public TableOptions withRule(boolean other) {
        return new TableOptions(other, gap);
    }

    public TableOptions withGap(int other) {
        return new TableOptions(rule, other);
    }
}
