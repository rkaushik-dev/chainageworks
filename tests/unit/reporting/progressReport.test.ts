import { describe, expect, it } from 'vitest';
import { formatItemCode } from '../../../src/domain/bill/itemCode.js';
import { formatChainageRange } from '../../../src/domain/chainage/range.js';
import { moneyMajor } from '../../../src/domain/money/money.js';
import { percentValue } from '../../../src/domain/money/percent.js';
import { buildCalendar } from '../../../src/domain/time/calendar.js';
import { formatSiteDate } from '../../../src/domain/time/siteDate.js';
import {
  buildProgressReport,
  hasProblems,
  heldLines,
  outstandingHoldPoints,
} from '../../../src/reporting/progressReport.js';
import { buildSchedule, periodByNumber } from '../../../src/valuation/period.js';
import { d, sampleContext } from '../../support/contextFixtures.js';

const context = sampleContext();
const schedule = buildSchedule({
  firstYear: 2025,
  firstMonth: 1,
  count: 6,
  cutOffDay: 25,
  calendar: buildCalendar({ id: 'C1', name: 'main works' }),
});
const march = periodByNumber(schedule, 3);
const report = buildProgressReport(context, march);

describe('buildProgressReport', () => {
  it('names the period and the bill revision it read', () => {
    expect(report.period.number).toBe(3);
    expect(report.billRevision).toBe('Rev A');
  });

  it('gives one line per package, in register order', () => {
    expect(report.lines.map((l) => l.packageId)).toEqual(['WP-01', 'WP-03', 'WP-02']);
  });

  it('carries the package name, section and extent', () => {
    const line = report.lines[0]!;
    expect(line.name).toMatch(/earthworks/);
    expect(line.section).toBe('section 1');
    expect(formatChainageRange(line.extent)).toBe('12+000.000 to 12+800.000');
  });

  it('totals the budget and what has been earned', () => {
    expect(moneyMajor(report.budget)).toBe(49_700 + 120_600 + 56_650);
    expect(moneyMajor(report.earned)).toBe(16_152.5);
  });

  it('reports the earned fraction on each line', () => {
    expect(percentValue(report.lines[0]!.fraction)).toBe(32.5);
    expect(percentValue(report.lines[1]!.fraction)).toBe(0);
  });
});

describe('the value being held back', () => {
  it('prices the work that is built and cannot be paid for', () => {
    const line = report.lines[0]!;
    expect(moneyMajor(line.earnedIfReleased)).toBeGreaterThan(moneyMajor(line.earned));
    expect(line.heldValue.minor).toBeGreaterThan(0);
  });

  it('adds the held value across the job', () => {
    expect(report.heldValue.minor).toBe(report.lines[0]!.heldValue.minor);
  });

  it('lists the packages holding value, worst first', () => {
    expect(heldLines(report).map((l) => l.packageId)).toEqual(['WP-01']);
  });

  it('falls to nothing once everything is released', () => {
    const released = sampleContext({ signOffs: [], ncrs: [], records: [] });
    const clean = buildProgressReport(released, march);
    expect(clean.heldValue.minor).toBe(0);
  });
});

describe('the step lines', () => {
  const steps = report.lines[0]!.steps;

  it('gives one per step in the rule of credit', () => {
    expect(steps.map((s) => s.stepId)).toEqual(['strip', 'excavate', 'place', 'trim']);
  });

  it('shows built against credited', () => {
    const excavate = steps[1]!;
    expect(excavate.builtFraction).toBe(0.75);
    expect(excavate.creditedFraction).toBe(0.5);
    expect(excavate.gate).toBe('part_open');
  });

  it('names the chainage that is held back', () => {
    expect(steps[1]!.heldBack.map(formatChainageRange)).toEqual(['12+400.000 to 12+600.000']);
  });

  it('reports the hold points nobody has signed', () => {
    const nothingSigned = buildProgressReport(sampleContext({ signOffs: [] }), march);
    expect(outstandingHoldPoints(nothingSigned)).toEqual(['IP-E1', 'IP-E2', 'IP-P1']);
  });

  it('still names the points on packages nobody has started', () => {
    expect(outstandingHoldPoints(report)).toEqual(['IP-E1', 'IP-E2', 'IP-P1']);
  });
});

describe('the calendar figures', () => {
  it('counts the working days in the period, which runs cut off to cut off', () => {
    expect(report.workingDays).toBe(20);
  });

  it('counts the productive days the same when nothing was stood down', () => {
    expect(report.productiveDays).toBe(20);
    expect(report.standdowns).toEqual([]);
  });

  it('takes standdowns off the productive count', () => {
    const wet = sampleContext();
    const withWeather = buildProgressReport(
      {
        ...wet,
        calendar: buildCalendar({
          id: 'C2',
          name: 'wet march',
          standdowns: [
            { date: d('2025-03-05'), reason: 'weather' },
            { date: d('2025-03-06'), reason: 'weather' },
          ],
        }),
      },
      march,
    );
    expect(withWeather.workingDays).toBe(20);
    expect(withWeather.productiveDays).toBe(18);
    expect(withWeather.standdowns.map((s) => formatSiteDate(s.date))).toEqual([
      '2025-03-05',
      '2025-03-06',
    ]);
  });
});

describe('the problems section', () => {
  it('carries the reconciliation against the bill', () => {
    expect(report.reconciliation.map((r) => formatItemCode(r.code))).toEqual([
      'A213.1',
      'E423.1',
      'F624.1',
    ]);
    expect(hasProblems(report)).toBe(true);
  });

  it('carries nothing for over measure when the survey is inside the allowance', () => {
    expect(report.overMeasures).toEqual([]);
  });
});
