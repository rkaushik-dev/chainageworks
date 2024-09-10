import { BadInputError } from '../../errors.js';
import type { Result } from '../../result.js';
import { err, ok } from '../../result.js';

declare const siteDateBrand: unique symbol;

/**
 * A day on site, held as a count of days from 1970-01-01.
 *
 * Deliberately not a timestamp. A site diary entry, a valuation cut-off and an
 * inspection sign-off are all dated, never timed, and giving them an instant
 * would drag a timezone into every comparison. The one place a clock time
 * matters is the audit trail on a record, and that is a separate field.
 */
export type SiteDate = number & { readonly [siteDateBrand]: true };

export const MS_PER_DAY = 86_400_000;

export interface DateParts {
  readonly year: number;
  readonly month: number;
  readonly day: number;
}

const DATE_TEXT = /^(\d{4})-(\d{2})-(\d{2})$/;

function daysInMonth(year: number, month: number): number {
  if (month === 2) {
    const leap = (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
    return leap ? 29 : 28;
  }
  return [4, 6, 9, 11].includes(month) ? 30 : 31;
}

export function siteDate(year: number, month: number, day: number): SiteDate {
  if (!Number.isInteger(year) || year < 1900 || year > 2200) {
    throw new BadInputError('year is outside the range this contract can run in', { year });
  }
  if (!Number.isInteger(month) || month < 1 || month > 12) {
    throw new BadInputError('month must be 1 to 12', { month });
  }
  const limit = daysInMonth(year, month);
  if (!Number.isInteger(day) || day < 1 || day > limit) {
    throw new BadInputError('day is not in that month', { year, month, day, limit });
  }
  return (Date.UTC(year, month - 1, day) / MS_PER_DAY) as SiteDate;
}

export function siteDateParts(date: SiteDate): DateParts {
  const d = new Date(date * MS_PER_DAY);
  return { year: d.getUTCFullYear(), month: d.getUTCMonth() + 1, day: d.getUTCDate() };
}

export function tryParseSiteDate(text: string): Result<SiteDate> {
  const trimmed = text.trim();
  const m = DATE_TEXT.exec(trimmed);
  if (m === null) {
    return err(new BadInputError('a site date is written yyyy-mm-dd', { text: trimmed }));
  }
  try {
    return ok(siteDate(Number(m[1]), Number(m[2]), Number(m[3])));
  } catch (e) {
    return err(e as BadInputError);
  }
}

export function parseSiteDate(text: string): SiteDate {
  const r = tryParseSiteDate(text);
  if (!r.ok) throw r.error;
  return r.value;
}

export function formatSiteDate(date: SiteDate): string {
  const { year, month, day } = siteDateParts(date);
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(
    day,
  ).padStart(2, '0')}`;
}

export function addDays(date: SiteDate, days: number): SiteDate {
  if (!Number.isInteger(days)) {
    throw new BadInputError('can only step a whole number of days', { days });
  }
  return (date + days) as SiteDate;
}

/** Days from the first to the second. Negative when they are the other way. */
export function diffDays(from: SiteDate, to: SiteDate): number {
  return to - from;
}

export function compareSiteDates(a: SiteDate, b: SiteDate): number {
  return a - b;
}

export function siteDatesEqual(a: SiteDate, b: SiteDate): boolean {
  return a === b;
}

export function minSiteDate(a: SiteDate, b: SiteDate): SiteDate {
  return a <= b ? a : b;
}

export function maxSiteDate(a: SiteDate, b: SiteDate): SiteDate {
  return a >= b ? a : b;
}

/** ISO weekday, so Monday is 1 and Sunday is 7. */
export function dayOfWeek(date: SiteDate): number {
  const raw = new Date(date * MS_PER_DAY).getUTCDay();
  return raw === 0 ? 7 : raw;
}

export function isWeekend(date: SiteDate): boolean {
  return dayOfWeek(date) >= 6;
}

export function startOfMonth(date: SiteDate): SiteDate {
  const { year, month } = siteDateParts(date);
  return siteDate(year, month, 1);
}

export function endOfMonth(date: SiteDate): SiteDate {
  const { year, month } = siteDateParts(date);
  return siteDate(year, month, daysInMonth(year, month));
}

/** Step whole months, clamping onto the last day when the target is short. */
export function addMonths(date: SiteDate, months: number): SiteDate {
  if (!Number.isInteger(months)) {
    throw new BadInputError('can only step a whole number of months', { months });
  }
  const { year, month, day } = siteDateParts(date);
  const zeroBased = year * 12 + (month - 1) + months;
  const targetYear = Math.floor(zeroBased / 12);
  const targetMonth = zeroBased - targetYear * 12 + 1;
  const limit = daysInMonth(targetYear, targetMonth);
  return siteDate(targetYear, targetMonth, Math.min(day, limit));
}

export function isLastDayOfMonth(date: SiteDate): boolean {
  return date === endOfMonth(date);
}
