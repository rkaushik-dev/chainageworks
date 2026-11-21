package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;

/**
 * How much retention is being held at a cut-off.
 *
 * @param uncapped what the rate alone would hold
 * @param cap what the limit allows to be held at all
 * @param accrued the lower of the two, which is what accrues
 * @param releasedOnCompletion what came back when the works were taken over
 * @param releasedOnDefects what came back with the defects certificate
 * @param held what is still held
 * @param capReached true when the rate alone would hold more than the limit allows
 */
public record RetentionState(
        Money uncapped,
        Money cap,
        Money accrued,
        Money releasedOnCompletion,
        Money releasedOnDefects,
        Money held,
        boolean capReached) {

    /**
     * How much is being held at a cut-off.
     *
     * <p>The two releases are separate on purpose. Half comes back when the works are taken over
     * and the rest only after the defects period, and a valuation that releases the lot at
     * completion overpays for a year.
     */
    public static RetentionState compute(RetentionQuery query) {
        Currency currency = query.grossValuation().currency();
        Money uncapped = query.terms().rate().of(query.grossValuation());
        Money cap = query.terms().limit().of(query.contractSum());
        Money accrued = Money.min(uncapped, cap);

        boolean takenOver = hasPassed(query.substantialCompletionOn(), query.asAt());
        boolean defectsCleared = hasPassed(query.defectsCertificateOn(), query.asAt());

        Money releasedOnCompletion =
                takenOver ? query.terms().releaseOnCompletion().of(accrued) : Money.zero(currency);
        Money releasedOnDefects = defectsCleared ? accrued.minus(releasedOnCompletion) : Money.zero(currency);

        return new RetentionState(
                uncapped,
                cap,
                accrued,
                releasedOnCompletion,
                releasedOnDefects,
                accrued.minus(releasedOnCompletion).minus(releasedOnDefects),
                uncapped.minor() > cap.minor());
    }

    private static boolean hasPassed(LocalDate date, LocalDate asAt) {
        return date != null && SiteDates.compare(date, asAt) <= 0;
    }

    /** The share of the gross valuation actually being held, after the cap. */
    public Percent effectiveRate(Money gross) {
        if (gross.minor() == 0) {
            return Percent.ZERO;
        }
        double scaled = (double) (held.minor() * Percent.BASIS_POINTS_WHOLE) / gross.minor();
        return Percent.ofBasisPoints(Numbers.round(scaled));
    }

    /** What is left to release, for the defects liability report. */
    public Money outstandingRelease() {
        return held;
    }
}
