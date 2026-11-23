package io.github.rkaushikdev.chainageworks.domain.fluctuation;

import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;
import java.util.List;

/**
 * The price adjustment factor at a date, and how each index contributed to it.
 *
 * @param asAt the date it was worked out for
 * @param indicesReadAt the date the current figures were read for, which trails the valuation by
 *     the lag the clause names
 * @param lines one line per weighted index
 * @param factor the price adjustment factor, as a fraction of the adjustable value
 * @param provisional true while any contributing figure is still provisional
 */
public record AdjustmentFactor(
        LocalDate asAt, LocalDate indicesReadAt, List<Line> lines, double factor, boolean provisional) {

    public AdjustmentFactor {
        lines = List.copyOf(lines);
    }

    /**
     * One weighted index in the factor.
     *
     * @param indexId the index series
     * @param weight its share of the price
     * @param movement how far it has moved since the base date, as a fraction of one
     * @param contribution the weighted contribution to the factor
     * @param provisional whether either figure it was read from is provisional
     */
    public record Line(String indexId, Percent weight, double movement, double contribution, boolean provisional) {}

    /** The factor as a percentage, which is how a certificate prints it. */
    public Percent asPercent() {
        return Percent.ofBasisPoints(Numbers.round(factor * Percent.BASIS_POINTS_WHOLE));
    }
}
