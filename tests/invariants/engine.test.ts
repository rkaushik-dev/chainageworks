import { describe, expect, it } from 'vitest';
import { coveredLength, intersectRangeLists } from '../../src/domain/chainage/range.js';
import { percentValue } from '../../src/domain/money/percent.js';
import { earnAll, earnPackage } from '../../src/progress/earn.js';
import { packageStepProgress } from '../../src/progress/stepProgress.js';
import { buildCertificate } from '../../src/valuation/certificate.js';
import { buildSchedule } from '../../src/valuation/period.js';
import { DEFAULT_RETENTION } from '../../src/valuation/retention.js';
import { buildCalendar } from '../../src/domain/time/calendar.js';
import { moneyFromMajor, zeroMoney } from '../../src/domain/money/money.js';
import { addDays } from '../../src/domain/time/siteDate.js';
import { d, range, sampleContext, sampleRecords, signOff } from '../support/contextFixtures.js';
import { rng } from '../support/random.js';

const context = sampleContext();
const contractSum = moneyFromMajor(312_190, 'GBP');
const schedule = buildSchedule({
  firstYear: 2025,
  firstMonth: 1,
  count: 12,
  cutOffDay: 25,
  calendar: buildCalendar({ id: 'C1', name: 'main works' }),
});

const cutOffs = Array.from({ length: 400 }, (_, i) => addDays(d('2025-01-01'), i));

describe('what is credited is a subset of what is built', () => {
  it('holds at every cut off across the job', () => {
    for (const pkg of context.register.packages) {
      for (const asAt of cutOffs) {
        for (const step of packageStepProgress(context, pkg, asAt)) {
          const outside = intersectRangeLists([...step.creditedExtent], [...step.builtExtent]);
          expect(coveredLength(outside)).toBe(coveredLength([...step.creditedExtent]));
          expect(step.fraction).toBeLessThanOrEqual(step.builtFraction + 1e-9);
        }
      }
    }
  });
});

describe('a package never earns more than its budget', () => {
  it('holds at every cut off', () => {
    for (const pkg of context.register.packages) {
      for (const asAt of cutOffs) {
        const earning = earnPackage(context, pkg, asAt);
        expect(earning.earnedValue.minor).toBeLessThanOrEqual(earning.budget.minor);
        expect(earning.earnedValue.minor).toBeGreaterThanOrEqual(0);
        expect(percentValue(earning.fraction)).toBeGreaterThanOrEqual(0);
        expect(percentValue(earning.fraction)).toBeLessThanOrEqual(100);
      }
    }
  });
});

describe('nothing is earned that was not measured', () => {
  it('holds for every measurable allocation at every cut off', () => {
    for (const pkg of context.register.packages) {
      for (const asAt of cutOffs) {
        for (const allocation of earnPackage(context, pkg, asAt).allocations) {
          if (allocation.notEarnable) {
            expect(allocation.earnedValue.minor).toBe(0);
            continue;
          }
          expect(allocation.earnedQuantity.milli).toBeLessThanOrEqual(
            allocation.measured.milli,
          );
        }
      }
    }
  });
});

describe('the earned figure only ever rises as the cut off moves out', () => {
  it('holds across the whole job, because nothing in the sample is ever undone', () => {
    let previous = -1;
    for (const asAt of cutOffs) {
      const total = earnAll(context, asAt).reduce((sum, e) => sum + e.earnedValue.minor, 0);
      expect(total).toBeGreaterThanOrEqual(previous);
      previous = total;
    }
  });

  it('falls only when a report is raised, which is the one thing that undoes credit', () => {
    const noReports = sampleContext({ ncrs: [] });
    let previous = -1;
    for (const asAt of cutOffs) {
      const total = earnAll(noReports, asAt).reduce((sum, e) => sum + e.earnedValue.minor, 0);
      expect(total).toBeGreaterThanOrEqual(previous);
      previous = total;
    }
  });
});

describe('the certificate always adds up', () => {
  it('gross less retention is the net, and the movement is the amount due', () => {
    let previous = zeroMoney('GBP');
    for (const period of schedule.periods) {
      const certificate = buildCertificate({
        context,
        period,
        contractSum,
        retention: DEFAULT_RETENTION,
        previouslyCertified: previous,
      });
      expect(certificate.gross.minor).toBe(
        certificate.measuredWork.minor + certificate.materialsOnSite.minor,
      );
      expect(certificate.netValuation.minor).toBe(
        certificate.gross.minor - certificate.retention.held.minor,
      );
      expect(certificate.amountDue.minor).toBe(certificate.netValuation.minor - previous.minor);
      previous = certificate.netValuation;
    }
  });
});

describe('the answer does not depend on the order the records arrived in', () => {
  it('holds for a hundred shuffles of the survey book', () => {
    const random = rng(4_912_337);
    const asAt = d('2025-03-31');
    const expected = earnAll(context, asAt).map((e) => e.earnedValue.minor);
    for (let run = 0; run < 100; run += 1) {
      const shuffled = [...sampleRecords()];
      for (let i = shuffled.length - 1; i > 0; i -= 1) {
        const j = random.int(0, i);
        [shuffled[i], shuffled[j]] = [shuffled[j]!, shuffled[i]!];
      }
      const reordered = sampleContext({ records: shuffled });
      expect(earnAll(reordered, asAt).map((e) => e.earnedValue.minor)).toEqual(expected);
    }
  });

  it('holds for the sign off register too', () => {
    const asAt = d('2025-03-31');
    const expected = earnAll(context, asAt).map((e) => e.earnedValue.minor);
    const reversed = sampleContext({ signOffs: [...sampleContext().signOffs].reverse() });
    expect(earnAll(reversed, asAt).map((e) => e.earnedValue.minor)).toEqual(expected);
  });
});

describe('a sign off never reduces what is earned', () => {
  it('holds when one more stretch is released', () => {
    const asAt = d('2025-04-30');
    const before = earnAll(context, asAt).reduce((sum, e) => sum + e.earnedValue.minor, 0);
    const after = earnAll(
      sampleContext({
        signOffs: [
          ...sampleContext().signOffs,
          signOff('SO-90', 'IP-E1', 'WP-01', '12+400', '12+800', '2025-04-04'),
        ],
      }),
      asAt,
    ).reduce((sum, e) => sum + e.earnedValue.minor, 0);
    expect(after).toBeGreaterThanOrEqual(before);
  });

  it('holds when a wider stretch is released on the same point', () => {
    const asAt = d('2025-03-31');
    const before = earnAll(context, asAt).reduce((sum, e) => sum + e.earnedValue.minor, 0);
    const wider = sampleContext({
      signOffs: sampleContext().signOffs.map((s) => ({
        ...s,
        extent: range('12+000', '12+800'),
      })),
    });
    const after = earnAll(wider, asAt).reduce((sum, e) => sum + e.earnedValue.minor, 0);
    expect(after).toBeGreaterThanOrEqual(before);
  });
});
