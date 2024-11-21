import { BadInputError, NotFoundError } from '../errors.js';
import type { SiteCalendar } from '../domain/time/calendar.js';
import { addWorkingDays, isWorkingDay, previousWorkingDay } from '../domain/time/calendar.js';
import type { DateRange } from '../domain/time/dateRange.js';
import { containsDate, dateRange } from '../domain/time/dateRange.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import {
  addDays,
  addMonths,
  compareSiteDates,
  endOfMonth,
  formatSiteDate,
  siteDate,
  siteDateParts,
} from '../domain/time/siteDate.js';

/**
 * One interim valuation.
 *
 * The cut-off is the date the works are measured to, not the date the paperwork
 * is done. Everything the engine is asked is asked as at a cut-off, so a
 * certificate can be rebuilt months later and come out the same.
 */
export interface ValuationPeriod {
  readonly number: number;
  readonly range: DateRange;
  readonly cutOff: SiteDate;
  readonly issuedOn: SiteDate;
  readonly dueOn: SiteDate;
}

export interface PeriodSchedule {
  readonly periods: readonly ValuationPeriod[];
}

export interface ScheduleInput {
  /** The month the first period covers. */
  readonly firstYear: number;
  readonly firstMonth: number;
  readonly count: number;
  /** Day of the month the works are measured to. */
  readonly cutOffDay: number;
  /** Working days from cut-off to the certificate being issued. */
  readonly issueDays?: number;
  /** Calendar days from issue to payment falling due. */
  readonly paymentDays?: number;
  readonly calendar: SiteCalendar;
}

/**
 * A cut-off that lands on a Sunday is pulled back to the Friday, because the
 * measurement is taken on site and nobody is there. The calendar therefore
 * decides the cut-off, which decides which sign-offs and which bill revision a
 * valuation reads. That chain is the reason the calendar is not a report
 * setting.
 */
export function resolveCutOff(
  calendar: SiteCalendar,
  year: number,
  month: number,
  day: number,
): SiteDate {
  const monthEnd = endOfMonth(siteDate(year, month, 1));
  const wanted = Math.min(day, siteDateParts(monthEnd).day);
  const nominal = siteDate(year, month, wanted);
  return isWorkingDay(calendar, nominal) ? nominal : previousWorkingDay(calendar, nominal);
}

export function buildSchedule(input: ScheduleInput): PeriodSchedule {
  if (!Number.isInteger(input.count) || input.count < 1) {
    throw new BadInputError('a schedule needs at least one period', { count: input.count });
  }
  if (!Number.isInteger(input.cutOffDay) || input.cutOffDay < 1 || input.cutOffDay > 31) {
    throw new BadInputError('the cut off day has to be a day of the month', {
      cutOffDay: input.cutOffDay,
    });
  }
  const issueDays = input.issueDays ?? 7;
  const paymentDays = input.paymentDays ?? 14;
  const periods: ValuationPeriod[] = [];
  let previousCutOff: SiteDate | null = null;

  for (let i = 0; i < input.count; i += 1) {
    const anchor = addMonths(siteDate(input.firstYear, input.firstMonth, 1), i);
    const { year, month } = siteDateParts(anchor);
    const cutOff = resolveCutOff(input.calendar, year, month, input.cutOffDay);
    const from = previousCutOff === null ? anchor : addDays(previousCutOff, 1);
    const issuedOn = addWorkingDays(input.calendar, cutOff, issueDays);
    periods.push({
      number: i + 1,
      range: dateRange(from, cutOff),
      cutOff,
      issuedOn,
      dueOn: addDays(issuedOn, paymentDays),
    });
    previousCutOff = cutOff;
  }
  return { periods };
}

export function periodByNumber(schedule: PeriodSchedule, number: number): ValuationPeriod {
  const period = schedule.periods.find((p) => p.number === number);
  if (period === undefined) {
    throw new NotFoundError('valuation period', String(number));
  }
  return period;
}

export function findPeriodByNumber(
  schedule: PeriodSchedule,
  number: number,
): ValuationPeriod | null {
  return schedule.periods.find((p) => p.number === number) ?? null;
}

/** The period a date falls inside, if any. */
export function periodFor(schedule: PeriodSchedule, date: SiteDate): ValuationPeriod | null {
  return schedule.periods.find((p) => containsDate(p.range, date)) ?? null;
}

/** The last period whose cut-off has passed on a date. */
export function periodAsAt(schedule: PeriodSchedule, date: SiteDate): ValuationPeriod | null {
  let found: ValuationPeriod | null = null;
  for (const period of schedule.periods) {
    if (compareSiteDates(period.cutOff, date) <= 0) found = period;
    else break;
  }
  return found;
}

export function previousPeriod(
  schedule: PeriodSchedule,
  period: ValuationPeriod,
): ValuationPeriod | null {
  return findPeriodByNumber(schedule, period.number - 1);
}

export function periodCount(schedule: PeriodSchedule): number {
  return schedule.periods.length;
}

export function describePeriod(period: ValuationPeriod): string {
  return (
    `valuation ${String(period.number)} to ${formatSiteDate(period.cutOff)}, ` +
    `issued ${formatSiteDate(period.issuedOn)}, due ${formatSiteDate(period.dueOn)}`
  );
}
