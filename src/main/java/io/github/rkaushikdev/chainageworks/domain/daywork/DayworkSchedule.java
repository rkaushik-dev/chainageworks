package io.github.rkaushikdev.chainageworks.domain.daywork;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The daywork schedule the contract was tendered with.
 *
 * <p>Daywork is the last resort: it is what work is paid at when it cannot be measured, and it is
 * priced from a schedule agreed at tender rather than from the bill. The percentage additions are
 * the part that gets argued over, so they are held separately from the rates and applied once, at
 * the end.
 *
 * @param currency what the rates are in
 * @param labour the labour lines
 * @param plant the plant lines
 * @param additions the percentage on each head
 * @param overtimeFactor what an hour outside normal working hours is worth, as a multiplier
 * @param standingFactor what an hour of plant standing idle is worth, as a share of its rate
 * @param submissionDays calendar days after the work within which a sheet has to be handed in,
 *     empty when the contract sets no deadline
 */
public record DayworkSchedule(
        Currency currency,
        List<DayworkRate> labour,
        List<DayworkRate> plant,
        DayworkAdditions additions,
        double overtimeFactor,
        double standingFactor,
        Optional<Integer> submissionDays) {

    /** Plant standing idle is paid at half its working rate unless the schedule says otherwise. */
    public static final double DEFAULT_STANDING_FACTOR = 0.5;

    public DayworkSchedule {
        labour = List.copyOf(labour);
        plant = List.copyOf(plant);
        submissionDays = submissionDays == null ? Optional.empty() : submissionDays;
    }

    /**
     * Build a schedule, refusing an overtime factor under one, two lines of one head sharing an
     * id, a line in another currency and a negative rate.
     */
    public static DayworkSchedule build(DayworkScheduleInput input) {
        Currency currency = input.currency() == null ? Currency.GBP : input.currency();
        double overtimeFactor = input.overtimeFactor() == null ? 1.5 : input.overtimeFactor();
        if (!Double.isFinite(overtimeFactor) || overtimeFactor < 1) {
            throw new BadInputException(
                    "an overtime factor is at least one", Details.of("overtimeFactor", overtimeFactor));
        }
        double standingFactor = input.standingFactor() == null ? DEFAULT_STANDING_FACTOR : input.standingFactor();
        if (!Double.isFinite(standingFactor) || standingFactor < 0 || standingFactor > 1) {
            throw new BadInputException(
                    "standing plant is paid at between none and all of its rate",
                    Details.of("standingFactor", standingFactor));
        }
        Optional<Integer> submissionDays = Optional.ofNullable(input.submissionDays());
        if (submissionDays.isPresent() && submissionDays.get() < 0) {
            throw new BadInputException(
                    "a deadline for daywork sheets is a whole number of days",
                    Details.of("submissionDays", submissionDays.get()));
        }
        for (List<DayworkRate> group : List.of(input.labour(), input.plant())) {
            Set<String> seen = new HashSet<>();
            for (DayworkRate entry : group) {
                if (!seen.add(entry.id())) {
                    throw new BadInputException("two daywork rates share an id", Details.of("rate", entry.id()));
                }
                if (entry.rate().currency() != currency) {
                    throw new BadInputException(
                            "a daywork rate is in another currency",
                            Details.of(
                                    "rate",
                                    entry.id(),
                                    "currency",
                                    entry.rate().currency().code()));
                }
                if (entry.rate().milliMinor() < 0) {
                    throw new BadInputException("a daywork rate cannot be negative", Details.of("rate", entry.id()));
                }
            }
        }
        DayworkAdditions defaults = DayworkAdditions.DEFAULT;
        DayworkAdditions additions = new DayworkAdditions(
                input.labourAddition() == null ? defaults.labour() : input.labourAddition(),
                input.plantAddition() == null ? defaults.plant() : input.plantAddition(),
                input.materialsAddition() == null ? defaults.materials() : input.materialsAddition());
        return new DayworkSchedule(
                currency, input.labour(), input.plant(), additions, overtimeFactor, standingFactor, submissionDays);
    }

    public Optional<DayworkRate> findLabourRate(String id) {
        return labour.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public DayworkRate requireLabourRate(String id) {
        return findLabourRate(id).orElseThrow(() -> new NotFoundException("daywork labour rate", id));
    }

    public Optional<DayworkRate> findPlantRate(String id) {
        return plant.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public DayworkRate requirePlantRate(String id) {
        return findPlantRate(id).orElseThrow(() -> new NotFoundException("daywork plant rate", id));
    }

    /**
     * Hours at a rate, with overtime charged at the contract factor.
     *
     * <p>The factor multiplies the rate and not the addition, which is what the schedule says: the
     * overheads on an hour do not go up because the hour was worked in the evening.
     */
    public static Money hoursValue(DayworkRate entry, double hours, double overtimeHours, double overtimeFactor) {
        Money normal = entry.rate().extend(Quantity.of(hours, Unit.HR));
        Money overtime = entry.rate().extend(Quantity.of(overtimeHours, Unit.HR));
        return normal.plus(overtime.scale(overtimeFactor));
    }

    /** A plant line priced: its working hours with overtime, and its standing hours at the standing rate. */
    public Money plantValue(PlantEntry entry) {
        DayworkRate rate = requirePlantRate(entry.rateId());
        Money working = hoursValue(rate, entry.hours(), entry.overtimeHours(), overtimeFactor);
        Money standing = rate.rate().extend(Quantity.of(entry.standingHours(), Unit.HR));
        return working.plus(standing.scale(standingFactor));
    }

    /** {@code value} with {@code addition} on top. */
    public static Money withAddition(Money value, Percent addition) {
        return value.plus(addition.of(value));
    }

    /** Nothing, in the schedule's currency. */
    public Money zero() {
        return Money.zero(currency);
    }

    /** {@code 2 labour rates, 2 plant rates, overtime at 1.50}. */
    public String describe() {
        return labour.size() + " labour rates, " + plant.size() + " plant rates, overtime at "
                + Numbers.toFixed(overtimeFactor, 2);
    }
}
