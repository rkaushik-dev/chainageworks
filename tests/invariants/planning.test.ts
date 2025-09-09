import { describe, expect, it } from 'vitest';
import { coveredLength, intersectRangeLists } from '../../src/domain/chainage/range.js';
import { MM_PER_METRE } from '../../src/domain/chainage/station.js';
import { allocateHaul } from '../../src/domain/earthworks/allocation.js';
import { haulMoment, isBalanced } from '../../src/domain/earthworks/allocation.js';
import { moneyFromMajor } from '../../src/domain/money/money.js';
import { quantity, quantityAmount } from '../../src/domain/quantity/quantity.js';
import { buildCalendar } from '../../src/domain/time/calendar.js';
import { addDays } from '../../src/domain/time/siteDate.js';
import { diagnose, isHealthy } from '../../src/app/doctor.js';
import { buildLookahead, readyNow, waitingOnInspection } from '../../src/progress/lookahead.js';
import { buildAllowances } from '../../src/progress/productivity.js';
import { packageStepProgress } from '../../src/progress/stepProgress.js';
import { d, sampleContext } from '../support/contextFixtures.js';
import { commercialJob, haulingJob, sampleJob } from '../support/jobFixtures.js';
import { samplePackages } from '../support/packageFixtures.js';
import { rng } from '../support/random.js';

const context = sampleContext();
const calendar = buildCalendar({ id: 'C1', name: 'main works' });
const packages = samplePackages();
const allowances = buildAllowances([
  { stepId: 'strip', metresPerHour: 10 },
  { stepId: 'excavate', metresPerHour: 12 },
]);

const starts = Array.from({ length: 120 }, (_, i) => addDays(d('2025-01-01'), i * 3));

describe('a lookahead never asks for work that has already been built', () => {
  it('holds from every start date across the job', () => {
    for (const from of starts) {
      const lookahead = buildLookahead({ context, packages, calendar, allowances, from });
      for (const step of lookahead.steps) {
        const pkg = packages.find((p) => p.id === step.packageId)!;
        const built = packageStepProgress(context, pkg, from).find(
          (detail) => detail.stepId === step.stepId,
        )!;
        const overlap = intersectRangeLists([...step.remaining], [...built.builtExtent]);
        expect(coveredLength(overlap)).toBe(0);
      }
    }
  });

  it('never asks for more than the package is long', () => {
    for (const from of starts) {
      const lookahead = buildLookahead({ context, packages, calendar, allowances, from });
      for (const step of lookahead.steps) {
        const pkg = packages.find((p) => p.id === step.packageId)!;
        const length = (pkg.extent.to - pkg.extent.from) / MM_PER_METRE;
        expect(step.remainingMetres).toBeLessThanOrEqual(length + 1e-9);
        expect(step.remainingMetres).toBeGreaterThan(0);
      }
    }
  });
});

describe('a lookahead splits every step exactly once', () => {
  it('never puts a step on both the ready list and the held list', () => {
    for (const from of starts) {
      const lookahead = buildLookahead({ context, packages, calendar, allowances, from });
      const ready = new Set(readyNow(lookahead).map((s) => `${s.packageId}/${s.stepId}`));
      const held = new Set(
        waitingOnInspection(lookahead).map((s) => `${s.packageId}/${s.stepId}`),
      );
      for (const key of held) expect(ready.has(key)).toBe(false);
    }
  });

  it('shrinks the work in front as the job goes on', () => {
    const early = buildLookahead({
      context,
      packages,
      calendar,
      allowances,
      from: d('2025-01-01'),
      weeks: 52,
    });
    const late = buildLookahead({
      context,
      packages,
      calendar,
      allowances,
      from: d('2025-04-01'),
      weeks: 52,
    });
    expect(late.daysOfWork).toBeLessThanOrEqual(early.daysOfWork);
  });
});

describe('a haul plan never moves more than there is or more than fits', () => {
  it('holds across random sites and links', () => {
    const random = rng(31415);
    for (let i = 0; i < 200; i += 1) {
      const cut = random.int(0, 20_000);
      const borrow = random.int(0, 10_000);
      const fill = random.int(0, 25_000);
      const tip = random.int(0, 15_000);

      const allocation = allocateHaul({
        sources: [
          {
            id: 'CUT',
            name: 'cut',
            kind: 'cut',
            volume: quantity(cut, 'm3'),
            unitCost: moneyFromMajor(0, 'GBP'),
          },
          {
            id: 'BOR',
            name: 'borrow',
            kind: 'borrow',
            volume: quantity(borrow, 'm3'),
            unitCost: moneyFromMajor(random.int(1, 6), 'GBP'),
          },
        ],
        destinations: [
          {
            id: 'FIL',
            name: 'fill',
            kind: 'fill',
            volume: quantity(fill, 'm3'),
            unitCost: moneyFromMajor(0, 'GBP'),
          },
          {
            id: 'TIP',
            name: 'tip',
            kind: 'tip',
            volume: quantity(tip, 'm3'),
            unitCost: moneyFromMajor(random.int(3, 12), 'GBP'),
          },
        ],
        links: [
          { fromId: 'CUT', toId: 'FIL', km: random.int(1, 10) },
          { fromId: 'CUT', toId: 'TIP', km: random.int(1, 10) },
          { fromId: 'BOR', toId: 'FIL', km: random.int(1, 10) },
        ],
        haulRate: moneyFromMajor(1, 'GBP'),
      });

      const outOfCut = allocation.movements
        .filter((m) => m.fromId === 'CUT')
        .reduce((total, m) => total + quantityAmount(m.volume), 0);
      const intoFill = allocation.movements
        .filter((m) => m.toId === 'FIL')
        .reduce((total, m) => total + quantityAmount(m.volume), 0);

      expect(outOfCut).toBeLessThanOrEqual(cut);
      expect(intoFill).toBeLessThanOrEqual(fill);
      expect(allocation.cost.minor).toBeGreaterThanOrEqual(0);
      expect(haulMoment(allocation)).toBeGreaterThanOrEqual(0);
      expect(quantityAmount(allocation.unplaced)).toBeGreaterThanOrEqual(0);
    }
  });

  it('is balanced only when nothing is left at either end', () => {
    const balanced = allocateHaul({
      sources: [
        {
          id: 'CUT',
          name: 'cut',
          kind: 'cut',
          volume: quantity(5000, 'm3'),
          unitCost: moneyFromMajor(0, 'GBP'),
        },
      ],
      destinations: [
        {
          id: 'FIL',
          name: 'fill',
          kind: 'fill',
          volume: quantity(5000, 'm3'),
          unitCost: moneyFromMajor(0, 'GBP'),
        },
      ],
      links: [{ fromId: 'CUT', toId: 'FIL', km: 2 }],
      haulRate: moneyFromMajor(1, 'GBP'),
    });
    expect(isBalanced(balanced)).toBe(true);
  });
});

describe('the doctor never reports an error on a job the tool built itself', () => {
  it('holds for every fixture', () => {
    for (const job of [sampleJob(), commercialJob(), haulingJob()]) {
      const diagnosis = diagnose(job);
      expect([job.settings.id, diagnosis.errors]).toEqual([job.settings.id, 0]);
      expect(isHealthy(diagnosis)).toBe(true);
    }
  });

  it('counts every finding as one level or the other', () => {
    const diagnosis = diagnose(haulingJob());
    expect(diagnosis.errors + diagnosis.warnings).toBe(diagnosis.findings.length);
  });
});
