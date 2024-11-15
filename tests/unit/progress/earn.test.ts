import { describe, expect, it } from 'vitest';
import { NotFoundError } from '../../../src/errors.js';
import { formatItemCode } from '../../../src/domain/bill/itemCode.js';
import { moneyMajor } from '../../../src/domain/money/money.js';
import { percentValue } from '../../../src/domain/money/percent.js';
import { buildRegister, requirePackage } from '../../../src/domain/packages/register.js';
import { formatQuantity, quantity } from '../../../src/domain/quantity/quantity.js';
import {
  cappedAllocations,
  earnAll,
  earnPackage,
  findEarning,
  totalEarned,
} from '../../../src/progress/earn.js';
import { pkg } from '../../support/packageFixtures.js';
import { d, sampleContext, sampleRecords, signOff } from '../../support/contextFixtures.js';

const context = sampleContext();
const wp01 = requirePackage(context.register, 'WP-01');
const cutOff = d('2025-03-31');

describe('earnPackage', () => {
  const earning = earnPackage(context, wp01, cutOff);

  it('earns the fraction the rule of credit worked out', () => {
    expect(percentValue(earning.fraction)).toBe(32.5);
  });

  it('carries the step detail through for the report', () => {
    expect(earning.steps.map((s) => s.stepId)).toEqual(['strip', 'excavate', 'place', 'trim']);
  });

  it('takes that fraction of each allocated quantity', () => {
    expect(earning.allocations.map((a) => formatQuantity(a.earnedQuantity))).toEqual([
      '2275.00 m3',
      '1625.00 m3',
    ]);
  });

  it('prices it at the bill rates in force on the day', () => {
    expect(earning.allocations.map((a) => moneyMajor(a.earnedValue))).toEqual([
      11_033.75, 5118.75,
    ]);
    expect(moneyMajor(earning.earnedValue)).toBe(16_152.5);
  });

  it('reports the budget the package was set', () => {
    expect(moneyMajor(earning.budget)).toBe(49_700);
  });

  it('earns nothing before anything was built', () => {
    const early = earnPackage(context, wp01, d('2025-02-28'));
    expect(percentValue(early.fraction)).toBe(0);
    expect(early.earnedValue.minor).toBe(0);
  });
});

describe('the measurement cap', () => {
  it('does not bite while the survey book is ahead of the credit', () => {
    const earning = earnPackage(context, wp01, cutOff);
    expect(cappedAllocations(earning)).toEqual([]);
  });

  it('holds the claim down to what was actually measured', () => {
    const thin = sampleContext({
      records: sampleRecords().map((r) =>
        r.id === 'SR-03' ? { ...r, quantity: quantity(900, 'm3') } : r,
      ),
    });
    const earning = earnPackage(thin, wp01, cutOff);
    const fill = earning.allocations.find((a) => formatItemCode(a.code) === 'E532.1');
    expect(fill?.cappedByMeasurement).toBe(true);
    expect(formatQuantity(fill!.earnedQuantity)).toBe('900.00 m3');
    expect(moneyMajor(fill!.earnedValue)).toBe(2835);
  });

  it('never earns more than the survey book supports', () => {
    const nothing = sampleContext({ records: [] });
    const earning = earnPackage(nothing, wp01, cutOff);
    expect(earning.earnedValue.minor).toBe(0);
  });
});

describe('items that cannot earn', () => {
  it('earns nothing against a provisional item however much was built', () => {
    const provisional = buildRegister([
      pkg({
        id: 'WP-40',
        from: '12+000',
        to: '12+800',
        profileId: 'bulk-earthworks',
        allocations: [['E423.9', 500, 'm3']],
      }),
    ]);
    const ctx = sampleContext();
    const earning = earnPackage(
      { ...ctx, register: provisional },
      requirePackage(provisional, 'WP-40'),
      cutOff,
    );
    expect(earning.allocations[0]?.notEarnable).toBe(true);
    expect(earning.earnedValue.minor).toBe(0);
  });

  it('lets a lump sum earn on the rule of credit alone, with nothing measured', () => {
    const lump = buildRegister([
      pkg({
        id: 'WP-41',
        from: '12+000',
        to: '12+800',
        profileId: 'bulk-earthworks',
        allocations: [['A213.1', 1, 'sum']],
      }),
    ]);
    const ctx = sampleContext();
    const earning = earnPackage(
      {
        ...ctx,
        register: lump,
        records: sampleRecords().map((r) => ({ ...r, packageId: 'WP-41' })),
      },
      requirePackage(lump, 'WP-41'),
      cutOff,
    );
    expect(percentValue(earning.fraction)).toBe(10);
    expect(moneyMajor(earning.earnedValue)).toBe(4850);
  });

  it('refuses an allocation the bill does not carry on that date', () => {
    const stray = buildRegister([
      pkg({ id: 'WP-42', from: '0+000', to: '0+100', allocations: [['Z999.1', 1, 'm3']] }),
    ]);
    const ctx = sampleContext();
    expect(() =>
      earnPackage({ ...ctx, register: stray }, requirePackage(stray, 'WP-42'), cutOff),
    ).toThrow(NotFoundError);
  });
});

describe('earnAll', () => {
  const all = earnAll(context, cutOff);

  it('covers every package in the register', () => {
    expect(all.map((e) => e.packageId)).toEqual(['WP-01', 'WP-03', 'WP-02']);
  });

  it('earns nothing on the packages nothing was booked against', () => {
    expect(findEarning(all, 'WP-02')?.earnedValue.minor).toBe(0);
    expect(findEarning(all, 'WP-03')?.earnedValue.minor).toBe(0);
    expect(findEarning(all, 'WP-99')).toBeNull();
  });

  it('adds to the whole job total', () => {
    expect(moneyMajor(totalEarned(all, context, cutOff))).toBe(16_152.5);
  });
});

describe('the cut off decides the answer', () => {
  it('reads more as the sign offs arrive', () => {
    const later = sampleContext({
      signOffs: [
        ...sampleContext().signOffs,
        signOff('SO-03', 'IP-E1', 'WP-01', '12+400', '12+800', '2025-04-04'),
      ],
    });
    const march = earnPackage(later, wp01, cutOff);
    const april = earnPackage(later, wp01, d('2025-04-30'));
    expect(percentValue(march.fraction)).toBe(32.5);
    expect(percentValue(april.fraction)).toBeGreaterThan(32.5);
  });

  it('does not let an april sign off change the march certificate', () => {
    const later = sampleContext({
      signOffs: [
        ...sampleContext().signOffs,
        signOff('SO-03', 'IP-E1', 'WP-01', '12+400', '12+800', '2025-04-04'),
      ],
    });
    expect(moneyMajor(earnPackage(later, wp01, cutOff).earnedValue)).toBe(16_152.5);
  });
});
