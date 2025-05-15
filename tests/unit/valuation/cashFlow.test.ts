import { describe, expect, it } from 'vitest';
import { moneyFromMajor, moneyMajor } from '../../../src/domain/money/money.js';
import { buildCalendar } from '../../../src/domain/time/calendar.js';
import { formatSiteDate, parseSiteDate } from '../../../src/domain/time/siteDate.js';
import {
  buildCashFlow,
  describeCashPoint,
  firstDryPeriod,
  peakExposure,
  receiptsBetween,
  totalReceipts,
} from '../../../src/valuation/cashFlow.js';
import { buildSchedule } from '../../../src/valuation/period.js';

const gbp = (major: number) => moneyFromMajor(major, 'GBP');

const schedule = buildSchedule({
  firstYear: 2025,
  firstMonth: 1,
  count: 6,
  cutOffDay: 25,
  calendar: buildCalendar({ id: 'C1', name: 'main works' }),
});

const certified = [
  gbp(10_000),
  gbp(35_000),
  gbp(80_000),
  gbp(140_000),
  gbp(180_000),
  gbp(200_000),
];

const onTime = buildCashFlow({
  periods: schedule.periods,
  certified,
  currency: 'GBP',
});

const late = buildCashFlow({
  periods: schedule.periods,
  certified,
  currency: 'GBP',
  latePeriods: 1,
});

describe('buildCashFlow', () => {
  it('gives a point for every period', () => {
    expect(onTime).toHaveLength(6);
    expect(onTime[0]!.period).toBe(1);
    expect(formatSiteDate(onTime[0]!.cutOff)).toBe('2025-01-24');
  });

  it('takes the movement off the cumulative series', () => {
    expect(moneyMajor(onTime[0]!.certificate)).toBe(10_000);
    expect(moneyMajor(onTime[1]!.certificate)).toBe(25_000);
    expect(moneyMajor(onTime[3]!.certificate)).toBe(60_000);
  });

  it('owes nothing at any point when everything is paid on time', () => {
    expect(onTime.every((point) => point.outstanding.minor === 0)).toBe(true);
    expect(moneyMajor(onTime[5]!.received)).toBe(200_000);
  });

  it('carries a period of late payment through every figure', () => {
    expect(moneyMajor(late[0]!.received)).toBe(0);
    expect(moneyMajor(late[0]!.outstanding)).toBe(10_000);
    expect(moneyMajor(late[1]!.receipt)).toBe(10_000);
    expect(moneyMajor(late[3]!.outstanding)).toBe(60_000);
  });

  it('still certifies the same figures however late the payer is', () => {
    expect(late.map((p) => moneyMajor(p.certified))).toEqual(
      onTime.map((p) => moneyMajor(p.certified)),
    );
  });

  it('refuses a certified series that does not match the schedule', () => {
    expect(() =>
      buildCashFlow({
        periods: schedule.periods,
        certified: certified.slice(0, 3),
        currency: 'GBP',
      }),
    ).toThrow(/does not match the schedule/);
  });

  it('refuses half a period of late payment', () => {
    expect(() =>
      buildCashFlow({
        periods: schedule.periods,
        certified,
        currency: 'GBP',
        latePeriods: 1.5,
      }),
    ).toThrow(/whole number of periods/);
  });

  it('handles a series that goes backwards after a remeasure', () => {
    const dropped = buildCashFlow({
      periods: schedule.periods,
      certified: [gbp(10_000), gbp(35_000), gbp(30_000), gbp(60_000), gbp(70_000), gbp(80_000)],
      currency: 'GBP',
    });
    expect(moneyMajor(dropped[2]!.certificate)).toBe(-5000);
  });
});

describe('peakExposure', () => {
  it('finds the period the job is owed the most', () => {
    expect(peakExposure(late)!.period).toBe(4);
    expect(moneyMajor(peakExposure(late)!.outstanding)).toBe(60_000);
  });

  it('is null on an empty forecast', () => {
    expect(peakExposure([])).toBeNull();
  });

  it('is null when the job is never owed anything', () => {
    expect(peakExposure(onTime)).toBeNull();
  });
});

describe('totalReceipts', () => {
  it('adds up everything that comes in', () => {
    expect(moneyMajor(totalReceipts(onTime, 'GBP'))).toBe(200_000);
  });

  it('leaves out what the late payer has not paid yet', () => {
    expect(moneyMajor(totalReceipts(late, 'GBP'))).toBe(180_000);
  });
});

describe('receiptsBetween', () => {
  it('adds up the receipts falling due inside the window', () => {
    const from = parseSiteDate('2025-03-01');
    const to = parseSiteDate('2025-05-31');
    expect(moneyMajor(receiptsBetween(onTime, from, to, 'GBP'))).toBe(130_000);
  });

  it('is nothing when the window catches no due dates', () => {
    const from = parseSiteDate('2026-01-01');
    const to = parseSiteDate('2026-02-01');
    expect(receiptsBetween(onTime, from, to, 'GBP').minor).toBe(0);
  });
});

describe('firstDryPeriod', () => {
  it('names the first period after the first with nothing coming in', () => {
    const flat = buildCashFlow({
      periods: schedule.periods,
      certified: [gbp(10_000), gbp(35_000), gbp(35_000), gbp(60_000), gbp(70_000), gbp(80_000)],
      currency: 'GBP',
    });
    expect(firstDryPeriod(flat)).toBe(3);
  });

  it('is null when something comes in every period', () => {
    expect(firstDryPeriod(onTime)).toBeNull();
  });
});

describe('describeCashPoint', () => {
  it('says the period, the due date and what is owed', () => {
    expect(describeCashPoint(late[3]!)).toBe('period 4, due 2025-05-20, 60000 outstanding');
  });
});
