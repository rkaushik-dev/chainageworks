import { describe, expect, it } from 'vitest';
import { chainageRange, formatChainageRange } from '../../../../src/domain/chainage/range.js';
import { parseStation } from '../../../../src/domain/chainage/station.js';
import type { Ncr } from '../../../../src/domain/itp/ncr.js';
import {
  applyWithholding,
  buildNcr,
  describeNcr,
  isOpenAt,
  settledIn,
  withheldExtent,
} from '../../../../src/domain/itp/ncr.js';
import type { StepGate } from '../../../../src/domain/itp/signOff.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';

const at = parseStation;
const d = parseSiteDate;
const r = (from: string, to: string) => chainageRange(at(from), at(to));

const openNcr = buildNcr({
  id: 'NCR-01',
  packageId: 'WP-01',
  extent: r('12+200', '12+400'),
  stepId: 'place',
  raisedOn: d('2025-03-05'),
  description: 'fill placed at 350 mm layers against a 250 mm specification',
});

const closedNcr = buildNcr({
  id: 'NCR-02',
  packageId: 'WP-01',
  extent: r('12+000', '12+200'),
  stepId: 'place',
  raisedOn: d('2025-02-10'),
  settledOn: d('2025-03-14'),
  status: 'closed',
  description: 'compaction short of the specified density, relaid',
});

const wholePackage = buildNcr({
  id: 'NCR-03',
  packageId: 'WP-01',
  extent: r('12+600', '12+800'),
  raisedOn: d('2025-03-01'),
  description: 'no records kept for the bay',
});

describe('buildNcr', () => {
  it('defaults to open, withholding credit, and the whole package', () => {
    expect(wholePackage.status).toBe('open');
    expect(wholePackage.withholdsCredit).toBe(true);
    expect(wholePackage.stepId).toBeNull();
    expect(wholePackage.settledOn).toBeNull();
  });

  it('trims the description and refuses an empty one', () => {
    expect(buildNcr({ ...wholePackage, description: '  spalled edge  ' }).description).toBe(
      'spalled edge',
    );
    expect(() => buildNcr({ ...wholePackage, description: '   ' })).toThrow(
      /needs a description/,
    );
  });

  it('refuses an open report with a settled date', () => {
    expect(() => buildNcr({ ...wholePackage, settledOn: d('2025-03-14') })).toThrow(
      /cannot carry a settled date/,
    );
  });

  it('refuses a settled report with no date', () => {
    expect(() => buildNcr({ ...wholePackage, status: 'closed' })).toThrow(
      /needs the date it was settled/,
    );
  });

  it('refuses a report settled before it was raised', () => {
    expect(() =>
      buildNcr({ ...wholePackage, status: 'closed', settledOn: d('2025-02-01') }),
    ).toThrow(/settled before it was raised/);
  });

  it('accepts a concession as a settled outcome', () => {
    const accepted = buildNcr({
      ...wholePackage,
      status: 'accepted',
      settledOn: d('2025-03-20'),
    });
    expect(accepted.status).toBe('accepted');
  });
});

describe('isOpenAt', () => {
  it('is closed before it was raised', () => {
    expect(isOpenAt(openNcr, d('2025-03-04'))).toBe(false);
  });

  it('is open from the day it was raised', () => {
    expect(isOpenAt(openNcr, d('2025-03-05'))).toBe(true);
    expect(isOpenAt(openNcr, d('2025-12-31'))).toBe(true);
  });

  it('stops being open on the day it was settled', () => {
    expect(isOpenAt(closedNcr, d('2025-03-13'))).toBe(true);
    expect(isOpenAt(closedNcr, d('2025-03-14'))).toBe(false);
  });
});

describe('settledIn', () => {
  it('finds reports settled inside a window', () => {
    const ncrs = [openNcr, closedNcr, wholePackage];
    expect(settledIn(ncrs, d('2025-03-01'), d('2025-03-31')).map((n) => n.id)).toEqual([
      'NCR-02',
    ]);
    expect(settledIn(ncrs, d('2025-04-01'), d('2025-04-30'))).toEqual([]);
  });
});

describe('withheldExtent', () => {
  const ncrs: Ncr[] = [openNcr, closedNcr, wholePackage];

  it('holds back the stretch an open report covers', () => {
    const held = withheldExtent({
      ncrs,
      packageId: 'WP-01',
      stepId: 'place',
      asAt: d('2025-03-10'),
    });
    expect(held.map(formatChainageRange)).toEqual([
      '12+000.000 to 12+400.000',
      '12+600.000 to 12+800.000',
    ]);
  });

  it('releases a stretch once the report is settled', () => {
    const held = withheldExtent({
      ncrs,
      packageId: 'WP-01',
      stepId: 'place',
      asAt: d('2025-03-31'),
    });
    expect(held.map(formatChainageRange)).toEqual([
      '12+200.000 to 12+400.000',
      '12+600.000 to 12+800.000',
    ]);
  });

  it('applies a report with no step named to every step', () => {
    const held = withheldExtent({
      ncrs,
      packageId: 'WP-01',
      stepId: 'trim',
      asAt: d('2025-03-31'),
    });
    expect(held.map(formatChainageRange)).toEqual(['12+600.000 to 12+800.000']);
  });

  it('ignores a report that does not withhold credit', () => {
    const advisory = buildNcr({ ...openNcr, id: 'NCR-04', withholdsCredit: false });
    const held = withheldExtent({
      ncrs: [advisory],
      packageId: 'WP-01',
      stepId: 'place',
      asAt: d('2025-03-31'),
    });
    expect(held).toEqual([]);
  });

  it('ignores a report against another package', () => {
    const held = withheldExtent({
      ncrs,
      packageId: 'WP-02',
      stepId: 'place',
      asAt: d('2025-03-31'),
    });
    expect(held).toEqual([]);
  });
});

describe('applyWithholding', () => {
  const openGate: StepGate = {
    stepId: 'place',
    state: 'open',
    openExtent: [r('12+000', '12+800')],
    heldPoints: [],
  };

  it('leaves the gate alone when nothing is withheld', () => {
    expect(applyWithholding(openGate, [])).toBe(openGate);
  });

  it('punches the withheld stretch out of the open extent', () => {
    const narrowed = applyWithholding(openGate, [r('12+200', '12+400')]);
    expect(narrowed.openExtent.map(formatChainageRange)).toEqual([
      '12+000.000 to 12+200.000',
      '12+400.000 to 12+800.000',
    ]);
    expect(narrowed.state).toBe('part_open');
  });

  it('closes the gate when the whole extent is withheld', () => {
    const shut = applyWithholding(openGate, [r('12+000', '12+800')]);
    expect(shut.openExtent).toEqual([]);
    expect(shut.state).toBe('held');
  });

  it('leaves an already held gate held', () => {
    const held: StepGate = {
      stepId: 'place',
      state: 'held',
      openExtent: [],
      heldPoints: ['IP-01'],
    };
    expect(applyWithholding(held, [r('12+000', '12+800')]).state).toBe('held');
  });
});

describe('describeNcr', () => {
  it('reads like a register line for an open report', () => {
    expect(describeNcr(openNcr)).toBe(
      'NCR-01 raised 2025-03-05, open: fill placed at 350 mm layers against a 250 mm specification',
    );
  });

  it('names the settlement for a closed report', () => {
    expect(describeNcr(closedNcr)).toContain('closed 2025-03-14');
  });
});
