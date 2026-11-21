package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.time.DateRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;

/**
 * One interim valuation.
 *
 * <p>The cut-off is the date the works are measured to, not the date the paperwork is done.
 * Everything the engine is asked is asked as at a cut-off, so a certificate can be rebuilt months
 * later and come out the same.
 *
 * @param number the valuation number, from one
 * @param range the days the valuation covers, ending on the cut-off
 * @param cutOff the date the works are measured to
 * @param issuedOn the date the certificate is issued
 * @param dueOn the date payment falls due
 */
public record ValuationPeriod(int number, DateRange range, LocalDate cutOff, LocalDate issuedOn, LocalDate dueOn) {

    /** {@code valuation 3 to 2025-03-25, issued 2025-04-03, due 2025-04-17}. */
    public String describe() {
        return "valuation " + number + " to " + SiteDates.format(cutOff) + ", issued " + SiteDates.format(issuedOn)
                + ", due " + SiteDates.format(dueOn);
    }
}
