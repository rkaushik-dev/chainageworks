import { describe, expect, it } from 'vitest';
import type { IndexReading } from '../../../../src/domain/fluctuation/index.js';
import {
  buildIndexSeries,
  describeSeries,
  findSeries,
  isProvisionalAt,
  latestReading,
  movementSince,
  provisionalMonths,
  readingAt,
  requireSeries,
  valueAt,
} from '../../../../src/domain/fluctuation/index.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';

const d = parseSiteDate;

const reading = (year: number, month: number, value: number, provisional = false) => ({
  year,
  month,
  value,
  provisional,
});

const labour = buildIndexSeries({
  id: 'IDX-LAB',
  name: 'civil engineering labour',
  readings: [
    reading(2024, 10, 100),
    reading(2024, 11, 101.4),
    reading(2024, 12, 102),
    reading(2025, 1, 103.6),
    reading(2025, 2, 104.2, true),
    reading(2025, 3, 105, true),
  ],
});

describe('buildIndexSeries', () => {
  it('sorts the readings into month order', () => {
    const jumbled = buildIndexSeries({
      id: 'IDX-X',
      readings: [reading(2025, 2, 104), reading(2024, 12, 102), reading(2025, 1, 103)],
    });
    expect(jumbled.readings.map((r) => r.value)).toEqual([102, 103, 104]);
  });

  it('takes the id as the name when none is given', () => {
    expect(buildIndexSeries({ id: 'IDX-X', readings: [] }).name).toBe('IDX-X');
  });

  it('refuses two figures for one month', () => {
    expect(() =>
      buildIndexSeries({
        id: 'IDX-X',
        readings: [reading(2025, 1, 103), reading(2025, 1, 104)],
      }),
    ).toThrow(/two figures for one month/);
  });

  it('refuses a month that is not a month', () => {
    expect(() => buildIndexSeries({ id: 'IDX-X', readings: [reading(2025, 13, 103)] })).toThrow(
      /month of the year/,
    );
  });

  it('refuses an index figure of nothing', () => {
    expect(() => buildIndexSeries({ id: 'IDX-X', readings: [reading(2025, 1, 0)] })).toThrow(
      /more than nothing/,
    );
  });
});

describe('readingAt', () => {
  it('takes the figure for the month itself', () => {
    expect(readingAt(labour, d('2025-01-25'))!.value).toBe(103.6);
  });

  it('carries the last published figure forward', () => {
    expect(readingAt(labour, d('2025-08-25'))!.value).toBe(105);
  });

  it('is null before the series begins', () => {
    expect(readingAt(labour, d('2024-08-25'))).toBeNull();
  });

  it('reads the same figure from any day inside the month', () => {
    expect(readingAt(labour, d('2024-12-01'))!.value).toBe(102);
    expect(readingAt(labour, d('2024-12-31'))!.value).toBe(102);
  });
});

describe('valueAt', () => {
  it('gives the figure', () => {
    expect(valueAt(labour, d('2024-12-25'))).toBe(102);
  });

  it('refuses a date the series does not reach back to', () => {
    expect(() => valueAt(labour, d('2024-01-01'))).toThrow(/index reading/);
  });
});

describe('isProvisionalAt', () => {
  it('says so while the figure is still provisional', () => {
    expect(isProvisionalAt(labour, d('2025-03-25'))).toBe(true);
  });

  it('says no once the figure is firm', () => {
    expect(isProvisionalAt(labour, d('2025-01-25'))).toBe(false);
  });

  it('says no rather than throwing before the series begins', () => {
    expect(isProvisionalAt(labour, d('2024-01-01'))).toBe(false);
  });
});

describe('provisionalMonths', () => {
  it('lists the months still to be firmed up', () => {
    expect(provisionalMonths(labour).map((r) => r.month)).toEqual([2, 3]);
  });

  it('lists nothing on a firm series', () => {
    const firm = buildIndexSeries({ id: 'IDX-F', readings: [reading(2025, 1, 100)] });
    expect(provisionalMonths(firm)).toEqual([]);
  });
});

describe('latestReading', () => {
  it('is the last month published', () => {
    expect(latestReading(labour)!.month).toBe(3);
  });

  it('is null on a series with nothing published', () => {
    expect(latestReading(buildIndexSeries({ id: 'IDX-E', readings: [] }))).toBeNull();
  });
});

describe('movementSince', () => {
  it('is the fraction the index has moved', () => {
    expect(movementSince(labour, d('2024-10-25'), d('2024-12-25'))).toBeCloseTo(0.02, 10);
  });

  it('is nothing at the base date itself', () => {
    expect(movementSince(labour, d('2024-10-25'), d('2024-10-25'))).toBe(0);
  });

  it('goes negative when the index falls back', () => {
    const falling = buildIndexSeries({
      id: 'IDX-FALL',
      readings: [reading(2025, 1, 110), reading(2025, 2, 99)],
    });
    expect(movementSince(falling, d('2025-01-25'), d('2025-02-25'))).toBeCloseTo(-0.1, 10);
  });

  it('refuses to read the index before the base date', () => {
    expect(() => movementSince(labour, d('2025-01-25'), d('2024-12-25'))).toThrow(
      /before the base date/,
    );
  });
});

describe('finding a series', () => {
  const all = [labour];

  it('gives null for one nobody loaded', () => {
    expect(findSeries(all, 'IDX-NONE')).toBeNull();
  });

  it('throws when the caller needs it', () => {
    expect(() => requireSeries(all, 'IDX-NONE')).toThrow(/index series/);
    expect(requireSeries(all, 'IDX-LAB').name).toBe('civil engineering labour');
  });
});

describe('describeSeries', () => {
  it('names the latest figure and whether it is firm', () => {
    expect(describeSeries(labour)).toBe(
      'IDX-LAB civil engineering labour, latest 105 for 2025-03, provisional',
    );
  });

  it('says so when nothing has been published', () => {
    const empty: IndexReading[] = [];
    expect(describeSeries(buildIndexSeries({ id: 'IDX-E', readings: empty }))).toContain(
      'no figures published',
    );
  });
});
