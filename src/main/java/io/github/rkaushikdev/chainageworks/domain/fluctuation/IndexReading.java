package io.github.rkaushikdev.chainageworks.domain.fluctuation;

/**
 * One published figure of a cost index, and whether it is final.
 *
 * <p>The provisional flag is the point. Construction cost indices are published provisionally and
 * firmed up months later, and a certificate worked out on a provisional figure has to be reworked
 * when the firm one lands. A series that forgets which figures were provisional cannot tell anybody
 * which certificates need revisiting.
 *
 * @param year the year it is for
 * @param month the month it is for, 1 to 12
 * @param value the index figure
 * @param provisional whether it may still be revised
 */
public record IndexReading(int year, int month, double value, boolean provisional) {

    /** A firm figure. */
    public static IndexReading firm(int year, int month, double value) {
        return new IndexReading(year, month, value, false);
    }
}
