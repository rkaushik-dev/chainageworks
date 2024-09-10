import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import {
  addDays,
  addMonths,
  compareSiteDates,
  dayOfWeek,
  diffDays,
  endOfMonth,
  formatSiteDate,
  isLastDayOfMonth,
  isWeekend,
  maxSiteDate,
  minSiteDate,
  parseSiteDate,
  siteDate,
  siteDateParts,
  siteDatesEqual,
  startOfMonth,
  tryParseSiteDate,
} from '../../../../src/domain/time/siteDate.js';

const d = parseSiteDate;

describe('siteDate', () => {
  it('counts days from the epoch', () => {
    expect(siteDate(1970, 1, 1)).toBe(0);
    expect(siteDate(1970, 1, 2)).toBe(1);
    expect(siteDate(1969, 12, 31)).toBe(-1);
  });

  it('splits back into parts', () => {
    expect(siteDateParts(siteDate(2025, 3, 14))).toEqual({ year: 2025, month: 3, day: 14 });
  });

  it('accepts the twenty ninth of February in a leap year', () => {
    expect(siteDateParts(siteDate(2024, 2, 29)).day).toBe(29);
  });

  it('refuses the twenty ninth of February in a common year', () => {
    expect(() => siteDate(2025, 2, 29)).toThrow(/not in that month/);
  });

  it('knows the century rule for leap years', () => {
    expect(() => siteDate(1900, 2, 29)).toThrow(/not in that month/);
    expect(siteDateParts(siteDate(2000, 2, 29)).day).toBe(29);
  });

  it('refuses a month outside one to twelve', () => {
    expect(() => siteDate(2025, 0, 1)).toThrow(/month must be 1 to 12/);
    expect(() => siteDate(2025, 13, 1)).toThrow(/month must be 1 to 12/);
  });

  it('refuses a day outside the month', () => {
    expect(() => siteDate(2025, 4, 31)).toThrow(/not in that month/);
    expect(() => siteDate(2025, 4, 0)).toThrow(/not in that month/);
  });

  it('refuses a year no contract could run in', () => {
    expect(() => siteDate(1899, 1, 1)).toThrow(/outside the range/);
    expect(() => siteDate(2201, 1, 1)).toThrow(/outside the range/);
    expect(() => siteDate(2025.5, 1, 1)).toThrow(/outside the range/);
  });
});

describe('parseSiteDate', () => {
  it('reads the iso form', () => {
    expect(siteDateParts(d('2025-03-14'))).toEqual({ year: 2025, month: 3, day: 14 });
  });

  it('ignores surrounding whitespace', () => {
    expect(d('  2025-03-14 ')).toBe(d('2025-03-14'));
  });

  it('refuses anything else', () => {
    for (const text of ['14/03/2025', '2025-3-14', '2025-03', 'yesterday', '']) {
      expect(tryParseSiteDate(text).ok).toBe(false);
    }
    expect(() => parseSiteDate('14/03/2025')).toThrow(BadInputError);
  });

  it('refuses a well shaped date that does not exist', () => {
    const r = tryParseSiteDate('2025-02-30');
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.error.message).toMatch(/not in that month/);
  });
});

describe('formatSiteDate', () => {
  it('pads month and day', () => {
    expect(formatSiteDate(siteDate(2025, 3, 4))).toBe('2025-03-04');
  });

  it('round trips through the parser', () => {
    for (const text of ['1970-01-01', '2024-02-29', '2025-12-31', '2199-06-15']) {
      expect(formatSiteDate(d(text))).toBe(text);
    }
  });
});

describe('day arithmetic', () => {
  it('steps forwards and backwards', () => {
    expect(formatSiteDate(addDays(d('2025-03-14'), 1))).toBe('2025-03-15');
    expect(formatSiteDate(addDays(d('2025-03-01'), -1))).toBe('2025-02-28');
  });

  it('crosses a leap day', () => {
    expect(formatSiteDate(addDays(d('2024-02-28'), 1))).toBe('2024-02-29');
    expect(formatSiteDate(addDays(d('2024-02-28'), 2))).toBe('2024-03-01');
  });

  it('refuses a fractional step', () => {
    expect(() => addDays(d('2025-03-14'), 0.5)).toThrow(/whole number of days/);
  });

  it('measures a signed difference', () => {
    expect(diffDays(d('2025-03-01'), d('2025-03-14'))).toBe(13);
    expect(diffDays(d('2025-03-14'), d('2025-03-01'))).toBe(-13);
    expect(diffDays(d('2025-03-14'), d('2025-03-14'))).toBe(0);
  });

  it('compares and picks extremes', () => {
    const early = d('2025-03-01');
    const late = d('2025-03-14');
    expect(compareSiteDates(early, late)).toBeLessThan(0);
    expect(siteDatesEqual(early, early)).toBe(true);
    expect(siteDatesEqual(early, late)).toBe(false);
    expect(minSiteDate(early, late)).toBe(early);
    expect(maxSiteDate(early, late)).toBe(late);
  });
});

describe('dayOfWeek', () => {
  it('numbers monday one through sunday seven', () => {
    expect(dayOfWeek(d('2025-03-10'))).toBe(1);
    expect(dayOfWeek(d('2025-03-14'))).toBe(5);
    expect(dayOfWeek(d('2025-03-15'))).toBe(6);
    expect(dayOfWeek(d('2025-03-16'))).toBe(7);
  });

  it('calls saturday and sunday the weekend', () => {
    expect(isWeekend(d('2025-03-14'))).toBe(false);
    expect(isWeekend(d('2025-03-15'))).toBe(true);
    expect(isWeekend(d('2025-03-16'))).toBe(true);
    expect(isWeekend(d('2025-03-17'))).toBe(false);
  });
});

describe('month boundaries', () => {
  it('finds the first and last day', () => {
    expect(formatSiteDate(startOfMonth(d('2025-03-14')))).toBe('2025-03-01');
    expect(formatSiteDate(endOfMonth(d('2025-03-14')))).toBe('2025-03-31');
    expect(formatSiteDate(endOfMonth(d('2025-02-14')))).toBe('2025-02-28');
    expect(formatSiteDate(endOfMonth(d('2024-02-14')))).toBe('2024-02-29');
  });

  it('reports the last day of the month', () => {
    expect(isLastDayOfMonth(d('2025-03-31'))).toBe(true);
    expect(isLastDayOfMonth(d('2025-03-30'))).toBe(false);
    expect(isLastDayOfMonth(d('2024-02-29'))).toBe(true);
  });
});

describe('addMonths', () => {
  it('steps whole months', () => {
    expect(formatSiteDate(addMonths(d('2025-03-14'), 1))).toBe('2025-04-14');
    expect(formatSiteDate(addMonths(d('2025-03-14'), -3))).toBe('2024-12-14');
  });

  it('clamps onto the last day when the target month is short', () => {
    expect(formatSiteDate(addMonths(d('2025-01-31'), 1))).toBe('2025-02-28');
    expect(formatSiteDate(addMonths(d('2024-01-31'), 1))).toBe('2024-02-29');
    expect(formatSiteDate(addMonths(d('2025-03-31'), -1))).toBe('2025-02-28');
  });

  it('crosses a year boundary in both directions', () => {
    expect(formatSiteDate(addMonths(d('2025-11-15'), 3))).toBe('2026-02-15');
    expect(formatSiteDate(addMonths(d('2025-02-15'), -3))).toBe('2024-11-15');
  });

  it('refuses a fractional step', () => {
    expect(() => addMonths(d('2025-03-14'), 1.5)).toThrow(/whole number of months/);
  });
});
