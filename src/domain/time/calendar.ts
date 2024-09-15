import { BadInputError } from '../../errors.js';
import type { DateRange } from './dateRange.js';
import { containsDate, eachDate } from './dateRange.js';
import type { SiteDate } from './siteDate.js';
import { addDays, compareSiteDates, dayOfWeek, formatSiteDate } from './siteDate.js';

export type StanddownReason =
  | 'weather'
  | 'ground_conditions'
  | 'access'
  | 'industrial'
  | 'other';

/**
 * A day the works were shut down, taken from the site diary.
 *
 * A standdown is not a holiday. The day was a working day, the crew were there,
 * and nothing was built. That difference matters twice over: it is what an
 * extension of time claim is built on, and it is why a period with the same
 * number of working days can carry very different progress.
 */
export interface Standdown {
  readonly date: SiteDate;
  readonly reason: StanddownReason;
  readonly note?: string;
}

export interface SiteCalendar {
  readonly id: string;
  readonly name: string;
  /** ISO weekday numbers that are worked, Monday being 1. */
  readonly workingDays: readonly number[];
  readonly holidays: readonly SiteDate[];
  readonly standdowns: readonly Standdown[];
}

export interface CalendarInput {
  readonly id: string;
  readonly name: string;
  readonly workingDays?: readonly number[];
  readonly holidays?: readonly SiteDate[];
  readonly standdowns?: readonly Standdown[];
}

/** Monday to Friday, which is what a contract says unless it says otherwise. */
export const DEFAULT_WORKING_DAYS: readonly number[] = [1, 2, 3, 4, 5];

export function buildCalendar(input: CalendarInput): SiteCalendar {
  const requested = input.workingDays ?? DEFAULT_WORKING_DAYS;
  for (const day of requested) {
    if (!Number.isInteger(day) || day < 1 || day > 7) {
      throw new BadInputError('a working day is an iso weekday from 1 to 7', { day });
    }
  }
  const workingDays = [...new Set(requested)].sort((a, b) => a - b);
  if (workingDays.length === 0) {
    throw new BadInputError('a calendar has to work at least one day a week', {
      calendar: input.id,
    });
  }
  const holidays = [...new Set(input.holidays ?? [])].sort(compareSiteDates);
  const standdowns = dedupeStanddowns(input.standdowns ?? []);
  return { id: input.id, name: input.name, workingDays, holidays, standdowns };
}

/** One standdown per day. A later entry for the same day replaces an earlier one. */
function dedupeStanddowns(standdowns: readonly Standdown[]): Standdown[] {
  const byDate = new Map<number, Standdown>();
  for (const standdown of standdowns) {
    byDate.set(standdown.date, standdown);
  }
  return [...byDate.values()].sort((a, b) => compareSiteDates(a.date, b.date));
}

export function isHoliday(calendar: SiteCalendar, date: SiteDate): boolean {
  return calendar.holidays.includes(date);
}

/** A day the contract expects work on, holidays already taken out. */
export function isWorkingDay(calendar: SiteCalendar, date: SiteDate): boolean {
  return calendar.workingDays.includes(dayOfWeek(date)) && !isHoliday(calendar, date);
}

export function standdownOn(calendar: SiteCalendar, date: SiteDate): Standdown | null {
  return calendar.standdowns.find((s) => s.date === date) ?? null;
}

/** A working day that was not stood down: the only kind that builds anything. */
export function isProductiveDay(calendar: SiteCalendar, date: SiteDate): boolean {
  return isWorkingDay(calendar, date) && standdownOn(calendar, date) === null;
}

export function workingDaysIn(calendar: SiteCalendar, range: DateRange): number {
  let count = 0;
  for (const date of eachDate(range)) {
    if (isWorkingDay(calendar, date)) count += 1;
  }
  return count;
}

export function productiveDaysIn(calendar: SiteCalendar, range: DateRange): number {
  let count = 0;
  for (const date of eachDate(range)) {
    if (isProductiveDay(calendar, date)) count += 1;
  }
  return count;
}

/**
 * Standdowns that actually cost something. A standdown booked on a Sunday on a
 * five day calendar is a diary note, not a lost day, and counting it would
 * inflate every delay claim built on this.
 */
export function standdownsIn(calendar: SiteCalendar, range: DateRange): Standdown[] {
  return calendar.standdowns.filter(
    (s) => containsDate(range, s.date) && isWorkingDay(calendar, s.date),
  );
}

export function standdownDaysIn(calendar: SiteCalendar, range: DateRange): number {
  return standdownsIn(calendar, range).length;
}

export function nextWorkingDay(calendar: SiteCalendar, date: SiteDate): SiteDate {
  let cursor = addDays(date, 1);
  for (let guard = 0; guard < 400; guard += 1) {
    if (isWorkingDay(calendar, cursor)) return cursor;
    cursor = addDays(cursor, 1);
  }
  throw new BadInputError('no working day found within a year', {
    calendar: calendar.id,
    from: formatSiteDate(date),
  });
}

export function previousWorkingDay(calendar: SiteCalendar, date: SiteDate): SiteDate {
  let cursor = addDays(date, -1);
  for (let guard = 0; guard < 400; guard += 1) {
    if (isWorkingDay(calendar, cursor)) return cursor;
    cursor = addDays(cursor, -1);
  }
  throw new BadInputError('no working day found within a year', {
    calendar: calendar.id,
    from: formatSiteDate(date),
  });
}

/** Step whole working days. Zero returns the day itself, worked or not. */
export function addWorkingDays(calendar: SiteCalendar, date: SiteDate, days: number): SiteDate {
  if (!Number.isInteger(days)) {
    throw new BadInputError('can only step a whole number of working days', { days });
  }
  let cursor = date;
  const step = days >= 0 ? nextWorkingDay : previousWorkingDay;
  for (let i = 0; i < Math.abs(days); i += 1) {
    cursor = step(calendar, cursor);
  }
  return cursor;
}

/** Working days from one date to another, counting neither end. */
export function workingDaysBetween(
  calendar: SiteCalendar,
  from: SiteDate,
  to: SiteDate,
): number {
  if (compareSiteDates(from, to) > 0) {
    return -workingDaysBetween(calendar, to, from);
  }
  let count = 0;
  for (let cursor = addDays(from, 1); compareSiteDates(cursor, to) < 0; ) {
    if (isWorkingDay(calendar, cursor)) count += 1;
    cursor = addDays(cursor, 1);
  }
  return count;
}
