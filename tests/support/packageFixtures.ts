import { parseItemCode } from '../../src/domain/bill/itemCode.js';
import { chainageRange } from '../../src/domain/chainage/range.js';
import { parseStation } from '../../src/domain/chainage/station.js';
import type { WorkPackage } from '../../src/domain/packages/workPackage.js';
import { buildWorkPackage } from '../../src/domain/packages/workPackage.js';
import { quantity } from '../../src/domain/quantity/quantity.js';
import type { UnitCode } from '../../src/domain/quantity/unit.js';

export interface PackageSpec {
  readonly id: string;
  readonly name?: string;
  readonly from: string;
  readonly to: string;
  readonly profileId?: string;
  readonly section?: string;
  readonly alignmentId?: string;
  readonly allocations: readonly [string, number, UnitCode][];
}

export function pkg(spec: PackageSpec): WorkPackage {
  return buildWorkPackage({
    id: spec.id,
    name: spec.name ?? `package ${spec.id}`,
    alignmentId: spec.alignmentId ?? 'A1',
    extent: chainageRange(parseStation(spec.from), parseStation(spec.to)),
    section: spec.section ?? 'section 1',
    profileId: spec.profileId ?? 'single-step',
    allocations: spec.allocations.map(([code, amount, unit]) => ({
      code: parseItemCode(code),
      quantity: quantity(amount, unit),
    })),
  });
}

/**
 * Three packages down the alignment, splitting the sample bill between them.
 * The earthworks split is deliberately short of the billed quantity, so the
 * reconciliation has something to report.
 */
export const SAMPLE_PACKAGES: readonly PackageSpec[] = [
  {
    id: 'WP-01',
    name: 'earthworks, chainage 12+000 to 12+800',
    from: '12+000',
    to: '12+800',
    profileId: 'bulk-earthworks',
    allocations: [
      ['E423.1', 7000, 'm3'],
      ['E532.1', 5000, 'm3'],
    ],
  },
  {
    id: 'WP-02',
    name: 'earthworks, chainage 12+800 to 13+600',
    from: '12+800',
    to: '13+600',
    profileId: 'bulk-earthworks',
    allocations: [
      ['E423.1', 5000, 'm3'],
      ['E423.2', 3200, 'm3'],
      ['E532.1', 4800, 'm3'],
    ],
  },
  {
    id: 'WP-03',
    name: 'pavement, chainage 12+000 to 13+600',
    from: '12+000',
    to: '13+600',
    section: 'section 2',
    profileId: 'pavement',
    allocations: [
      ['R712.1', 4000, 'm2'],
      ['R712.2', 4000, 'm2'],
    ],
  },
];

export const samplePackages = (): WorkPackage[] => SAMPLE_PACKAGES.map(pkg);
