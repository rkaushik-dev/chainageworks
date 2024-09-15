import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import {
  addWorkingDays,
  buildCalendar,
  DEFAULT_WORKING_DAYS,
  isHoliday,
  isProductiveDay,
  isWorkingDay,
  nextWorkingDay,
  previousWorkingDay,
  productiveDaysIn,
  standdownDaysIn,
  standdownOn,
  standdownsIn,
  workingDaysBetween,
  workingDaysIn,
} from '../../../../src/domain/time/calendar.js';
import { dateRange, monthRange } from '../../../../src/domain/time/dateRange.js';
import {
  addDays,
  formatSiteDate,
  parseSiteDate,
} from '../../../../src/domain/time/siteDate.js';

const d = parseSiteDate;
const r = (from: string, to: string) => dateRange(d(from), d(to));

const weekdays = buildCalendar({ id: 'C1', name: 'main works' });

const withHolidays = buildCalendar({
  id: 'C2',
  name: 'main works with holidays',
  holidays: [d('2025-04-18'), d('2025-04-21')],
});

const sixDay = buildCalendar({
  id: 'C3',
  name: 'six day working',
  workingDays: [1, 2, 3, 4, 5, 6],
});

const withStanddowns = buildCalendar({
  id: 'C4',
  name: 'wet march',
  standdowns: [
    { date: d('2025-03-05'), reason: 'weather', note: 'rain, no concrete' },
    { date: d('2025-03-06'), reason: 'ground_conditions' },
    { date: d('2025-03-16'), reason: 'weather' },
  ],
});

describe('buildCalendar', () => {
  it('defaults to monday through friday', () => {
    expect(weekdays.workingDays).toEqual([...DEFAULT_WORKING_DAYS]);
  });

  it('sorts and dedupes the working days', () => {
    const cal = buildCalendar({ id: 'x', name: 'x', workingDays: [5, 1, 1, 3] });
    expect(cal.workingDays).toEqual([1, 3, 5]);
  });

  it('refuses a weekday outside one to seven', () => {
    expect(() => buildCalendar({ id: 'x', name: 'x', workingDays: [0] })).toThrow(
      /iso weekday/,
    );
    expect(() => buildCalendar({ id: 'x', name: 'x', workingDays: [8] })).toThrow(
      /iso weekday/,
    );
    expect(() => buildCalendar({ id: 'x', name: 'x', workingDays: [1.5] })).toThrow(
      /iso weekday/,
    );
  });

  it('refuses a calendar that never works', () => {
    expect(() => buildCalendar({ id: 'x', name: 'x', workingDays: [] })).toThrow(
      /at least one day a week/,
    );
  });

  it('sorts and dedupes holidays', () => {
    const cal = buildCalendar({
      id: 'x',
      name: 'x',
      holidays: [d('2025-04-21'), d('2025-04-18'), d('2025-04-21')],
    });
    expect(cal.holidays.map(formatSiteDate)).toEqual(['2025-04-18', '2025-04-21']);
  });

  it('keeps one standdown per day, the last one entered', () => {
    const cal = buildCalendar({
      id: 'x',
      name: 'x',
      standdowns: [
        { date: d('2025-03-05'), reason: 'weather' },
        { date: d('2025-03-05'), reason: 'access' },
      ],
    });
    expect(cal.standdowns).toHaveLength(1);
    expect(cal.standdowns[0]?.reason).toBe('access');
  });
});

describe('isWorkingDay', () => {
  it('works weekdays and not weekends', () => {
    expect(isWorkingDay(weekdays, d('2025-03-14'))).toBe(true);
    expect(isWorkingDay(weekdays, d('2025-03-15'))).toBe(false);
    expect(isWorkingDay(weekdays, d('2025-03-16'))).toBe(false);
  });

  it('works saturday on a six day calendar', () => {
    expect(isWorkingDay(sixDay, d('2025-03-15'))).toBe(true);
    expect(isWorkingDay(sixDay, d('2025-03-16'))).toBe(false);
  });

  it('does not work a holiday that falls on a weekday', () => {
    expect(isHoliday(withHolidays, d('2025-04-18'))).toBe(true);
    expect(isWorkingDay(withHolidays, d('2025-04-18'))).toBe(false);
    expect(isWorkingDay(withHolidays, d('2025-04-17'))).toBe(true);
  });
});

describe('standdowns', () => {
  it('finds the standdown on a day', () => {
    expect(standdownOn(withStanddowns, d('2025-03-05'))?.reason).toBe('weather');
    expect(standdownOn(withStanddowns, d('2025-03-07'))).toBeNull();
  });

  it('makes a working day unproductive without making it a non working day', () => {
    expect(isWorkingDay(withStanddowns, d('2025-03-05'))).toBe(true);
    expect(isProductiveDay(withStanddowns, d('2025-03-05'))).toBe(false);
    expect(isProductiveDay(withStanddowns, d('2025-03-07'))).toBe(true);
  });

  it('ignores a standdown booked on a day nobody was working', () => {
    expect(
      standdownsIn(withStanddowns, monthRange(2025, 3)).map((s) => formatSiteDate(s.date)),
    ).toEqual(['2025-03-05', '2025-03-06']);
    expect(standdownDaysIn(withStanddowns, monthRange(2025, 3))).toBe(2);
  });

  it('counts nothing outside the range asked about', () => {
    expect(standdownDaysIn(withStanddowns, monthRange(2025, 4))).toBe(0);
  });
});

describe('counting days in a range', () => {
  it('counts working days in a month', () => {
    expect(workingDaysIn(weekdays, monthRange(2025, 3))).toBe(21);
    expect(workingDaysIn(sixDay, monthRange(2025, 3))).toBe(26);
  });

  it('takes holidays off the count', () => {
    expect(workingDaysIn(weekdays, monthRange(2025, 4))).toBe(22);
    expect(workingDaysIn(withHolidays, monthRange(2025, 4))).toBe(20);
  });

  it('takes standdowns off the productive count but not the working count', () => {
    expect(workingDaysIn(withStanddowns, monthRange(2025, 3))).toBe(21);
    expect(productiveDaysIn(withStanddowns, monthRange(2025, 3))).toBe(19);
  });

  it('counts a single day range', () => {
    expect(workingDaysIn(weekdays, r('2025-03-14', '2025-03-14'))).toBe(1);
    expect(workingDaysIn(weekdays, r('2025-03-15', '2025-03-15'))).toBe(0);
  });
});

describe('stepping working days', () => {
  it('finds the next and previous working day', () => {
    expect(formatSiteDate(nextWorkingDay(weekdays, d('2025-03-14')))).toBe('2025-03-17');
    expect(formatSiteDate(previousWorkingDay(weekdays, d('2025-03-17')))).toBe('2025-03-14');
  });

  it('steps over a holiday', () => {
    expect(formatSiteDate(nextWorkingDay(withHolidays, d('2025-04-17')))).toBe('2025-04-22');
  });

  it('steps over a weekend on a six day calendar by one day only', () => {
    expect(formatSiteDate(nextWorkingDay(sixDay, d('2025-03-14')))).toBe('2025-03-15');
  });

  it('does not step over a standdown, because the day is still a working day', () => {
    expect(formatSiteDate(nextWorkingDay(withStanddowns, d('2025-03-04')))).toBe('2025-03-05');
  });

  it('adds and subtracts whole working days', () => {
    expect(formatSiteDate(addWorkingDays(weekdays, d('2025-03-14'), 1))).toBe('2025-03-17');
    expect(formatSiteDate(addWorkingDays(weekdays, d('2025-03-14'), 5))).toBe('2025-03-21');
    expect(formatSiteDate(addWorkingDays(weekdays, d('2025-03-17'), -1))).toBe('2025-03-14');
  });

  it('returns the day itself for a step of nothing', () => {
    expect(addWorkingDays(weekdays, d('2025-03-15'), 0)).toBe(d('2025-03-15'));
  });

  it('refuses a fractional step', () => {
    expect(() => addWorkingDays(weekdays, d('2025-03-14'), 1.5)).toThrow(BadInputError);
  });

  it('gives up rather than looping forever on an impossible calendar', () => {
    const never = buildCalendar({
      id: 'never',
      name: 'shut',
      workingDays: [1],
      holidays: Array.from({ length: 60 }, (_, i) => addDays(d('2025-01-06'), i * 7)),
    });
    expect(() => nextWorkingDay(never, d('2025-01-01'))).toThrow(/within a year/);
    expect(() => previousWorkingDay(never, d('2026-03-01'))).toThrow(/within a year/);
  });
});

describe('workingDaysBetween', () => {
  it('counts neither end', () => {
    expect(workingDaysBetween(weekdays, d('2025-03-14'), d('2025-03-17'))).toBe(0);
    expect(workingDaysBetween(weekdays, d('2025-03-14'), d('2025-03-21'))).toBe(4);
  });

  it('is zero for the same day', () => {
    expect(workingDaysBetween(weekdays, d('2025-03-14'), d('2025-03-14'))).toBe(0);
  });

  it('is negative the other way round', () => {
    expect(workingDaysBetween(weekdays, d('2025-03-21'), d('2025-03-14'))).toBe(-4);
  });
});
