package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import java.time.LocalDate;

/**
 * Planned against earned at one cut-off.
 *
 * @param period the valuation number
 * @param cutOff the date the works were measured to
 * @param planned what the baseline expected by then
 * @param earned what had been earned by then
 * @param variance earned less planned; negative is behind
 * @param plannedShare the planned figure as a share of the baseline total
 * @param earnedShare the earned figure as a share of the baseline total
 */
public record CurvePoint(
        int period,
        LocalDate cutOff,
        Money planned,
        Money earned,
        Money variance,
        Percent plannedShare,
        Percent earnedShare) {

    /** True when less had been earned than planned. */
    public boolean isBehind() {
        return variance.minor() < 0;
    }
}
