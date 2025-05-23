import { BadInputError, NotFoundError } from '../../errors.js';
import type { SiteDate } from '../time/siteDate.js';
import { compareSiteDates, formatSiteDate, siteDateParts } from '../time/siteDate.js';

/**
 * One published figure of a cost index, and whether it is final.
 *
 * The provisional flag is the point. Construction cost indices are published
 * provisionally and firmed up months later, and a certificate worked out on a
 * provisional figure has to be reworked when the firm one lands. A series that
 * forgets which figures were provisional cannot tell anybody which
 * certificates need revisiting.
 */
export interface IndexReading {
  readonly year: number;
  readonly month: number;
  readonly value: number;
  readonly provisional: boolean;
}

export interface IndexSeries {
  readonly id: string;
  readonly name: string;
  readonly readings: readonly IndexReading[];
}

export interface IndexSeriesInput {
  readonly id: string;
  readonly name?: string;
  readonly readings: readonly IndexReading[];
}

function monthKey(year: number, month: number): number {
  return year * 12 + (month - 1);
}

export function buildIndexSeries(input: IndexSeriesInput): IndexSeries {
  const seen = new Set<number>();
  for (const reading of input.readings) {
    if (!Number.isInteger(reading.month) || reading.month < 1 || reading.month > 12) {
      throw new BadInputError('an index reading is filed against a month of the year', {
        series: input.id,
        month: reading.month,
      });
    }
    if (!Number.isFinite(reading.value) || reading.value <= 0) {
      throw new BadInputError('an index figure has to be more than nothing', {
        series: input.id,
        value: reading.value,
      });
    }
    const key = monthKey(reading.year, reading.month);
    if (seen.has(key)) {
      throw new BadInputError('the series has two figures for one month', {
        series: input.id,
        year: reading.year,
        month: reading.month,
      });
    }
    seen.add(key);
  }
  return {
    id: input.id,
    name: input.name ?? input.id,
    readings: [...input.readings].sort(
      (a, b) => monthKey(a.year, a.month) - monthKey(b.year, b.month),
    ),
  };
}

/**
 * The figure in force for a date.
 *
 * The reading for the month itself is used when it exists, and the last one
 * before it otherwise. Indices are published in arrears, so the month a
 * valuation falls in usually has no figure yet, and carrying the last
 * published one forward is what the contract says to do rather than a
 * convenience.
 */
export function readingAt(series: IndexSeries, date: SiteDate): IndexReading | null {
  const { year, month } = siteDateParts(date);
  const wanted = monthKey(year, month);
  let found: IndexReading | null = null;
  for (const reading of series.readings) {
    if (monthKey(reading.year, reading.month) > wanted) break;
    found = reading;
  }
  return found;
}

export function valueAt(series: IndexSeries, date: SiteDate): number {
  const reading = readingAt(series, date);
  if (reading === null) {
    throw new NotFoundError('index reading', `${series.id} at ${formatSiteDate(date)}`);
  }
  return reading.value;
}

/** True when the figure that would be used has not been firmed up yet. */
export function isProvisionalAt(series: IndexSeries, date: SiteDate): boolean {
  return readingAt(series, date)?.provisional ?? false;
}

export function latestReading(series: IndexSeries): IndexReading | null {
  return series.readings.at(-1) ?? null;
}

/** Months of the series that are still provisional, oldest first. */
export function provisionalMonths(series: IndexSeries): IndexReading[] {
  return series.readings.filter((reading) => reading.provisional);
}

export function findSeries(all: readonly IndexSeries[], id: string): IndexSeries | null {
  return all.find((series) => series.id === id) ?? null;
}

export function requireSeries(all: readonly IndexSeries[], id: string): IndexSeries {
  const found = findSeries(all, id);
  if (found === null) throw new NotFoundError('index series', id);
  return found;
}

/**
 * How far an index has moved since a base date, as a fraction of one.
 *
 * Returned as a fraction rather than a percentage because it is multiplied by
 * a weighting straight afterwards, and the two conventions do not survive
 * being mixed. A tenth of a percent lost here is thousands of pounds on a
 * contract sum.
 */
export function movementSince(series: IndexSeries, base: SiteDate, asAt: SiteDate): number {
  if (compareSiteDates(asAt, base) < 0) {
    throw new BadInputError('an index cannot be read before the base date', {
      series: series.id,
      base: formatSiteDate(base),
      asAt: formatSiteDate(asAt),
    });
  }
  const from = valueAt(series, base);
  return (valueAt(series, asAt) - from) / from;
}

export function describeSeries(series: IndexSeries): string {
  const latest = latestReading(series);
  if (latest === null) return `${series.id} ${series.name}, no figures published`;
  const state = latest.provisional ? 'provisional' : 'firm';
  return (
    `${series.id} ${series.name}, latest ${String(latest.value)} ` +
    `for ${String(latest.year)}-${String(latest.month).padStart(2, '0')}, ${state}`
  );
}
