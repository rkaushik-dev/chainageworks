package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.progress.PackageEarning;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.valuation.PeriodSchedule;
import io.github.rkaushikdev.chainageworks.valuation.ValuationPeriod;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The S-curve: planned value against earned value at every cut-off of the schedule. */
public final class SCurve {

    private SCurve() {}

    /**
     * Planned against earned at every cut-off.
     *
     * <p>Earned is recomputed from scratch at each cut-off rather than accumulated, so a remeasure
     * or a late sign-off shows up on the period it belongs to instead of being smeared across the
     * ones after it. A schedule that runs past the baseline holds the baseline's last figure.
     *
     * @throws BadInputException when the baseline has no periods
     */
    public static List<CurvePoint> build(ProgressContext context, PeriodSchedule schedule, Baseline baseline) {
        List<Money> cumulative = baseline.cumulative();
        if (cumulative.isEmpty()) {
            throw new BadInputException("a baseline needs at least one period");
        }
        Money total = cumulative.get(cumulative.size() - 1);
        List<CurvePoint> points = new ArrayList<>(schedule.periods().size());
        for (int index = 0; index < schedule.periods().size(); index++) {
            ValuationPeriod period = schedule.periods().get(index);
            Money planned = index < cumulative.size() ? cumulative.get(index) : total;
            Money earned = Money.sum(
                    PackageEarning.earnAll(context, period.cutOff()).stream()
                            .map(PackageEarning::earnedValue)
                            .toList(),
                    total.currency());
            points.add(new CurvePoint(
                    period.number(),
                    period.cutOff(),
                    planned,
                    earned,
                    earned.minus(planned),
                    shareOf(planned, total),
                    shareOf(earned, total)));
        }
        return List.copyOf(points);
    }

    private static Percent shareOf(Money part, Money whole) {
        return whole.minor() == 0 ? Percent.ZERO : Percent.share(part, whole);
    }

    /** The variance at the last cut-off on the curve, if there is one. */
    public static Optional<Money> closingVariance(List<CurvePoint> points) {
        return points.isEmpty()
                ? Optional.empty()
                : Optional.of(points.get(points.size() - 1).variance());
    }

    /** The first period the job fell behind its baseline, if it did. */
    public static Optional<Integer> firstBehindPeriod(List<CurvePoint> points) {
        return points.stream().filter(CurvePoint::isBehind).findFirst().map(CurvePoint::period);
    }

    /** Periods where the earned figure went down, which needs explaining. */
    public static List<CurvePoint> regressions(List<CurvePoint> points) {
        List<CurvePoint> out = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).earned().minor() < points.get(i - 1).earned().minor()) {
                out.add(points.get(i));
            }
        }
        return List.copyOf(out);
    }
}
