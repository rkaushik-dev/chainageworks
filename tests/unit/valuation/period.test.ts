import { describe, expect, it } from 'vitest';
import { NotFoundError } from '../../../src/errors.js';
import { buildCalendar } from '../../../src/domain/time/calendar.js';
import { formatDateRange } from '../../../src/domain/time/dateRange.js';
import { formatSiteDate, parseSiteDate } from '../../../src/domain/time/siteDate.js';
import {
  buildSchedule,
  describePeriod,
  findPeriodByNumber,
  periodAsAt,
  periodByNumber,
  periodCount,
  periodFor,
  previousPeriod,
  resolveCutOff,
} from '../../../src/valuation/period.js';

const d = parseSiteDate;
const weekdays = buildCalendar({ id: 'C1', name: 'main works' });
const easter = buildCalendar({
  id: 'C2',
  name: 'main works with holidays',
  holidays: [d('2025-04-18'), d('2025-04-21')],
});

const schedule = buildSchedule({
  firstYear: 2025,
  firstMonth: 1,
  count: 6,
  cutOffDay: 25,
  calendar: weekdays,
});

describe('resolveCutOff', () => {
  it('takes the day asked for when it is a working day', () => {
    expect(formatSiteDate(resolveCutOff(weekdays, 2025, 3, 25))).toBe('2025-03-25');
  });

  it('pulls back off a weekend', () => {
    expect(formatSiteDate(resolveCutOff(weekdays, 2025, 5, 25))).toBe('2025-05-23');
  });

  it('pulls back off a holiday as well', () => {
    expect(formatSiteDate(resolveCutOff(easter, 2025, 4, 21))).toBe('2025-04-17');
  });

  it('clamps a day past the end of a short month', () => {
    expect(formatSiteDate(resolveCutOff(weekdays, 2025, 2, 31))).toBe('2025-02-28');
  });
});

describe('buildSchedule', () => {
  it('numbers the periods from one', () => {
    expect(schedule.periods.map((p) => p.number)).toEqual([1, 2, 3, 4, 5, 6]);
    expect(periodCount(schedule)).toBe(6);
  });

  it('runs the first period from the start of the month', () => {
    expect(formatDateRange(periodByNumber(schedule, 1).range)).toBe('2025-01-01 to 2025-01-24');
  });

  it('starts each later period the day after the last cut off', () => {
    expect(formatDateRange(periodByNumber(schedule, 2).range)).toBe('2025-01-25 to 2025-02-25');
    expect(formatDateRange(periodByNumber(schedule, 3).range)).toBe('2025-02-26 to 2025-03-25');
  });

  it('leaves no gap and no overlap between periods', () => {
    for (let i = 1; i < schedule.periods.length; i += 1) {
      const before = schedule.periods[i - 1]!;
      const after = schedule.periods[i]!;
      expect(after.range.from - before.range.to).toBe(1);
    }
  });

  it('issues a week of working days after the cut off', () => {
    expect(formatSiteDate(periodByNumber(schedule, 3).issuedOn)).toBe('2025-04-03');
  });

  it('falls due a fortnight after issue', () => {
    expect(formatSiteDate(periodByNumber(schedule, 3).dueOn)).toBe('2025-04-17');
  });

  it('honours a different issue and payment lag', () => {
    const quick = buildSchedule({
      firstYear: 2025,
      firstMonth: 3,
      count: 1,
      cutOffDay: 25,
      issueDays: 2,
      paymentDays: 30,
      calendar: weekdays,
    });
    const only = periodByNumber(quick, 1);
    expect(formatSiteDate(only.issuedOn)).toBe('2025-03-27');
    expect(formatSiteDate(only.dueOn)).toBe('2025-04-26');
  });

  it('refuses a schedule with no periods', () => {
    expect(() =>
      buildSchedule({
        firstYear: 2025,
        firstMonth: 1,
        count: 0,
        cutOffDay: 25,
        calendar: weekdays,
      }),
    ).toThrow(/at least one period/);
  });

  it('refuses a cut off day that is not a day of the month', () => {
    for (const cutOffDay of [0, 32, 1.5]) {
      expect(() =>
        buildSchedule({
          firstYear: 2025,
          firstMonth: 1,
          count: 1,
          cutOffDay,
          calendar: weekdays,
        }),
      ).toThrow(/day of the month/);
    }
  });

  it('rolls into the next year', () => {
    const spanning = buildSchedule({
      firstYear: 2024,
      firstMonth: 11,
      count: 4,
      cutOffDay: 25,
      calendar: weekdays,
    });
    expect(formatSiteDate(periodByNumber(spanning, 4).cutOff)).toBe('2025-02-25');
  });
});

describe('finding a period', () => {
  it('finds by number', () => {
    expect(periodByNumber(schedule, 4).number).toBe(4);
    expect(findPeriodByNumber(schedule, 99)).toBeNull();
    expect(() => periodByNumber(schedule, 99)).toThrow(NotFoundError);
  });

  it('finds the period a date falls inside', () => {
    expect(periodFor(schedule, d('2025-03-10'))?.number).toBe(3);
    expect(periodFor(schedule, d('2025-03-25'))?.number).toBe(3);
    expect(periodFor(schedule, d('2025-03-26'))?.number).toBe(4);
    expect(periodFor(schedule, d('2024-12-31'))).toBeNull();
  });

  it('finds the last period whose cut off has passed', () => {
    expect(periodAsAt(schedule, d('2025-03-24'))?.number).toBe(2);
    expect(periodAsAt(schedule, d('2025-03-25'))?.number).toBe(3);
    expect(periodAsAt(schedule, d('2024-12-01'))).toBeNull();
    expect(periodAsAt(schedule, d('2026-01-01'))?.number).toBe(6);
  });

  it('walks back a period', () => {
    expect(previousPeriod(schedule, periodByNumber(schedule, 3))?.number).toBe(2);
    expect(previousPeriod(schedule, periodByNumber(schedule, 1))).toBeNull();
  });
});

describe('describePeriod', () => {
  it('reads like a certificate heading', () => {
    expect(describePeriod(periodByNumber(schedule, 3))).toBe(
      'valuation 3 to 2025-03-25, issued 2025-04-03, due 2025-04-17',
    );
  });
});
