package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageAllocation;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.survey.RecordQuery;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.progress.PackageEarning;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The forecast outturn of the measured works: what the job is going to cost if it carries on the
 * way it has been going.
 *
 * <p>Two answers are combined, item by item. An item that has barely started is forecast at its
 * billed value, because the bill is the best information there is. An item that is well under way is
 * forecast from its own trend, because by then the ground has already told everyone what the bill
 * got wrong. The threshold between the two is the whole judgement, so it is a parameter and each
 * line says which side it fell.
 *
 * @param asAt the date the account is taken at
 * @param billTotal the bill in force, priced at its billed quantities
 * @param measuredToDate what has been measured against every item, at the rates in force
 * @param forecastOutturn the lines' forecasts added up
 * @param movement the forecast less the bill total; positive is over
 * @param lines one line per bill item, in bill order
 */
public record FinalAccount(
        LocalDate asAt,
        Money billTotal,
        Money measuredToDate,
        Money forecastOutturn,
        Money movement,
        List<Line> lines) {

    /**
     * How far through an item has to be before its trend is worth extrapolating.
     *
     * <p>Below this the sample is too small: an item three percent complete whose first measure ran
     * long would forecast at four times the bill, and a forecast that swings like that is worse than
     * no forecast at all.
     */
    public static final Percent DEFAULT_TREND_THRESHOLD = Percent.of(25);

    /** How far an item's quantity has to move off the bill before its rate is reopened. */
    public static final Percent RERATE_QUANTITY_SHARE = Percent.of(10);

    /** How much the movement has to be worth, against the contract sum, before anyone reopens a rate. */
    public static final Percent RERATE_VALUE_SHARE = Percent.ofBasisPoints(1);

    public FinalAccount {
        lines = List.copyOf(lines);
    }

    /**
     * One bill item in the final account.
     *
     * @param code the bill item
     * @param description what the bill calls it
     * @param billed what the bill says it is worth
     * @param measured what has actually been measured, at the rate in force
     * @param percentComplete how far through it is, weighted across its packages, to two places
     * @param forecast what it is forecast to be worth
     * @param movement the forecast less the billed value
     * @param extrapolated true when the forecast came from the trend rather than from the bill
     */
    public record Line(
            ItemCode code,
            String description,
            Money billed,
            Money measured,
            double percentComplete,
            Money forecast,
            Money movement,
            boolean extrapolated) {

        /**
         * The forecast movement as a share of the billed value, which at one rate is the same as the
         * share of the billed quantity. An item billed at nothing has moved by the whole of anything
         * forecast against it.
         */
        public Percent quantityMovement() {
            if (billed.isZero()) {
                return movement.isZero() ? Percent.ZERO : Percent.FULL;
            }
            return Percent.share(movement.abs(), billed);
        }

        /** {@code E423.1 18.96% complete, on the trend}. */
        public String describe() {
            String basis = extrapolated ? "trend" : "bill";
            return code.format() + " " + Numbers.format(percentComplete) + "% complete, on the " + basis;
        }
    }

    /** {@link #build(ProgressContext, LocalDate, Percent)} at the {@link #DEFAULT_TREND_THRESHOLD}. */
    public static FinalAccount build(ProgressContext context, LocalDate asAt) {
        return build(context, asAt, DEFAULT_TREND_THRESHOLD);
    }

    /**
     * The account at a date, each item forecast on its trend once it is at least {@code
     * trendThreshold} complete and something has been measured against it.
     */
    public static FinalAccount build(ProgressContext context, LocalDate asAt, Percent trendThreshold) {
        Bill bill = context.billHistory().billAsAt(asAt);
        Currency currency = bill.currency();
        Map<String, Double> fractions = packageFractions(context, asAt);

        List<Line> lines = bill.items().stream()
                .map(item -> lineFor(context, item, asAt, fractions, trendThreshold, currency))
                .toList();

        Money billTotal = Money.sum(lines.stream().map(Line::billed).toList(), currency);
        Money forecastOutturn = Money.sum(lines.stream().map(Line::forecast).toList(), currency);
        return new FinalAccount(
                asAt,
                billTotal,
                Money.sum(lines.stream().map(Line::measured).toList(), currency),
                forecastOutturn,
                forecastOutturn.minus(billTotal),
                lines);
    }

    /** How far each package has got, so an item can be weighted across them. */
    private static Map<String, Double> packageFractions(ProgressContext context, LocalDate asAt) {
        Map<String, Double> out = new HashMap<>();
        for (PackageEarning earning : PackageEarning.earnAll(context, asAt)) {
            out.put(earning.packageId(), earning.fraction().fraction());
        }
        return out;
    }

    private static Line lineFor(
            ProgressContext context,
            BillItem item,
            LocalDate asAt,
            Map<String, Double> fractions,
            Percent threshold,
            Currency currency) {
        Money billed = item.billedValue();
        String key = item.code().key();

        Money measuredValue = Money.zero(currency);
        long weight = 0;
        double weighted = 0;

        for (WorkPackage pkg : context.register().packages()) {
            Optional<PackageAllocation> allocation = pkg.allocations().stream()
                    .filter(a -> a.code().key().equals(key))
                    .findFirst();
            if (allocation.isEmpty()) {
                continue;
            }
            Optional<Quantity> measured = SurveyRecord.measuredQuantity(
                    context.records(),
                    asAt,
                    RecordQuery.ALL.withPackage(pkg.id()).withCode(item.code()));
            if (measured.isPresent()) {
                measuredValue = Money.sum(List.of(measuredValue, item.rate().extend(measured.get())), currency);
            }
            long share = allocation.get().quantity().milli();
            weight += share;
            weighted += share * fractions.getOrDefault(pkg.id(), 0.0);
        }

        double complete = weight == 0 ? 0 : weighted / weight;
        boolean canExtrapolate = item.isEarnable() && complete >= threshold.fraction() && measuredValue.minor() > 0;
        Money forecast = canExtrapolate
                ? Money.max(new Money((long) Numbers.round(measuredValue.minor() / complete), currency), measuredValue)
                : billed;

        return new Line(
                item.code(),
                item.description(),
                billed,
                measuredValue,
                Numbers.parse(Numbers.toFixed(complete * 100, 2)),
                forecast,
                forecast.minus(billed),
                canExtrapolate);
    }

    /**
     * Items whose quantity is forecast to move far enough off the bill that a new rate may be due,
     * the largest movement either way first.
     *
     * <p>Both tests have to be met, the way the remeasurement clauses set them: the quantity moves by
     * more than {@link #RERATE_QUANTITY_SHARE} of what was billed, and the movement is worth more
     * than {@link #RERATE_VALUE_SHARE} of the contract sum. The first alone would reopen the rate on
     * every small item that doubled, the second alone on every large item that drifted a little,
     * and neither is what the parties priced the risk of.
     */
    public List<Line> rerateCandidates(Money contractSum) {
        Money floor = RERATE_VALUE_SHARE.of(contractSum);
        return movingLines().stream()
                .filter(line -> line.quantityMovement().compareTo(RERATE_QUANTITY_SHARE) > 0)
                .filter(line -> line.movement().abs().compareTo(floor) > 0)
                .toList();
    }

    /** Lines whose forecast has moved away from the bill, the largest movement either way first. */
    public List<Line> movingLines() {
        List<Line> moving = new ArrayList<>(
                lines.stream().filter(line -> line.movement().minor() != 0).toList());
        moving.sort(
                Comparator.comparingLong((Line line) -> Math.abs(line.movement().minor()))
                        .reversed());
        return List.copyOf(moving);
    }
}
