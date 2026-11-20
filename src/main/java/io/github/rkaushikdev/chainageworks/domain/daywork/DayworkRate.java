package io.github.rkaushikdev.chainageworks.domain.daywork;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;

/**
 * One line of the daywork schedule: a grade of labour or an item of plant and its hourly rate.
 *
 * @param id the line's key
 * @param description what it is
 * @param rate the rate per hour
 */
public record DayworkRate(String id, String description, Rate rate) {

    /** A line priced per hour in sterling. */
    public static DayworkRate of(String id, String description, double perHour) {
        return of(id, description, perHour, Currency.GBP);
    }

    /** A line priced per hour in {@code currency}. */
    public static DayworkRate of(String id, String description, double perHour, Currency currency) {
        return new DayworkRate(id, description, Rate.of(perHour, currency, Unit.HR));
    }
}
