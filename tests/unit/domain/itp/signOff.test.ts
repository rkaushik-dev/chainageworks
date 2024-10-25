import { describe, expect, it } from 'vitest';
import { PIPE_RUN } from '../../../../src/domain/credit/catalogue.js';
import {
  chainageRange,
  formatChainageRange,
  intersectRangeLists,
} from '../../../../src/domain/chainage/range.js';
import { parseStation } from '../../../../src/domain/chainage/station.js';
import { buildInspectionPlan } from '../../../../src/domain/itp/plan.js';
import type { SignOff } from '../../../../src/domain/itp/signOff.js';
import {
  buildSignOff,
  describeSignOff,
  gateForStep,
  openFraction,
  releases,
} from '../../../../src/domain/itp/signOff.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';

const at = parseStation;
const d = parseSiteDate;
const r = (from: string, to: string) => chainageRange(at(from), at(to));

const plan = buildInspectionPlan(
  {
    id: 'ITP-PIPE',
    name: 'pipe laying',
    profileId: 'pipe-run',
    points: [
      { id: 'IP-01', name: 'trench formation', type: 'hold', stepId: 'excavate' },
      { id: 'IP-02', name: 'bedding grading', type: 'witness', stepId: 'bed' },
      { id: 'IP-03', name: 'line and level', type: 'hold', stepId: 'lay' },
      { id: 'IP-06', name: 'joint inspection', type: 'hold', stepId: 'lay' },
    ],
  },
  PIPE_RUN,
);

const extent = r('12+000', '12+800');

const sign = (
  id: string,
  pointId: string,
  from: string,
  to: string,
  signedOn: string,
  outcome?: 'passed' | 'passed_with_comment' | 'failed',
): SignOff =>
  buildSignOff({
    id,
    pointId,
    packageId: 'WP-01',
    extent: r(from, to),
    signedOn: d(signedOn),
    by: 'A Surveyor',
    ...(outcome === undefined ? {} : { outcome }),
  });

const gate = (stepId: string, signOffs: readonly SignOff[], asAt = '2025-03-31') =>
  gateForStep({ plan, signOffs, stepId, packageId: 'WP-01', extent, asAt: d(asAt) });

describe('buildSignOff', () => {
  it('defaults the outcome to passed', () => {
    expect(sign('S1', 'IP-01', '12+000', '12+800', '2025-03-10').outcome).toBe('passed');
  });

  it('refuses a sign off with nobody named', () => {
    expect(() =>
      buildSignOff({
        id: 'S1',
        pointId: 'IP-01',
        packageId: 'WP-01',
        extent,
        signedOn: d('2025-03-10'),
        by: '   ',
      }),
    ).toThrow(/name who signed it/);
  });

  it('treats a failed inspection as no release', () => {
    expect(releases(sign('S1', 'IP-01', '12+000', '12+800', '2025-03-10'))).toBe(true);
    const commented = sign(
      'S2',
      'IP-01',
      '12+000',
      '12+800',
      '2025-03-10',
      'passed_with_comment',
    );
    expect(releases(commented)).toBe(true);
    expect(releases(sign('S3', 'IP-01', '12+000', '12+800', '2025-03-10', 'failed'))).toBe(
      false,
    );
  });
});

describe('gateForStep', () => {
  it('is open over the whole extent when the step has no hold point', () => {
    const result = gate('bed', []);
    expect(result.state).toBe('open');
    expect(result.openExtent.map(formatChainageRange)).toEqual(['12+000.000 to 12+800.000']);
  });

  it('is held when the hold point has not been signed at all', () => {
    const result = gate('excavate', []);
    expect(result.state).toBe('held');
    expect(result.openExtent).toEqual([]);
    expect(result.heldPoints).toEqual(['IP-01']);
    expect(openFraction(result, extent)).toBe(0);
  });

  it('opens the whole extent when the hold point covers it', () => {
    const result = gate('excavate', [sign('S1', 'IP-01', '12+000', '12+800', '2025-03-10')]);
    expect(result.state).toBe('open');
    expect(openFraction(result, extent)).toBe(1);
  });

  it('opens only the stretch that was inspected', () => {
    const result = gate('excavate', [sign('S1', 'IP-01', '12+000', '12+200', '2025-03-10')]);
    expect(result.state).toBe('part_open');
    expect(result.openExtent.map(formatChainageRange)).toEqual(['12+000.000 to 12+200.000']);
    expect(openFraction(result, extent)).toBe(0.25);
  });

  it('adds up several sign offs on the same point', () => {
    const result = gate('excavate', [
      sign('S1', 'IP-01', '12+000', '12+200', '2025-03-10'),
      sign('S2', 'IP-01', '12+200', '12+600', '2025-03-14'),
    ]);
    expect(result.openExtent.map(formatChainageRange)).toEqual(['12+000.000 to 12+600.000']);
    expect(openFraction(result, extent)).toBe(0.75);
  });

  it('needs every hold point on the step, not just one', () => {
    const oneOnly = gate('lay', [sign('S1', 'IP-03', '12+000', '12+800', '2025-03-10')]);
    expect(oneOnly.state).toBe('held');
    expect(oneOnly.heldPoints).toEqual(['IP-06']);

    const both = gate('lay', [
      sign('S1', 'IP-03', '12+000', '12+800', '2025-03-10'),
      sign('S2', 'IP-06', '12+000', '12+800', '2025-03-11'),
    ]);
    expect(both.state).toBe('open');
  });

  it('takes the overlap when two hold points released different stretches', () => {
    const result = gate('lay', [
      sign('S1', 'IP-03', '12+000', '12+600', '2025-03-10'),
      sign('S2', 'IP-06', '12+400', '12+800', '2025-03-11'),
    ]);
    expect(result.openExtent.map(formatChainageRange)).toEqual(['12+400.000 to 12+600.000']);
    expect(result.state).toBe('part_open');
    expect(result.heldPoints).toEqual([]);
  });

  it('ignores a sign off dated after the cut off', () => {
    const late = [sign('S1', 'IP-01', '12+000', '12+800', '2025-04-02')];
    expect(gate('excavate', late, '2025-03-31').state).toBe('held');
    expect(gate('excavate', late, '2025-04-30').state).toBe('open');
  });

  it('ignores a failed inspection', () => {
    const failed = [sign('S1', 'IP-01', '12+000', '12+800', '2025-03-10', 'failed')];
    expect(gate('excavate', failed).state).toBe('held');
  });

  it('ignores a sign off against another package', () => {
    const other = buildSignOff({
      id: 'S9',
      pointId: 'IP-01',
      packageId: 'WP-02',
      extent,
      signedOn: d('2025-03-10'),
      by: 'A Surveyor',
    });
    expect(gate('excavate', [other]).state).toBe('held');
  });

  it('clips a sign off that runs past the extent asked about', () => {
    const result = gate('excavate', [sign('S1', 'IP-01', '11+000', '14+000', '2025-03-10')]);
    expect(result.openExtent.map(formatChainageRange)).toEqual(['12+000.000 to 12+800.000']);
  });
});

describe('intersectRangeLists', () => {
  it('finds the shared stretches', () => {
    const shared = intersectRangeLists(
      [r('12+000', '12+600'), r('13+000', '13+400')],
      [r('12+400', '13+200')],
    );
    expect(shared.map(formatChainageRange)).toEqual([
      '12+400.000 to 12+600.000',
      '13+000.000 to 13+200.000',
    ]);
  });

  it('gives nothing when either side is empty', () => {
    expect(intersectRangeLists([], [r('12+000', '12+600')])).toEqual([]);
    expect(intersectRangeLists([r('12+000', '12+600')], [])).toEqual([]);
  });

  it('normalises both sides first, so overlap is not counted twice', () => {
    const shared = intersectRangeLists(
      [r('12+000', '12+400'), r('12+200', '12+600')],
      [r('12+000', '12+800')],
    );
    expect(shared.map(formatChainageRange)).toEqual(['12+000.000 to 12+600.000']);
  });
});

describe('describeSignOff', () => {
  it('reads like a register line', () => {
    expect(describeSignOff(sign('S1', 'IP-01', '12+000', '12+800', '2025-03-10'))).toBe(
      'IP-01 passed on 2025-03-10 by A Surveyor',
    );
  });
});
