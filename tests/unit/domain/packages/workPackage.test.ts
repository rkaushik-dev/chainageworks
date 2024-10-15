import { describe, expect, it } from 'vitest';
import { NotFoundError } from '../../../../src/errors.js';
import { formatItemCode, parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import { chainageRange } from '../../../../src/domain/chainage/range.js';
import { parseStation } from '../../../../src/domain/chainage/station.js';
import { moneyMajor } from '../../../../src/domain/money/money.js';
import {
  allocatedCodes,
  buildWorkPackage,
  describeWorkPackage,
  findAllocation,
  packageBudget,
  packageCovers,
  packageLength,
  packagesClash,
  requireAllocation,
} from '../../../../src/domain/packages/workPackage.js';
import { quantity } from '../../../../src/domain/quantity/quantity.js';
import { sampleBill } from '../../../support/billFixtures.js';
import { pkg, samplePackages } from '../../../support/packageFixtures.js';

const [earthworksOne, earthworksTwo, pavement] = samplePackages();

describe('buildWorkPackage', () => {
  it('keeps what it was given', () => {
    expect(earthworksOne?.id).toBe('WP-01');
    expect(earthworksOne?.profileId).toBe('bulk-earthworks');
    expect(earthworksOne?.section).toBe('section 1');
    expect(packageLength(earthworksOne!)).toBe(800_000);
  });

  it('defaults the section to nothing', () => {
    const bare = buildWorkPackage({
      id: 'WP-99',
      name: 'odds and ends',
      alignmentId: 'A1',
      extent: chainageRange(parseStation('0+000'), parseStation('0+100')),
      profileId: 'single-step',
      allocations: [{ code: parseItemCode('E423.1'), quantity: quantity(1, 'm3') }],
    });
    expect(bare.section).toBe('');
  });

  it('refuses a package with no id', () => {
    expect(() =>
      pkg({ id: '  ', from: '0+000', to: '0+100', allocations: [['E423.1', 1, 'm3']] }),
    ).toThrow(/needs an id/);
  });

  it('refuses a package that can never earn', () => {
    expect(() => pkg({ id: 'WP-X', from: '0+000', to: '0+100', allocations: [] })).toThrow(
      /can never earn/,
    );
  });

  it('refuses the same bill item allocated twice', () => {
    expect(() =>
      pkg({
        id: 'WP-X',
        from: '0+000',
        to: '0+100',
        allocations: [
          ['E423.1', 1, 'm3'],
          ['E 4 2 3 . 1', 2, 'm3'],
        ],
      }),
    ).toThrow(/allocated twice/);
  });

  it('refuses a negative allocation', () => {
    expect(() =>
      pkg({ id: 'WP-X', from: '0+000', to: '0+100', allocations: [['E423.1', -1, 'm3']] }),
    ).toThrow(/cannot be negative/);
  });
});

describe('allocations', () => {
  it('finds one by code', () => {
    expect(findAllocation(earthworksOne!, parseItemCode('E423.1'))?.quantity.milli).toBe(
      7_000_000,
    );
    expect(findAllocation(earthworksOne!, parseItemCode('R712.1'))).toBeNull();
  });

  it('throws from the strict lookup', () => {
    expect(() => requireAllocation(earthworksOne!, parseItemCode('R712.1'))).toThrow(
      NotFoundError,
    );
  });

  it('lists the codes a package earns against', () => {
    expect(allocatedCodes(earthworksTwo!).map((code) => formatItemCode(code))).toEqual([
      'E423.1',
      'E423.2',
      'E532.1',
    ]);
  });
});

describe('packageCovers', () => {
  it('answers on the half open extent', () => {
    expect(packageCovers(earthworksOne!, parseStation('12+000'))).toBe(true);
    expect(packageCovers(earthworksOne!, parseStation('12+400'))).toBe(true);
    expect(packageCovers(earthworksOne!, parseStation('12+800'))).toBe(false);
    expect(packageCovers(earthworksTwo!, parseStation('12+800'))).toBe(true);
  });
});

describe('packagesClash', () => {
  it('is false for packages that abut', () => {
    expect(packagesClash(earthworksOne!, earthworksTwo!)).toBe(false);
  });

  it('is false when they overlap but share no bill item', () => {
    expect(packagesClash(earthworksOne!, pavement!)).toBe(false);
  });

  it('is true when they overlap and share an item', () => {
    const overlapping = pkg({
      id: 'WP-04',
      from: '12+400',
      to: '13+000',
      allocations: [['E423.1', 100, 'm3']],
    });
    expect(packagesClash(earthworksOne!, overlapping)).toBe(true);
  });

  it('is false against itself', () => {
    expect(packagesClash(earthworksOne!, earthworksOne!)).toBe(false);
  });

  it('is false across alignments', () => {
    const other = pkg({
      id: 'WP-05',
      from: '12+400',
      to: '13+000',
      alignmentId: 'A2',
      allocations: [['E423.1', 100, 'm3']],
    });
    expect(packagesClash(earthworksOne!, other)).toBe(false);
  });
});

describe('packageBudget', () => {
  const bill = sampleBill();

  it('values the allocation at the bill rates', () => {
    expect(moneyMajor(packageBudget(earthworksOne!, bill))).toBe(7000 * 4.85 + 5000 * 3.15);
  });

  it('values the pavement package', () => {
    expect(moneyMajor(packageBudget(pavement!, bill))).toBe(4000 * 11.4 + 4000 * 18.75);
  });

  it('refuses an allocation the bill does not carry', () => {
    const stray = pkg({
      id: 'WP-06',
      from: '0+000',
      to: '0+100',
      allocations: [['Z999.1', 1, 'm3']],
    });
    expect(() => packageBudget(stray, bill)).toThrow(NotFoundError);
  });

  it('refuses an allocation in the wrong unit for the item', () => {
    const wrong = pkg({
      id: 'WP-07',
      from: '0+000',
      to: '0+100',
      allocations: [['E423.1', 1, 'm2']],
    });
    expect(() => packageBudget(wrong, bill)).toThrow(/different units/);
  });
});

describe('describeWorkPackage', () => {
  it('reads like a package register line', () => {
    expect(describeWorkPackage(earthworksOne!)).toBe(
      'WP-01 earthworks, chainage 12+000 to 12+800 [12+000.000 to 12+800.000]',
    );
  });
});
