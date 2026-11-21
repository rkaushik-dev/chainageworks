package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The cash forecast: a certified series turned into the money expected in the bank, month by month. */
public final class CashFlow {

    private CashFlow() {}

    /**
     * Turn a certified series into a forecast of cash in.
     *
     * <p>Late payment is modelled as a whole number of periods rather than a number of days, because
     * that is how it actually behaves on a monthly certificate: a payer who misses the due date does
     * not pay four days late, it pays with the next run. Modelling it in days produces a forecast
     * that is precisely wrong.
     *
     * @throws BadInputException for a negative number of late periods, or a certified series that is
     *     not one figure per period
     */
    public static List<CashPoint> build(CashFlowInput input) {
        int late = input.latePeriods() == null ? 0 : input.latePeriods();
        if (late < 0) {
            throw new BadInputException("late payment is a whole number of periods", Details.of("late", late));
        }
        if (input.certified().size() != input.periods().size()) {
            throw new BadInputException(
                    "the certified series does not match the schedule",
                    Details.of(
                            "certified", input.certified().size(),
                            "periods", input.periods().size()));
        }

        Money zero = Money.zero(input.currency());
        List<Money> certifiedSeries = input.certified();
        List<CashPoint> points = new ArrayList<>(input.periods().size());

        for (int index = 0; index < input.periods().size(); index++) {
            ValuationPeriod period = input.periods().get(index);
            Money certified = certifiedSeries.get(index);
            Money previous = index == 0 ? zero : certifiedSeries.get(index - 1);
            int paidIndex = index - late;
            Money received = paidIndex < 0 ? zero : certifiedSeries.get(paidIndex);
            Money receivedBefore = paidIndex <= 0 ? zero : certifiedSeries.get(paidIndex - 1);

            points.add(new CashPoint(
                    period.number(),
                    period.cutOff(),
                    period.dueOn(),
                    certified,
                    certified.minus(previous),
                    received.minus(receivedBefore),
                    received,
                    certified.minus(received)));
        }
        return List.copyOf(points);
    }

    /**
     * The most the job is ever owed at once, and the period it happens in.
     *
     * <p>Empty when the job is never owed anything, rather than the first period with a zero against
     * it. A caller asking for the peak is asking how much funding to arrange, and handing back a
     * period holding nothing answers a question nobody asked. The earliest period wins a tie.
     */
    public static Optional<CashPoint> peakExposure(List<CashPoint> points) {
        CashPoint worst = null;
        for (CashPoint point : points) {
            if (worst == null
                    || point.outstanding().minor() > worst.outstanding().minor()) {
                worst = point;
            }
        }
        return worst == null || worst.outstanding().minor() <= 0 ? Optional.empty() : Optional.of(worst);
    }

    /** Everything the forecast expects to come in. */
    public static Money totalReceipts(List<CashPoint> points, Currency currency) {
        return Money.sum(points.stream().map(CashPoint::receipt).toList(), currency);
    }

    /** Cash expected in over a window, both ends included, which is what a funding request asks for. */
    public static Money receiptsBetween(List<CashPoint> points, LocalDate from, LocalDate to, Currency currency) {
        return Money.sum(
                points.stream()
                        .filter(point -> SiteDates.compare(point.dueOn(), from) >= 0
                                && SiteDates.compare(point.dueOn(), to) <= 0)
                        .map(CashPoint::receipt)
                        .toList(),
                currency);
    }

    /** The first period after the first where the forecast says nothing new comes in, if there is one. */
    public static Optional<Integer> firstDryPeriod(List<CashPoint> points) {
        return points.stream()
                .filter(point -> point.receipt().minor() == 0 && point.period() > 1)
                .findFirst()
                .map(CashPoint::period);
    }
}
