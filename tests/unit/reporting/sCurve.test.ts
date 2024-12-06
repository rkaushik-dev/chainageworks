import { describe, expect, it } from 'vitest';
import { moneyFromMajor, moneyMajor } from '../../../src/domain/money/money.js';
import { percentValue } from '../../../src/domain/money/percent.js';
import { buildCalendar } from '../../../src/domain/time/calendar.js';
import { formatSiteDate } from '../../../src/domain/time/siteDate.js';
import {
  buildSCurve,
  closingVariance,
  firstBehindPeriod,
  isBehind,
  linearBaseline,
  regressions,
  sCurveBaseline,
} from '../../../src/reporting/sCurve.js';
import { buildSchedule } from '../../../src/valuation/period.js';
import { sampleContext } from '../../support/contextFixtures.js';

const gbp = (major: number) => moneyFromMajor(major, 'GBP');
const context = sampleContext();
const schedule = buildSchedule({
  firstYear: 2025,
  firstMonth: 1,
  count: 6,
  cutOffDay: 25,
  calendar: buildCalendar({ id: 'C1', name: 'main works' }),
});

describe('linearBaseline', () => {
  it('spreads the total evenly and ends on it', () => {
    const baseline = linearBaseline(gbp(1200), 4);
    expect(baseline.cumulative.map(moneyMajor)).toEqual([300, 600, 900, 1200]);
  });

  it('never loses a penny to rounding', () => {
    const baseline = linearBaseline(gbp(1000.01), 3);
    expect(baseline.cumulative[2]?.minor).toBe(100_001);
  });

  it('refuses a baseline with no periods', () => {
    expect(() => linearBaseline(gbp(100), 0)).toThrow(/at least one period/);
    expect(() => linearBaseline(gbp(100), 1.5)).toThrow(/at least one period/);
  });
});

describe('sCurveBaseline', () => {
  const baseline = sCurveBaseline(gbp(1_000_000), 6);

  it('ends on the total', () => {
    expect(moneyMajor(baseline.cumulative[5]!)).toBe(1_000_000);
  });

  it('rises through the middle faster than at either end', () => {
    const shares = baseline.cumulative.map((c, i) =>
      i === 0 ? c.minor : c.minor - baseline.cumulative[i - 1]!.minor,
    );
    expect(shares[0]).toBeLessThan(shares[2]!);
    expect(shares[5]).toBeLessThan(shares[3]!);
  });

  it('never goes backwards', () => {
    for (let i = 1; i < baseline.cumulative.length; i += 1) {
      expect(baseline.cumulative[i]!.minor).toBeGreaterThanOrEqual(
        baseline.cumulative[i - 1]!.minor,
      );
    }
  });

  it('handles a single period', () => {
    expect(moneyMajor(sCurveBaseline(gbp(500), 1).cumulative[0]!)).toBe(500);
  });

  it('refuses a baseline with no periods', () => {
    expect(() => sCurveBaseline(gbp(100), 0)).toThrow(/at least one period/);
  });
});

describe('buildSCurve', () => {
  const baseline = linearBaseline(gbp(120_000), 6);
  const points = buildSCurve(context, schedule, baseline);

  it('gives one point per period', () => {
    expect(points.map((p) => p.period)).toEqual([1, 2, 3, 4, 5, 6]);
    expect(formatSiteDate(points[2]!.cutOff)).toBe('2025-03-25');
  });

  it('reads the planned figure off the baseline', () => {
    expect(points.map((p) => moneyMajor(p.planned))).toEqual([
      20_000, 40_000, 60_000, 80_000, 100_000, 120_000,
    ]);
  });

  it('earns nothing before the work started and something once it did', () => {
    expect(points[1]?.earned.minor).toBe(0);
    expect(moneyMajor(points[2]!.earned)).toBe(16_152.5);
  });

  it('reports the variance as earned less planned', () => {
    expect(moneyMajor(points[2]!.variance)).toBe(16_152.5 - 60_000);
    expect(isBehind(points[2]!)).toBe(true);
  });

  it('reports each figure as a share of the total', () => {
    expect(percentValue(points[2]!.plannedShare)).toBe(50);
    expect(percentValue(points[2]!.earnedShare)).toBeCloseTo(13.46, 1);
  });

  it('names the first period the job fell behind', () => {
    expect(firstBehindPeriod(points)).toBe(1);
  });

  it('reports the closing variance', () => {
    expect(moneyMajor(closingVariance(points)!)).toBe(16_152.5 - 120_000);
    expect(closingVariance([])).toBeNull();
  });

  it('finds no regression when the earned figure only ever rises', () => {
    expect(regressions(points)).toEqual([]);
  });

  it('refuses an empty baseline', () => {
    expect(() => buildSCurve(context, schedule, { cumulative: [] })).toThrow(
      /at least one period/,
    );
  });

  it('holds the last planned figure when the schedule runs past the baseline', () => {
    const short = linearBaseline(gbp(120_000), 2);
    const long = buildSCurve(context, schedule, short);
    expect(moneyMajor(long[5]!.planned)).toBe(120_000);
  });
});
