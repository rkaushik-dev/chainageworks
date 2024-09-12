import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import {
  compareDateRanges,
  containsDate,
  dateRange,
  dateRangeCovers,
  dateRangesEqual,
  dateRangesOverlap,
  datesIn,
  describeMonth,
  formatDateRange,
  intersectDateRanges,
  monthRange,
  monthRangeOf,
  parseDateRange,
  rangeDays,
  singleDay,
  splitByMonth,
  unionDateRanges,
} from '../../../../src/domain/time/dateRange.js';
import { formatSiteDate, parseSiteDate } from '../../../../src/domain/time/siteDate.js';

const d = parseSiteDate;
const r = (from: string, to: string) => dateRange(d(from), d(to));
const show = (range: { from: number; to: number }) => `${formatDateRange(range as never)}`;

describe('dateRange', () => {
  it('holds both ends', () => {
    expect(show(r('2025-03-01', '2025-03-31'))).toBe('2025-03-01 to 2025-03-31');
  });

  it('allows a single day', () => {
    expect(rangeDays(singleDay(d('2025-03-14')))).toBe(1);
  });

  it('refuses a range that ends before it starts', () => {
    expect(() => r('2025-03-31', '2025-03-01')).toThrow(BadInputError);
    expect(() => r('2025-03-31', '2025-03-01')).toThrow(/ends on or after it starts/);
  });
});

describe('rangeDays', () => {
  it('counts both ends, so march is thirty one days', () => {
    expect(rangeDays(r('2025-03-01', '2025-03-31'))).toBe(31);
  });

  it('counts a leap february as twenty nine', () => {
    expect(rangeDays(monthRange(2024, 2))).toBe(29);
    expect(rangeDays(monthRange(2025, 2))).toBe(28);
  });
});

describe('monthRange', () => {
  it('builds a whole calendar month', () => {
    expect(show(monthRange(2025, 3))).toBe('2025-03-01 to 2025-03-31');
    expect(show(monthRange(2025, 4))).toBe('2025-04-01 to 2025-04-30');
  });

  it('builds the month a date falls in', () => {
    expect(show(monthRangeOf(d('2025-03-14')))).toBe('2025-03-01 to 2025-03-31');
  });

  it('names the month for a report heading', () => {
    expect(describeMonth(monthRange(2025, 3))).toBe('2025-03');
    expect(describeMonth(monthRange(2025, 11))).toBe('2025-11');
  });
});

describe('formatDateRange', () => {
  it('writes a single day once', () => {
    expect(formatDateRange(singleDay(d('2025-03-14')))).toBe('2025-03-14');
  });
});

describe('parseDateRange', () => {
  it('reads the word form and the dash forms', () => {
    expect(show(parseDateRange('2025-03-01 to 2025-03-31'))).toBe('2025-03-01 to 2025-03-31');
    expect(show(parseDateRange('2025-03-01 .. 2025-03-31'))).toBe('2025-03-01 to 2025-03-31');
    expect(show(parseDateRange('2025-03-01 -- 2025-03-31'))).toBe('2025-03-01 to 2025-03-31');
  });

  it('reads a bare date as one day', () => {
    expect(rangeDays(parseDateRange('2025-03-14'))).toBe(1);
  });

  it('throws on either end being unreadable', () => {
    expect(() => parseDateRange('nope to 2025-03-31')).toThrow(BadInputError);
    expect(() => parseDateRange('2025-03-01 to nope')).toThrow(BadInputError);
    expect(() => parseDateRange('nonsense')).toThrow(BadInputError);
  });

  it('throws when the range runs backwards', () => {
    expect(() => parseDateRange('2025-03-31 to 2025-03-01')).toThrow(/ends on or after/);
  });
});

describe('containsDate', () => {
  it('includes both ends', () => {
    const march = monthRange(2025, 3);
    expect(containsDate(march, d('2025-03-01'))).toBe(true);
    expect(containsDate(march, d('2025-03-31'))).toBe(true);
    expect(containsDate(march, d('2025-02-28'))).toBe(false);
    expect(containsDate(march, d('2025-04-01'))).toBe(false);
  });
});

describe('overlap and cover', () => {
  it('counts a shared single day as an overlap', () => {
    expect(
      dateRangesOverlap(r('2025-03-01', '2025-03-15'), r('2025-03-15', '2025-03-31')),
    ).toBe(true);
  });

  it('is false for consecutive ranges that do not share a day', () => {
    expect(dateRangesOverlap(monthRange(2025, 3), monthRange(2025, 4))).toBe(false);
  });

  it('compares equality on both ends', () => {
    expect(dateRangesEqual(monthRange(2025, 3), r('2025-03-01', '2025-03-31'))).toBe(true);
    expect(dateRangesEqual(monthRange(2025, 3), monthRange(2025, 4))).toBe(false);
  });

  it('covers an inner range including the shared ends', () => {
    expect(dateRangeCovers(monthRange(2025, 3), r('2025-03-10', '2025-03-20'))).toBe(true);
    expect(dateRangeCovers(monthRange(2025, 3), r('2025-03-20', '2025-04-02'))).toBe(false);
  });
});

describe('intersectDateRanges', () => {
  it('returns the shared days', () => {
    expect(show(intersectDateRanges(r('2025-03-01', '2025-03-20'), monthRange(2025, 3))!)).toBe(
      '2025-03-01 to 2025-03-20',
    );
  });

  it('returns a single shared day', () => {
    const shared = intersectDateRanges(
      r('2025-03-01', '2025-03-15'),
      r('2025-03-15', '2025-03-31'),
    );
    expect(formatSiteDate(shared!.from)).toBe('2025-03-15');
    expect(shared!.from).toBe(shared!.to);
  });

  it('returns null when nothing is shared', () => {
    expect(intersectDateRanges(monthRange(2025, 3), monthRange(2025, 5))).toBeNull();
  });
});

describe('unionDateRanges', () => {
  it('merges overlapping ranges', () => {
    const merged = unionDateRanges([
      r('2025-03-10', '2025-03-20'),
      r('2025-03-01', '2025-03-15'),
    ]);
    expect(merged.map(show)).toEqual(['2025-03-01 to 2025-03-20']);
  });

  it('merges ranges that are simply consecutive days', () => {
    const merged = unionDateRanges([monthRange(2025, 3), monthRange(2025, 4)]);
    expect(merged.map(show)).toEqual(['2025-03-01 to 2025-04-30']);
  });

  it('keeps ranges with a real gap between them', () => {
    const merged = unionDateRanges([monthRange(2025, 5), monthRange(2025, 3)]);
    expect(merged).toHaveLength(2);
  });

  it('handles an empty list and leaves the input alone', () => {
    const input = [monthRange(2025, 5), monthRange(2025, 3)];
    expect(unionDateRanges([])).toEqual([]);
    unionDateRanges(input);
    expect(describeMonth(input[0]!)).toBe('2025-05');
  });
});

describe('datesIn', () => {
  it('lists every day inclusive', () => {
    const days = datesIn(r('2025-03-01', '2025-03-03'));
    expect(days.map(formatSiteDate)).toEqual(['2025-03-01', '2025-03-02', '2025-03-03']);
  });

  it('lists one day for a single day range', () => {
    expect(datesIn(singleDay(d('2025-03-14')))).toHaveLength(1);
  });

  it('lists a whole month', () => {
    expect(datesIn(monthRange(2025, 3))).toHaveLength(31);
  });
});

describe('splitByMonth', () => {
  it('leaves a range inside one month alone', () => {
    expect(splitByMonth(r('2025-03-05', '2025-03-20')).map(show)).toEqual([
      '2025-03-05 to 2025-03-20',
    ]);
  });

  it('cuts at every month boundary', () => {
    expect(splitByMonth(r('2025-02-20', '2025-04-05')).map(show)).toEqual([
      '2025-02-20 to 2025-02-28',
      '2025-03-01 to 2025-03-31',
      '2025-04-01 to 2025-04-05',
    ]);
  });

  it('cuts across a year boundary', () => {
    expect(splitByMonth(r('2024-12-28', '2025-01-03')).map(show)).toEqual([
      '2024-12-28 to 2024-12-31',
      '2025-01-01 to 2025-01-03',
    ]);
  });
});

describe('compareDateRanges', () => {
  it('orders by start then by end', () => {
    expect(compareDateRanges(monthRange(2025, 3), monthRange(2025, 4))).toBeLessThan(0);
    expect(
      compareDateRanges(r('2025-03-01', '2025-03-31'), r('2025-03-01', '2025-03-15')),
    ).toBeGreaterThan(0);
    expect(compareDateRanges(monthRange(2025, 3), monthRange(2025, 3))).toBe(0);
  });
});
