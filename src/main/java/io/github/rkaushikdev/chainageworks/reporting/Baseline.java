package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Apportion;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the programme said would be earned by the end of each period.
 *
 * @param cumulative the planned figure to date at each cut-off, first period first
 */
public record Baseline(List<Money> cumulative) {

    public Baseline {
        cumulative = List.copyOf(cumulative);
    }

    /**
     * Spread a total across periods on an S shape.
     *
     * <p>A civil job does not earn in a straight line. It starts slowly while the site is set up,
     * runs hard through the middle, and tails off through testing and snagging. A straight line
     * baseline therefore reports the job as behind for the first third of its life and ahead for
     * the last third, whatever actually happened.
     *
     * @throws BadInputException when there are no periods
     */
    public static Baseline sCurve(Money total, int periods) {
        requirePeriods(periods);
        List<Double> weights = new ArrayList<>(periods);
        for (int i = 0; i < periods; i++) {
            double t = (i + 0.5) / periods;
            weights.add(StrictMath.pow(StrictMath.sin(Math.PI * t), 2));
        }
        return accumulate(total, Apportion.money(total, weights));
    }

    /**
     * An even spread, for a job that really does run flat.
     *
     * @throws BadInputException when there are no periods
     */
    public static Baseline linear(Money total, int periods) {
        requirePeriods(periods);
        return accumulate(total, Apportion.money(total, Collections.nCopies(periods, 1)));
    }

    private static void requirePeriods(int periods) {
        if (periods < 1) {
            throw new BadInputException("a baseline needs at least one period", Details.of("periods", periods));
        }
    }

    private static Baseline accumulate(Money total, List<Money> shares) {
        List<Money> cumulative = new ArrayList<>(shares.size());
        Money running = Money.zero(total.currency());
        for (Money share : shares) {
            running = running.plus(share);
            cumulative.add(running);
        }
        return new Baseline(cumulative);
    }
}
