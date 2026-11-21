package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;

/**
 * One month of the cash forecast.
 *
 * <p>Certified and received are held on different dates on purpose. The certificate is a fact about
 * the works and the receipt is a fact about the bank, and the gap between them is the one that
 * decides whether the job can pay its subcontractors. Reporting them on the same date, which is what
 * a valuation report does, makes that gap invisible.
 *
 * @param period the valuation number
 * @param cutOff the date the works were measured to
 * @param dueOn the date payment falls due
 * @param certified net certified to the end of this period, cumulative
 * @param certificate the movement this period, which is what the certificate is worth
 * @param receipt cash actually expected in the bank on the due date
 * @param received cumulative cash in by the due date
 * @param outstanding certified and not yet received
 */
public record CashPoint(
        int period,
        LocalDate cutOff,
        LocalDate dueOn,
        Money certified,
        Money certificate,
        Money receipt,
        Money received,
        Money outstanding) {

    /** {@code period 4, due 2025-05-20, 60000 outstanding}. */
    public String describe() {
        return "period " + period + ", due " + SiteDates.format(dueOn) + ", "
                + Numbers.format(outstanding.minor() / 100.0) + " outstanding";
    }
}
