import { describe, expect, it } from 'vitest';
import type { ChainageRange } from '../../../src/domain/chainage/range.js';
import { formatChainageRange } from '../../../src/domain/chainage/range.js';
import { earnedFraction } from '../../../src/domain/credit/profile.js';
import { BULK_EARTHWORKS } from '../../../src/domain/credit/catalogue.js';
import { percentValue } from '../../../src/domain/money/percent.js';
import { requirePackage } from '../../../src/domain/packages/register.js';
import {
  builtExtentFor,
  gateFor,
  heldBackExtent,
  packageStepProgress,
  stepProgressFor,
  toStepProgress,
} from '../../../src/progress/stepProgress.js';
import {
  d,
  range,
  sampleContext,
  sampleRecords,
  signOff,
} from '../../support/contextFixtures.js';

const context = sampleContext();
const wp01 = requirePackage(context.register, 'WP-01');
const cutOff = d('2025-03-31');
const show = (ranges: readonly ChainageRange[]) => ranges.map(formatChainageRange);

describe('builtExtentFor', () => {
  it('takes the stretch the survey book covers for a step', () => {
    expect(show(builtExtentFor(context, wp01, 'strip', cutOff))).toEqual([
      '12+000.000 to 12+800.000',
    ]);
    expect(show(builtExtentFor(context, wp01, 'excavate', cutOff))).toEqual([
      '12+000.000 to 12+600.000',
    ]);
  });

  it('is empty for a step nothing was booked against', () => {
    expect(builtExtentFor(context, wp01, 'trim', cutOff)).toEqual([]);
  });

  it('ignores records that are not in date yet', () => {
    expect(builtExtentFor(context, wp01, 'excavate', d('2025-03-05'))).toEqual([]);
  });

  it('clips a record that runs past the package', () => {
    const wide = sampleContext({
      records: [
        ...sampleRecords().slice(0, 1),
        {
          ...sampleRecords()[1]!,
          extent: range('11+000', '14+000'),
        },
      ],
    });
    expect(show(builtExtentFor(wide, wp01, 'excavate', cutOff))).toEqual([
      '12+000.000 to 12+800.000',
    ]);
  });
});

describe('gateFor', () => {
  it('is open over the whole package when no plan gates the profile', () => {
    const ungated = sampleContext({ plans: [] });
    const gate = gateFor(ungated, wp01, 'excavate', cutOff);
    expect(gate.state).toBe('open');
    expect(show(gate.openExtent)).toEqual(['12+000.000 to 12+800.000']);
  });

  it('is open only over what the hold point released', () => {
    const gate = gateFor(context, wp01, 'excavate', cutOff);
    expect(show(gate.openExtent)).toEqual(['12+000.000 to 12+400.000']);
  });

  it('is open over the whole package for a step with no hold point', () => {
    expect(gateFor(context, wp01, 'strip', cutOff).state).toBe('open');
  });
});

describe('stepProgressFor', () => {
  it('credits the whole of a step with nothing gating it', () => {
    const strip = stepProgressFor(context, wp01, 'strip', cutOff);
    expect(strip.fraction).toBe(1);
    expect(strip.gate).toBe('open');
    expect(heldBackExtent(strip)).toEqual([]);
  });

  it('credits only what the hold point released', () => {
    const excavate = stepProgressFor(context, wp01, 'excavate', cutOff);
    expect(show(excavate.builtExtent)).toEqual(['12+000.000 to 12+600.000']);
    expect(show(excavate.creditedExtent)).toEqual(['12+000.000 to 12+400.000']);
    expect(excavate.fraction).toBe(0.5);
    expect(show(heldBackExtent(excavate))).toEqual(['12+400.000 to 12+600.000']);
  });

  it('takes the open non conformance out of what is left', () => {
    const place = stepProgressFor(context, wp01, 'place', cutOff);
    expect(show(place.builtExtent)).toEqual(['12+000.000 to 12+400.000']);
    expect(show(place.creditedExtent)).toEqual([
      '12+000.000 to 12+100.000',
      '12+150.000 to 12+200.000',
    ]);
    expect(place.fraction).toBeCloseTo(0.1875, 6);
    expect(show(place.withheldExtent)).toEqual(['12+100.000 to 12+150.000']);
  });

  it('credits nothing for a step nobody has started', () => {
    const trim = stepProgressFor(context, wp01, 'trim', cutOff);
    expect(trim.fraction).toBe(0);
    expect(trim.creditedExtent).toEqual([]);
  });

  it('names the hold point that is stopping a step', () => {
    const noSignOffs = sampleContext({ signOffs: [] });
    const excavate = stepProgressFor(noSignOffs, wp01, 'excavate', cutOff);
    expect(excavate.gate).toBe('held');
    expect(excavate.heldPoints).toEqual(['IP-E1']);
    expect(excavate.fraction).toBe(0);
    expect(show(heldBackExtent(excavate))).toEqual(['12+000.000 to 12+600.000']);
  });

  it('releases the withheld stretch once the report is settled', () => {
    const settled = sampleContext({
      ncrs: [
        {
          ...sampleContext().ncrs[0]!,
          status: 'closed',
          settledOn: d('2025-03-28'),
        },
      ],
    });
    const place = stepProgressFor(settled, wp01, 'place', cutOff);
    expect(show(place.creditedExtent)).toEqual(['12+000.000 to 12+200.000']);
  });

  it('reads more once a later sign off lands', () => {
    const later = sampleContext({
      signOffs: [
        ...sampleContext().signOffs,
        signOff('SO-03', 'IP-E1', 'WP-01', '12+400', '12+800', '2025-04-04'),
      ],
    });
    expect(stepProgressFor(later, wp01, 'excavate', cutOff).fraction).toBe(0.5);
    expect(stepProgressFor(later, wp01, 'excavate', d('2025-04-30')).fraction).toBe(0.75);
  });
});

describe('packageStepProgress and the rule of credit', () => {
  const details = packageStepProgress(context, wp01, cutOff);

  it('gives one detail per step in the profile order', () => {
    expect(details.map((s) => s.stepId)).toEqual(['strip', 'excavate', 'place', 'trim']);
  });

  it('turns the details into the shape the rule of credit reads', () => {
    expect(toStepProgress(details).map((s) => percentValue(s.fraction))).toEqual([
      100, 50, 18.75, 0,
    ]);
  });

  it('earns nothing for the placing, because the excavation is not finished', () => {
    const earned = earnedFraction(BULK_EARTHWORKS, toStepProgress(details));
    expect(percentValue(earned)).toBe(32.5);
  });

  it('releases the placing once the excavation is signed off in full', () => {
    const complete = sampleContext({
      signOffs: [
        signOff('SO-01', 'IP-E1', 'WP-01', '12+000', '12+800', '2025-03-12'),
        signOff('SO-02', 'IP-E2', 'WP-01', '12+000', '12+800', '2025-03-22'),
      ],
      records: [
        ...sampleRecords().slice(0, 1),
        {
          ...sampleRecords()[1]!,
          extent: range('12+000', '12+800'),
        },
        sampleRecords()[2]!,
      ],
      ncrs: [],
    });
    const earned = earnedFraction(
      BULK_EARTHWORKS,
      toStepProgress(packageStepProgress(complete, wp01, cutOff)),
    );
    expect(percentValue(earned)).toBe(72.5);
  });
});
