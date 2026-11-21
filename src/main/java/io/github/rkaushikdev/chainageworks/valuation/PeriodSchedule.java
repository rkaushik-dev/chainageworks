package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.time.DateRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The interim valuations of a contract, in order.
 *
 * @param periods the periods, numbered from one
 */
public record PeriodSchedule(List<ValuationPeriod> periods) {

    private static final int DEFAULT_ISSUE_DAYS = 7;
    private static final int DEFAULT_PAYMENT_DAYS = 14;
    private static final int LAST_DAY_OF_ANY_MONTH = 31;

    public PeriodSchedule {
        periods = List.copyOf(periods);
    }

    /**
     * The cut-off for a month.
     *
     * <p>A cut-off that lands on a Sunday is pulled back to the Friday, because the measurement is
     * taken on site and nobody is there. The calendar therefore decides the cut-off, which decides
     * which sign-offs and which bill revision a valuation reads. That chain is the reason the
     * calendar is not a report setting.
     */
    public static LocalDate resolveCutOff(SiteCalendar calendar, int year, int month, int day) {
        LocalDate monthEnd = SiteDates.endOfMonth(SiteDates.of(year, month, 1));
        int wanted = Math.min(day, SiteDates.parts(monthEnd).day());
        LocalDate nominal = SiteDates.of(year, month, wanted);
        return calendar.isWorkingDay(nominal) ? nominal : calendar.previousWorkingDay(nominal);
    }

    public static PeriodSchedule build(ScheduleInput input) {
        if (input.count() < 1) {
            throw new BadInputException("a schedule needs at least one period", Details.of("count", input.count()));
        }
        if (input.cutOffDay() < 1 || input.cutOffDay() > LAST_DAY_OF_ANY_MONTH) {
            throw new BadInputException(
                    "the cut off day has to be a day of the month", Details.of("cutOffDay", input.cutOffDay()));
        }
        int issueDays = input.issueDays() == null ? DEFAULT_ISSUE_DAYS : input.issueDays();
        int paymentDays = input.paymentDays() == null ? DEFAULT_PAYMENT_DAYS : input.paymentDays();
        List<ValuationPeriod> periods = new ArrayList<>();
        LocalDate previousCutOff = null;

        for (int i = 0; i < input.count(); i++) {
            LocalDate anchor = SiteDates.addMonths(SiteDates.of(input.firstYear(), input.firstMonth(), 1), i);
            SiteDates.DateParts parts = SiteDates.parts(anchor);
            LocalDate cutOff = resolveCutOff(input.calendar(), parts.year(), parts.month(), input.cutOffDay());
            LocalDate from = previousCutOff == null ? anchor : SiteDates.addDays(previousCutOff, 1);
            LocalDate issuedOn = input.calendar().addWorkingDays(cutOff, issueDays);
            periods.add(new ValuationPeriod(
                    i + 1, DateRange.of(from, cutOff), cutOff, issuedOn, SiteDates.addDays(issuedOn, paymentDays)));
            previousCutOff = cutOff;
        }
        return new PeriodSchedule(periods);
    }

    public ValuationPeriod byNumber(int number) {
        return findByNumber(number)
                .orElseThrow(() -> new NotFoundException("valuation period", String.valueOf(number)));
    }

    public Optional<ValuationPeriod> findByNumber(int number) {
        return periods.stream().filter(p -> p.number() == number).findFirst();
    }

    /** The period a date falls inside, if any. */
    public Optional<ValuationPeriod> periodFor(LocalDate date) {
        return periods.stream().filter(p -> p.range().contains(date)).findFirst();
    }

    /** The last period whose cut-off has passed on a date. */
    public Optional<ValuationPeriod> asAt(LocalDate date) {
        ValuationPeriod found = null;
        for (ValuationPeriod period : periods) {
            if (SiteDates.compare(period.cutOff(), date) <= 0) {
                found = period;
            } else {
                break;
            }
        }
        return Optional.ofNullable(found);
    }

    public Optional<ValuationPeriod> previous(ValuationPeriod period) {
        return findByNumber(period.number() - 1);
    }

    public int count() {
        return periods.size();
    }
}
