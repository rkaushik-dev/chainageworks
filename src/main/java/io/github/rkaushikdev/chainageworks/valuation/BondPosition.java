package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;

/**
 * Where a retention bond stands on a date.
 *
 * @param bond the bond
 * @param asAt the date
 * @param cover what the bond covers on this date
 * @param wouldHold what the employer would otherwise be holding in cash
 * @param released cash the contractor has because the bond is in place
 * @param shortfall the part of the cash retention the bond does not reach
 * @param expired true once the bond has lapsed
 * @param daysToExpiry calendar days until it lapses, negative once it has
 */
public record BondPosition(
        RetentionBond bond,
        LocalDate asAt,
        Money cover,
        Money wouldHold,
        Money released,
        Money shortfall,
        boolean expired,
        int daysToExpiry) {

    /** {@code RB-01 with Northgate Surety, 2635 short of the retention}. */
    public String describe() {
        if (expired) {
            return bond.id() + " expired on " + SiteDates.format(bond.expiresOn());
        }
        String gap = shortfall.minor() == 0
                ? "covers the retention in full"
                : Numbers.format(shortfall.minor() / 100.0) + " short of the retention";
        return bond.id() + " with " + bond.surety() + ", " + gap;
    }
}
