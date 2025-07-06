import { describe, expect, it } from 'vitest';
import { formatChainageRange } from '../../../src/domain/chainage/range.js';
import { buildCalendar } from '../../../src/domain/time/calendar.js';
import { formatSiteDate } from '../../../src/domain/time/siteDate.js';
import {
  buildLookahead,
  describeLookahead,
  finishBy,
  fitsInWindow,
  isInWindow,
  readyNow,
  unpriced,
  waitingOnInspection,
} from '../../../src/progress/lookahead.js';
import { buildAllowances } from '../../../src/progress/productivity.js';
import { d, sampleContext } from '../../support/contextFixtures.js';
import { samplePackages } from '../../support/packageFixtures.js';

const context = sampleContext();
const calendar = buildCalendar({ id: 'C1', name: 'main works' });
const packages = samplePackages();

const allowances = buildAllowances([
  { stepId: 'strip', metresPerHour: 10 },
  { stepId: 'excavate', metresPerHour: 12 },
  { stepId: 'place', metresPerHour: 8 },
]);

const lookahead = buildLookahead({
  context,
  packages,
  calendar,
  allowances,
  from: d('2025-03-26'),
});

const step = (packageId: string, stepId: string) =>
  lookahead.steps.find((s) => s.packageId === packageId && s.stepId === stepId);

describe('buildLookahead', () => {
  it('runs four weeks from the date by default', () => {
    expect(formatSiteDate(lookahead.from)).toBe('2025-03-26');
    expect(formatSiteDate(lookahead.to)).toBe('2025-04-22');
    expect(lookahead.workingDays).toBe(20);
  });

  it('takes the window from the weeks asked for', () => {
    const short = buildLookahead({
      context,
      packages,
      calendar,
      allowances,
      from: d('2025-03-26'),
      weeks: 1,
    });
    expect(formatSiteDate(short.to)).toBe('2025-04-01');
    expect(short.workingDays).toBe(5);
  });

  it('refuses a lookahead shorter than a week', () => {
    expect(() =>
      buildLookahead({
        context,
        packages,
        calendar,
        allowances,
        from: d('2025-03-26'),
        weeks: 0,
      }),
    ).toThrow(/at least one week/);
  });

  it('refuses a lookahead with no gang on it', () => {
    expect(() =>
      buildLookahead({
        context,
        packages,
        calendar,
        allowances,
        from: d('2025-03-26'),
        gangs: 0,
      }),
    ).toThrow(/at least one gang/);
  });

  it('leaves out a step with nothing left to build', () => {
    expect(step('WP-01', 'strip')).toBeUndefined();
  });

  it('measures what is left rather than what is credited', () => {
    const excavate = step('WP-01', 'excavate')!;
    expect(excavate.remainingMetres).toBe(200);
    expect(excavate.remaining.map(formatChainageRange)).toEqual(['12+600.000 to 12+800.000']);
  });

  it('prices the time at the allowance and an eight hour shift', () => {
    expect(step('WP-01', 'excavate')!.days).toBeCloseTo(200 / 96, 6);
    expect(step('WP-01', 'place')!.days).toBeCloseTo(400 / 64, 6);
  });

  it('halves the days when a second gang is put on', () => {
    const pushed = buildLookahead({
      context,
      packages,
      calendar,
      allowances,
      from: d('2025-03-26'),
      gangs: 2,
    });
    const one = step('WP-01', 'excavate')!.days!;
    const two = pushed.steps.find((s) => s.packageId === 'WP-01' && s.stepId === 'excavate')!;
    expect(two.days).toBeCloseTo(one / 2, 9);
  });

  it('leaves the days null on a step with no allowance', () => {
    expect(step('WP-01', 'trim')!.days).toBeNull();
  });

  it('adds the days across every package', () => {
    const total = lookahead.steps.reduce((sum, s) => sum + (s.days ?? 0), 0);
    expect(lookahead.daysOfWork).toBeCloseTo(total, 9);
    expect(lookahead.daysOfWork).toBeGreaterThan(0);
  });
});

describe('readyNow and waitingOnInspection', () => {
  it('names the steps nothing is in front of', () => {
    const ready = readyNow(lookahead);
    expect(ready.length).toBeGreaterThan(0);
    expect(ready.every((s) => s.waitingOn.length === 0)).toBe(true);
  });

  it('names the step an inspector is holding rather than a gang', () => {
    const held = waitingOnInspection(lookahead);
    expect(held.map((s) => `${s.packageId} ${s.stepId}`)).toContain('WP-02 excavate');
    expect(held.every((s) => s.heldPoints.length > 0)).toBe(true);
  });

  it('never puts a step on both lists', () => {
    const ready = new Set(readyNow(lookahead).map((s) => `${s.packageId}/${s.stepId}`));
    for (const held of waitingOnInspection(lookahead)) {
      expect(ready.has(`${held.packageId}/${held.stepId}`)).toBe(false);
    }
  });
});

describe('fitsInWindow', () => {
  it('says so when the work in front is more than the window has room for', () => {
    const week = buildLookahead({
      context,
      packages,
      calendar,
      allowances,
      from: d('2025-03-26'),
      weeks: 1,
    });
    expect(fitsInWindow(week)).toBe(week.daysOfWork <= week.workingDays);
  });

  it('fits a window with a big enough push behind it', () => {
    const pushed = buildLookahead({
      context,
      packages,
      calendar,
      allowances,
      from: d('2025-03-26'),
      weeks: 12,
      gangs: 4,
    });
    expect(fitsInWindow(pushed)).toBe(true);
  });
});

describe('finishBy', () => {
  it('counts the working days out from the start of the window', () => {
    const finish = finishBy(lookahead, calendar);
    expect(finish).toBeGreaterThan(lookahead.from);
    expect(formatSiteDate(finish)).toMatch(/^2025-/);
  });
});

describe('unpriced', () => {
  it('names the steps with work left and no allowance', () => {
    expect(unpriced(lookahead).every((s) => s.days === null)).toBe(true);
    expect(unpriced(lookahead).length).toBeGreaterThan(0);
  });
});

describe('isInWindow', () => {
  it('takes the first and the last day of the window', () => {
    expect(isInWindow(lookahead, d('2025-03-26'))).toBe(true);
    expect(isInWindow(lookahead, d('2025-04-22'))).toBe(true);
    expect(isInWindow(lookahead, d('2025-03-25'))).toBe(false);
    expect(isInWindow(lookahead, d('2025-04-23'))).toBe(false);
  });
});

describe('describeLookahead', () => {
  it('says the window, the work and whether it fits', () => {
    expect(describeLookahead(lookahead)).toMatch(
      /^2025-03-26 to 2025-04-22, [\d.]+ days of work in 20 working days, /,
    );
  });
});
