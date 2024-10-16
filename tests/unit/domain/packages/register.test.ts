import { describe, expect, it } from 'vitest';
import { NotFoundError } from '../../../../src/errors.js';
import { formatItemCode, parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import { chainageRange, formatChainageRange } from '../../../../src/domain/chainage/range.js';
import { parseStation } from '../../../../src/domain/chainage/station.js';
import {
  allocatedQuantity,
  buildRegister,
  clashes,
  coveredExtent,
  findPackage,
  packagesAt,
  packagesFor,
  packagesInSection,
  reconcileWithBill,
  requirePackage,
  sections,
  uncoveredStretches,
} from '../../../../src/domain/packages/register.js';
import { formatQuantity } from '../../../../src/domain/quantity/quantity.js';
import { sampleBill } from '../../../support/billFixtures.js';
import { pkg, samplePackages } from '../../../support/packageFixtures.js';

const register = buildRegister(samplePackages());
const bill = sampleBill();
const at = parseStation;

describe('buildRegister', () => {
  it('sorts by where the package starts, then by id', () => {
    expect(register.packages.map((p) => p.id)).toEqual(['WP-01', 'WP-03', 'WP-02']);
  });

  it('refuses two packages sharing an id', () => {
    expect(() =>
      buildRegister([
        pkg({ id: 'WP-01', from: '0+000', to: '0+100', allocations: [['E423.1', 1, 'm3']] }),
        pkg({ id: 'WP-01', from: '0+100', to: '0+200', allocations: [['E423.1', 1, 'm3']] }),
      ]),
    ).toThrow(/share an id/);
  });

  it('accepts an empty register', () => {
    expect(buildRegister([]).packages).toEqual([]);
  });
});

describe('looking packages up', () => {
  it('finds by id', () => {
    expect(findPackage(register, 'WP-02')?.name).toMatch(/12\+800 to 13\+600/);
    expect(findPackage(register, 'WP-99')).toBeNull();
    expect(requirePackage(register, 'WP-01').id).toBe('WP-01');
  });

  it('throws for an id nothing carries', () => {
    expect(() => requirePackage(register, 'WP-99')).toThrow(NotFoundError);
  });

  it('lists the sections', () => {
    expect(sections(register)).toEqual(['section 1', 'section 2']);
    expect(packagesInSection(register, 'section 2').map((p) => p.id)).toEqual(['WP-03']);
  });

  it('finds every package covering a station', () => {
    expect(packagesAt(register, at('12+400')).map((p) => p.id)).toEqual(['WP-01', 'WP-03']);
    expect(packagesAt(register, at('13+000')).map((p) => p.id)).toEqual(['WP-03', 'WP-02']);
    expect(packagesAt(register, at('14+000'))).toEqual([]);
  });

  it('finds every package earning against an item', () => {
    expect(packagesFor(register, parseItemCode('E423.1')).map((p) => p.id)).toEqual([
      'WP-01',
      'WP-02',
    ]);
    expect(packagesFor(register, parseItemCode('F624.1'))).toEqual([]);
  });
});

describe('allocatedQuantity', () => {
  it('adds the shares across every package', () => {
    expect(formatQuantity(allocatedQuantity(register, parseItemCode('E423.1'))!)).toBe(
      '12000.00 m3',
    );
    expect(formatQuantity(allocatedQuantity(register, parseItemCode('E532.1'))!)).toBe(
      '9800.00 m3',
    );
  });

  it('gives nothing for an item nothing is allocated against', () => {
    expect(allocatedQuantity(register, parseItemCode('F624.1'))).toBeNull();
  });
});

describe('reconcileWithBill', () => {
  const lines = reconcileWithBill(register, bill);

  it('reports every problem in bill order', () => {
    expect(lines.map((l) => formatItemCode(l.code))).toEqual(['A213.1', 'E423.1', 'F624.1']);
  });

  it('reports the earthworks item that is short of the billed quantity', () => {
    const short = lines.find((l) => formatItemCode(l.code) === 'E423.1');
    expect(short?.problem).toBe('under_allocated');
    expect(short?.message).toBe('E423.1 is allocated 12000.00 m3 of a billed 12400.00 m3');
  });

  it('reports items no package earns against', () => {
    const orphans = lines.filter((l) => l.problem === 'not_allocated');
    expect(orphans.map((l) => formatItemCode(l.code))).toEqual(['A213.1', 'F624.1']);
    expect(orphans[0]?.message).toMatch(/no package earns against it/);
  });

  it('leaves provisional items out, because nothing may earn against them yet', () => {
    expect(lines.some((l) => formatItemCode(l.code) === 'E423.9')).toBe(false);
  });

  it('says nothing about an item that balances exactly', () => {
    expect(lines.some((l) => formatItemCode(l.code) === 'E532.1')).toBe(false);
    expect(lines.some((l) => formatItemCode(l.code) === 'R712.1')).toBe(false);
  });

  it('reports over allocation', () => {
    const greedy = buildRegister([
      pkg({
        id: 'WP-10',
        from: '12+000',
        to: '12+800',
        allocations: [['E423.1', 20_000, 'm3']],
      }),
    ]);
    const over = reconcileWithBill(greedy, bill).find(
      (l) => formatItemCode(l.code) === 'E423.1',
    );
    expect(over?.problem).toBe('over_allocated');
    expect(over?.message).toBe('E423.1 is allocated 20000.00 m3 against a billed 12400.00 m3');
  });

  it('reports an allocation against an item the bill dropped', () => {
    const stale = buildRegister([
      pkg({ id: 'WP-11', from: '0+000', to: '0+100', allocations: [['Z999.1', 1, 'm3']] }),
    ]);
    const line = reconcileWithBill(stale, bill).find(
      (l) => formatItemCode(l.code) === 'Z999.1',
    );
    expect(line?.problem).toBe('unknown_item');
    expect(line?.billed).toBeNull();
  });
});

describe('uncoveredStretches', () => {
  it('finds the length beyond the last package', () => {
    const gaps = uncoveredStretches(register, chainageRange(at('12+000'), at('14+000')), 'A1');
    expect(gaps.map(formatChainageRange)).toEqual(['13+600.000 to 14+000.000']);
  });

  it('finds a hole in the middle', () => {
    const holed = buildRegister([
      pkg({ id: 'WP-20', from: '0+000', to: '0+100', allocations: [['E423.1', 1, 'm3']] }),
      pkg({ id: 'WP-21', from: '0+300', to: '0+400', allocations: [['E423.1', 1, 'm3']] }),
    ]);
    const gaps = uncoveredStretches(holed, chainageRange(at('0+000'), at('0+400')), 'A1');
    expect(gaps.map(formatChainageRange)).toEqual(['0+100.000 to 0+300.000']);
  });

  it('finds nothing when the packages cover the lot', () => {
    expect(
      uncoveredStretches(register, chainageRange(at('12+000'), at('13+600')), 'A1'),
    ).toEqual([]);
  });

  it('ignores packages on another alignment', () => {
    const gaps = uncoveredStretches(register, chainageRange(at('12+000'), at('13+600')), 'A2');
    expect(gaps.map(formatChainageRange)).toEqual(['12+000.000 to 13+600.000']);
  });

  it('measures the covered length once, counting overlap once', () => {
    expect(coveredExtent(register, 'A1')).toBe(1_600_000);
    expect(coveredExtent(register, 'A2')).toBe(0);
  });
});

describe('clashes', () => {
  it('finds nothing when packages that overlap share no item', () => {
    expect(clashes(register)).toEqual([]);
  });

  it('finds a pair that overlaps and shares an item', () => {
    const clashing = buildRegister([
      ...samplePackages(),
      pkg({
        id: 'WP-30',
        from: '12+400',
        to: '13+000',
        allocations: [['E423.1', 100, 'm3']],
      }),
    ]);
    const found = clashes(clashing).map(([a, b]) => `${a.id}/${b.id}`);
    expect(found).toEqual(['WP-01/WP-30', 'WP-30/WP-02']);
  });
});
