import { BadInputError } from '../../errors.js';
import type { SiteDate } from './siteDate.js';
import {
  addDays,
  compareSiteDates,
  endOfMonth,
  formatSiteDate,
  maxSiteDate,
  minSiteDate,
  siteDate,
  siteDateParts,
  startOfMonth,
  tryParseSiteDate,
} from './siteDate.js';

/**
 * A run of days, inclusive at both ends.
 *
 * This is the opposite convention to a chainage range, and on purpose. A
 * valuation period is written "1 to 31 March" and means both of those days;
 * writing it half open would put the last day of the month outside the month.
 * Lengths are counted in days, so there is no join to double count.
 */
export interface DateRange {
  readonly from: SiteDate;
  readonly to: SiteDate;
}

export function dateRange(from: SiteDate, to: SiteDate): DateRange {
  if (compareSiteDates(from, to) > 0) {
    throw new BadInputError('a date range ends on or after it starts', {
      from: formatSiteDate(from),
      to: formatSiteDate(to),
    });
  }
  return { from, to };
}

export function singleDay(date: SiteDate): DateRange {
  return { from: date, to: date };
}

/** Whole calendar month, which is what an interim valuation period usually is. */
export function monthRange(year: number, month: number): DateRange {
  const first = siteDate(year, month, 1);
  return { from: first, to: endOfMonth(first) };
}

export function monthRangeOf(date: SiteDate): DateRange {
  return { from: startOfMonth(date), to: endOfMonth(date) };
}

export function formatDateRange(range: DateRange): string {
  if (range.from === range.to) return formatSiteDate(range.from);
  return `${formatSiteDate(range.from)} to ${formatSiteDate(range.to)}`;
}

const RANGE_TEXT = /^(.+?)\s*(?:\bto\b|\.\.|--)\s*(.+)$/i;

export function parseDateRange(text: string): DateRange {
  const trimmed = text.trim();
  const m = RANGE_TEXT.exec(trimmed);
  if (m === null) {
    const single = tryParseSiteDate(trimmed);
    if (!single.ok) throw single.error;
    return singleDay(single.value);
  }
  const from = tryParseSiteDate(m[1] ?? '');
  if (!from.ok) throw from.error;
  const to = tryParseSiteDate(m[2] ?? '');
  if (!to.ok) throw to.error;
  return dateRange(from.value, to.value);
}

/** Days in the range, counting both ends. A single day is one day. */
export function rangeDays(range: DateRange): number {
  return range.to - range.from + 1;
}

export function containsDate(range: DateRange, date: SiteDate): boolean {
  return compareSiteDates(date, range.from) >= 0 && compareSiteDates(date, range.to) <= 0;
}

export function dateRangesOverlap(a: DateRange, b: DateRange): boolean {
  return maxSiteDate(a.from, b.from) <= minSiteDate(a.to, b.to);
}

export function dateRangesEqual(a: DateRange, b: DateRange): boolean {
  return a.from === b.from && a.to === b.to;
}

export function dateRangeCovers(outer: DateRange, inner: DateRange): boolean {
  return (
    compareSiteDates(outer.from, inner.from) <= 0 && compareSiteDates(outer.to, inner.to) >= 0
  );
}

export function intersectDateRanges(a: DateRange, b: DateRange): DateRange | null {
  const from = maxSiteDate(a.from, b.from);
  const to = minSiteDate(a.to, b.to);
  return compareSiteDates(from, to) <= 0 ? { from, to } : null;
}

export function compareDateRanges(a: DateRange, b: DateRange): number {
  const byFrom = compareSiteDates(a.from, b.from);
  return byFrom !== 0 ? byFrom : compareSiteDates(a.to, b.to);
}

/** Sort and merge, treating consecutive days as continuous. */
export function unionDateRanges(ranges: readonly DateRange[]): DateRange[] {
  const sorted = [...ranges].sort(compareDateRanges);
  const out: DateRange[] = [];
  for (const range of sorted) {
    const last = out[out.length - 1];
    if (last !== undefined && compareSiteDates(range.from, addDays(last.to, 1)) <= 0) {
      out[out.length - 1] = { from: last.from, to: maxSiteDate(last.to, range.to) };
    } else {
      out.push(range);
    }
  }
  return out;
}

export function* eachDate(range: DateRange): Generator<SiteDate> {
  for (let day = range.from; compareSiteDates(day, range.to) <= 0; day = addDays(day, 1)) {
    yield day;
  }
}

export function datesIn(range: DateRange): SiteDate[] {
  return [...eachDate(range)];
}

/**
 * Cut a range at every month boundary. A progress period that straddles a month
 * has to be reported against each one, because the bill revision in force can
 * change on the first of a month.
 */
export function splitByMonth(range: DateRange): DateRange[] {
  const out: DateRange[] = [];
  let cursor = range.from;
  while (compareSiteDates(cursor, range.to) <= 0) {
    const monthEnd = endOfMonth(cursor);
    const to = minSiteDate(monthEnd, range.to);
    out.push({ from: cursor, to });
    cursor = addDays(to, 1);
  }
  return out;
}

export function describeMonth(range: DateRange): string {
  const { year, month } = siteDateParts(range.from);
  return `${String(year)}-${String(month).padStart(2, '0')}`;
}
