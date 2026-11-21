package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A retention bond, which stands in place of cash retention.
 *
 * <p>The trade is straightforward: the contractor pays a bank a percentage a year for a guarantee,
 * and in exchange the employer stops holding cash off every certificate. Which is cheaper depends
 * entirely on how long the money would otherwise sit there, which is why the two have to be
 * comparable rather than a setting somebody picks once.
 *
 * @param id the bond
 * @param surety who stands behind it
 * @param maximum the most the bond can be called for, usually the retention cap
 * @param issuedOn the date it was issued
 * @param expiresOn when the bond lapses, whether or not the works are finished
 * @param rate what the surety charges a year, against the bonded amount
 * @param reducesTo the reduced amount once substantial completion is certified, if any
 */
public record RetentionBond(
        String id,
        String surety,
        Money maximum,
        LocalDate issuedOn,
        LocalDate expiresOn,
        Percent rate,
        Optional<Money> reducesTo) {

    public static final Percent DEFAULT_RATE = Percent.of(1);

    private static final double DAYS_A_YEAR = 365;

    public static RetentionBond build(RetentionBondInput input) {
        if (input.maximum().minor() <= 0) {
            throw new BadInputException("a bond has to be for some amount", Details.of("bond", input.id()));
        }
        if (SiteDates.compare(input.expiresOn(), input.issuedOn()) <= 0) {
            throw new BadInputException(
                    "a bond cannot expire before it is issued",
                    Details.of(
                            "bond",
                            input.id(),
                            "issuedOn",
                            SiteDates.format(input.issuedOn()),
                            "expiresOn",
                            SiteDates.format(input.expiresOn())));
        }
        Percent rate = input.rate() == null ? DEFAULT_RATE : input.rate();
        if (rate.basisPoints() < 0 || rate.basisPoints() > Percent.FULL.basisPoints()) {
            throw new BadInputException("a bond rate runs from 0 to 100", Details.of("bond", input.id()));
        }
        Optional<Money> reducesTo = Optional.ofNullable(input.reducesTo());
        if (reducesTo.isPresent() && reducesTo.get().minor() > input.maximum().minor()) {
            throw new BadInputException(
                    "a bond cannot reduce to more than it started at", Details.of("bond", input.id()));
        }
        return new RetentionBond(
                input.id(), input.surety(), input.maximum(), input.issuedOn(), input.expiresOn(), rate, reducesTo);
    }

    /** {@link #position(Money, LocalDate, LocalDate)} with no substantial completion yet. */
    public BondPosition position(Money wouldHold, LocalDate asAt) {
        return position(wouldHold, asAt, null);
    }

    /**
     * Where the bond stands against the cash it replaces.
     *
     * <p>The shortfall is the figure worth reporting. A bond written for the retention cap covers
     * the whole of it, but one written for a round number at tender stops covering somewhere in the
     * middle of the job, and the employer is entitled to hold cash for the difference from that
     * moment.
     *
     * @param substantialCompletionOn the date of substantial completion, or null when there is none
     */
    public BondPosition position(Money wouldHold, LocalDate asAt, LocalDate substantialCompletionOn) {
        Currency currency = maximum.currency();
        boolean expired = SiteDates.compare(asAt, expiresOn) > 0;
        boolean notYet = SiteDates.compare(asAt, issuedOn) < 0;
        boolean reduced = reducesTo.isPresent()
                && substantialCompletionOn != null
                && SiteDates.compare(asAt, substantialCompletionOn) >= 0;

        Money face = reduced ? reducesTo.get() : maximum;
        Money cover = expired || notYet ? Money.zero(currency) : face;
        return new BondPosition(
                this,
                asAt,
                cover,
                wouldHold,
                Money.min(cover, wouldHold),
                Money.max(wouldHold.minus(cover), Money.zero(currency)),
                expired,
                SiteDates.diffDays(asAt, expiresOn));
    }

    /**
     * What the bond costs for a year on the cover it carries.
     *
     * <p>Charged on the face of the bond rather than on what is drawn against it, because that is
     * how a surety prices one. A contractor who thinks the charge follows the retention it replaces
     * gets a nasty surprise in the month the works stop and the bond does not.
     */
    public Money annualCharge() {
        return rate.of(maximum);
    }

    private double years() {
        return SiteDates.diffDays(issuedOn, expiresOn) / DAYS_A_YEAR;
    }

    /** The whole charge from issue to expiry, which is what the trade turns on. */
    public Money totalCharge() {
        Money annual = annualCharge();
        return new Money((long) Numbers.round(annual.minor() * years()), annual.currency());
    }

    /** True when the bond costs less than the cash it frees up would earn. */
    public boolean isWorthTaking(Money wouldHold, Percent interestRate) {
        Money covered = Money.min(maximum, wouldHold);
        Money interest = interestRate.of(covered);
        return totalCharge().minor() < Numbers.round(interest.minor() * years());
    }

    /** Bonds within a window of lapsing, which is the list somebody has to chase. */
    public static List<RetentionBond> expiringWithin(List<RetentionBond> bonds, LocalDate asAt, int days) {
        List<RetentionBond> soon = new ArrayList<>(bonds.stream()
                .filter(bond -> {
                    int left = SiteDates.diffDays(asAt, bond.expiresOn());
                    return left >= 0 && left <= days;
                })
                .toList());
        soon.sort(Comparator.comparing(RetentionBond::expiresOn));
        return List.copyOf(soon);
    }
}
