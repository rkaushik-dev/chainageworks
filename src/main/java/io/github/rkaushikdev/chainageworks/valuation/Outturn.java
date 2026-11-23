package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSheet;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.PriceAdjustment;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationApplication;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The anticipated final account: everything the job is going to be worth.
 *
 * <p>Kept as separate parts rather than one number, because the five parts are argued separately
 * and by different people. Measured works are a surveyor's argument, variations are a contract
 * argument, damages are a programme argument, and rolling them into a single outturn means the first
 * person to disagree with any of them disagrees with all of it.
 *
 * @param asAt the date the account is taken at
 * @param contractSum the tendered contract sum
 * @param account the final account of the measured works, item by item
 * @param measuredWorks forecast value of the measured works, from the bill and the trend
 * @param variations what the variations in force add to, or take off, the bill
 * @param daywork signed daywork to date
 * @param priceAdjustment what the fluctuation clause does to the forecast permanent work
 * @param damages damages the employer would deduct at the forecast completion date
 * @param total the forecast outturn, damages taken off
 * @param movement the total against the contract sum; positive is over
 * @param movementPercent the movement as a share of the contract sum
 */
public record Outturn(
        LocalDate asAt,
        Money contractSum,
        FinalAccount account,
        Money measuredWorks,
        Money variations,
        Money daywork,
        PriceAdjustment priceAdjustment,
        Money damages,
        Money total,
        Money movement,
        Percent movementPercent) {

    /**
     * One part of the outturn as a final account letter sets it out.
     *
     * @param name what the part is
     * @param amount what it adds to the outturn, negative for a deduction
     */
    public record Part(String name, Money amount) {}

    /**
     * Roll the whole account up at a date.
     *
     * <p>Damages come off the total rather than sitting beside it, because that is what actually
     * happens to the money: the employer deducts them from the last certificate. Reporting them
     * alongside would let a job read as on budget while it was sixty days late.
     */
    public static Outturn build(OutturnInput input) {
        Currency currency = input.contractSum().currency();
        FinalAccount account = input.trendThreshold() == null
                ? FinalAccount.build(input.context(), input.asAt())
                : FinalAccount.build(input.context(), input.asAt(), input.trendThreshold());
        Bill bill = input.context().billHistory().billAsAt(input.asAt());

        Money variations = input.variations().isEmpty()
                ? Money.zero(currency)
                : VariationApplication.value(bill, input.variations(), input.asAt());

        Money daywork = input.dayworkSchedule() == null
                ? Money.zero(currency)
                : DayworkSheet.dayworkValue(input.dayworkSchedule(), input.dayworkSheets(), input.asAt());

        Money permanentWork = account.forecastOutturn().plus(variations).plus(daywork);
        PriceAdjustment adjustment = input.adjustment() == null
                ? PriceAdjustment.none(permanentWork, input.asAt())
                : input.adjustment().adjust(input.indices(), permanentWork, input.asAt());

        Money damages = damagesAt(input, currency);
        Money total = permanentWork.plus(adjustment.amount()).minus(damages);
        Money movement = total.minus(input.contractSum());

        return new Outturn(
                input.asAt(),
                input.contractSum(),
                account,
                account.forecastOutturn(),
                variations,
                daywork,
                adjustment,
                damages,
                total,
                movement,
                Percent.share(movement, input.contractSum()));
    }

    private static Money damagesAt(OutturnInput input, Currency currency) {
        CompletionTerms terms = input.completion();
        if (terms == null || input.calendar() == null) {
            return Money.zero(currency);
        }
        CompletionTerms.Position position = terms.position(input.delayEvents(), input.calendar(), input.asAt());
        return terms.damages(position, input.contractSum(), input.asAt(), Optional.ofNullable(input.takenOverOn()))
                .amount();
    }

    /**
     * How late the job is forecast to be at the input's date, in calendar days. Nought when the
     * contract fixes no completion date or no calendar is given.
     */
    public static int forecastDaysLate(OutturnInput input) {
        CompletionTerms terms = input.completion();
        if (terms == null || input.calendar() == null) {
            return 0;
        }
        return terms.position(input.delayEvents(), input.calendar(), input.asAt())
                .daysLate(input.asAt());
    }

    /** The parts of the outturn, in the order a final account letter sets them out. */
    public List<Part> parts() {
        return List.of(
                new Part("measured works", measuredWorks),
                new Part("variations", variations),
                new Part("daywork", daywork),
                new Part("price adjustment", priceAdjustment.amount()),
                new Part("liquidated damages", damages.negate()));
    }

    /** True when the outturn is forecast above the contract sum. */
    public boolean isOverContractSum() {
        return movement.minor() > 0;
    }

    /** The parts adding up to the total, which is the check a reviewer does by hand. */
    public boolean partsAddUp() {
        Money sum = Money.sum(parts().stream().map(Part::amount).toList(), total.currency());
        return sum.minor() == total.minor();
    }

    /** {@code outturn at 2025-03-25, 4200.5 under the contract sum}. */
    public String describe() {
        String state = isOverContractSum() ? "over" : "under";
        return "outturn at " + SiteDates.format(asAt) + ", " + Numbers.format(Math.abs(movement.minor()) / 100.0) + " "
                + state + " the contract sum";
    }
}
