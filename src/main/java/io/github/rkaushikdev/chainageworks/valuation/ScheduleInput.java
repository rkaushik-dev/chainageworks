package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;

/**
 * What a valuation schedule is built from.
 *
 * @param firstYear the year of the month the first period covers
 * @param firstMonth the month the first period covers
 * @param count how many periods
 * @param cutOffDay day of the month the works are measured to
 * @param issueDays working days from cut-off to the certificate being issued; null means seven
 * @param paymentDays calendar days from issue to payment falling due; null means fourteen
 * @param calendar the site calendar
 */
public record ScheduleInput(
        int firstYear,
        int firstMonth,
        int count,
        int cutOffDay,
        Integer issueDays,
        Integer paymentDays,
        SiteCalendar calendar) {

    public static ScheduleInput of(int firstYear, int firstMonth, int count, int cutOffDay, SiteCalendar calendar) {
        return new ScheduleInput(firstYear, firstMonth, count, cutOffDay, null, null, calendar);
    }

    public ScheduleInput withIssueDays(int other) {
        return new ScheduleInput(firstYear, firstMonth, count, cutOffDay, other, paymentDays, calendar);
    }

    public ScheduleInput withPaymentDays(int other) {
        return new ScheduleInput(firstYear, firstMonth, count, cutOffDay, issueDays, other, calendar);
    }
}
