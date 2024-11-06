import { describe, expect, it } from 'vitest';
import { formatItemCode, parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import { percent, percentValue } from '../../../../src/domain/money/percent.js';
import { buildRegister } from '../../../../src/domain/packages/register.js';
import { formatQuantity } from '../../../../src/domain/quantity/quantity.js';
import {
  allowanceFor,
  allowedQuantity,
  buildToleranceSet,
  DEFAULT_TOLERANCES,
  findOrphanRecords,
  findOverMeasures,
  findRecordsOutsideExtent,
} from '../../../../src/domain/survey/tolerance.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';
import { pkg, samplePackages } from '../../../support/packageFixtures.js';
import { records } from '../../../support/surveyFixtures.js';

const d = parseSiteDate;
const register = buildRegister(samplePackages());
const asAt = d('2025-03-31');

describe('the tolerance set', () => {
  it('reads a class rule where there is one', () => {
    expect(percentValue(allowanceFor(DEFAULT_TOLERANCES, parseItemCode('E423.1')))).toBe(10);
    expect(percentValue(allowanceFor(DEFAULT_TOLERANCES, parseItemCode('K132.1')))).toBe(0);
  });

  it('falls back for a class with no rule of its own', () => {
    expect(percentValue(allowanceFor(DEFAULT_TOLERANCES, parseItemCode('R712.1')))).toBe(2.5);
    expect(percentValue(allowanceFor(DEFAULT_TOLERANCES, parseItemCode('F624.1')))).toBe(2.5);
  });

  it('refuses two rules covering the same class', () => {
    expect(() =>
      buildToleranceSet([
        { workClass: null, allowance: percent(1) },
        { workClass: 'E', allowance: percent(5) },
        { workClass: 'E', allowance: percent(10) },
      ]),
    ).toThrow(/same work class/);
  });

  it('refuses a set with no fallback', () => {
    expect(() => buildToleranceSet([{ workClass: 'E', allowance: percent(5) }])).toThrow(
      /needs a fallback rule/,
    );
  });

  it('refuses a negative allowance', () => {
    expect(() => buildToleranceSet([{ workClass: null, allowance: percent(-1) }])).toThrow(
      /cannot be negative/,
    );
  });

  it('accepts a set built from the defaults', () => {
    expect(buildToleranceSet(DEFAULT_TOLERANCES.rules).rules).toHaveLength(5);
  });
});

describe('allowedQuantity', () => {
  const [earthworksOne] = samplePackages();

  it('adds the class allowance to the allocation', () => {
    const allowed = allowedQuantity(
      earthworksOne!,
      parseItemCode('E423.1'),
      DEFAULT_TOLERANCES,
    );
    expect(formatQuantity(allowed!)).toBe('7700.00 m3');
  });

  it('gives nothing for an item the package is not allocated', () => {
    expect(
      allowedQuantity(earthworksOne!, parseItemCode('R712.1'), DEFAULT_TOLERANCES),
    ).toBeNull();
  });
});

describe('findOverMeasures', () => {
  it('finds nothing when everything is inside the allowance', () => {
    const book = records([
      {
        id: 'SR-01',
        code: 'E423.1',
        stepId: 'excavate',
        from: '12+000',
        to: '12+800',
        amount: 7500,
        unit: 'm3',
        on: '2025-03-10',
      },
    ]);
    expect(findOverMeasures(book, register, asAt)).toEqual([]);
  });

  it('reports a measurement past the allowance', () => {
    const book = records([
      {
        id: 'SR-01',
        code: 'E423.1',
        stepId: 'excavate',
        from: '12+000',
        to: '12+800',
        amount: 8200,
        unit: 'm3',
        on: '2025-03-10',
      },
    ]);
    const found = findOverMeasures(book, register, asAt);
    expect(found).toHaveLength(1);
    expect(found[0]?.packageId).toBe('WP-01');
    expect(formatQuantity(found[0]!.excess)).toBe('500.00 m3');
    expect(found[0]?.message).toBe(
      'WP-01 E423.1 measured 8200.00 m3 against an allowance of 7700.00 m3 ' +
        '(7000.00 m3 plus 10%)',
    );
  });

  it('applies the tighter allowance a pavement item gets', () => {
    const book = records([
      {
        id: 'SR-01',
        packageId: 'WP-03',
        code: 'R712.1',
        stepId: 'subbase',
        from: '12+000',
        to: '13+600',
        amount: 4200,
        unit: 'm2',
        on: '2025-03-10',
      },
    ]);
    const found = findOverMeasures(book, register, asAt);
    expect(found.map((o) => formatItemCode(o.code))).toEqual(['R712.1']);
    expect(formatQuantity(found[0]!.allowed)).toBe('4100.00 m2');
  });

  it('adds records across a package before it decides', () => {
    const book = records([
      {
        id: 'SR-01',
        code: 'E423.1',
        stepId: 'excavate',
        from: '12+000',
        to: '12+400',
        amount: 4000,
        unit: 'm3',
        on: '2025-03-10',
      },
      {
        id: 'SR-02',
        code: 'E423.1',
        stepId: 'excavate',
        from: '12+400',
        to: '12+800',
        amount: 4000,
        unit: 'm3',
        on: '2025-03-20',
      },
    ]);
    expect(findOverMeasures(book, register, asAt)).toHaveLength(1);
  });

  it('ignores records that are not in date yet', () => {
    const book = records([
      {
        id: 'SR-01',
        code: 'E423.1',
        stepId: 'excavate',
        from: '12+000',
        to: '12+800',
        amount: 8200,
        unit: 'm3',
        on: '2025-04-10',
      },
    ]);
    expect(findOverMeasures(book, register, asAt)).toEqual([]);
  });

  it('honours a set with a different allowance', () => {
    const strict = buildToleranceSet([{ workClass: null, allowance: percent(0) }]);
    const book = records([
      {
        id: 'SR-01',
        code: 'E423.1',
        stepId: 'excavate',
        from: '12+000',
        to: '12+800',
        amount: 7001,
        unit: 'm3',
        on: '2025-03-10',
      },
    ]);
    expect(findOverMeasures(book, register, asAt, strict)).toHaveLength(1);
  });
});

describe('findRecordsOutsideExtent', () => {
  it('finds a record booked past the end of its package', () => {
    const book = records([
      {
        id: 'SR-01',
        code: 'E423.1',
        stepId: 'excavate',
        from: '12+000',
        to: '13+000',
        amount: 100,
        unit: 'm3',
        on: '2025-03-10',
      },
    ]);
    expect(findRecordsOutsideExtent(book, register).map((r) => r.id)).toEqual(['SR-01']);
  });

  it('finds nothing when the record sits inside', () => {
    const book = records([
      {
        id: 'SR-01',
        code: 'E423.1',
        stepId: 'excavate',
        from: '12+100',
        to: '12+700',
        amount: 100,
        unit: 'm3',
        on: '2025-03-10',
      },
    ]);
    expect(findRecordsOutsideExtent(book, register)).toEqual([]);
  });

  it('says nothing about a record whose package is missing', () => {
    const book = records([
      {
        id: 'SR-01',
        packageId: 'WP-99',
        code: 'E423.1',
        stepId: 'excavate',
        from: '0+000',
        to: '9+000',
        amount: 100,
        unit: 'm3',
        on: '2025-03-10',
      },
    ]);
    expect(findRecordsOutsideExtent(book, register)).toEqual([]);
    expect(findOrphanRecords(book, register).map((r) => r.id)).toEqual(['SR-01']);
  });
});

describe('findOverMeasures against an empty register', () => {
  it('reports nothing at all', () => {
    const empty = buildRegister([]);
    const book = records([
      {
        id: 'SR-01',
        code: 'E423.1',
        stepId: 'excavate',
        from: '12+000',
        to: '12+800',
        amount: 99_999,
        unit: 'm3',
        on: '2025-03-10',
      },
    ]);
    expect(findOverMeasures(book, empty, asAt)).toEqual([]);
    expect(findOrphanRecords(book, empty).map((r) => r.id)).toEqual(['SR-01']);
  });

  it('does not fire on a package nothing was measured against', () => {
    const lonely = buildRegister([
      pkg({
        id: 'WP-50',
        from: '0+000',
        to: '0+100',
        allocations: [['E423.1', 10, 'm3']],
      }),
    ]);
    expect(findOverMeasures([], lonely, asAt)).toEqual([]);
  });
});
