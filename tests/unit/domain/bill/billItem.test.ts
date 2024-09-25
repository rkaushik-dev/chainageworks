import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import {
  billedValue,
  buildBillItem,
  describeBillItem,
  isEarnable,
  isLumpItem,
  isMeasuredItem,
  isNilQuantityItem,
  isProvisionalItem,
  reQuantify,
} from '../../../../src/domain/bill/billItem.js';
import { parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import { chainageRange } from '../../../../src/domain/chainage/range.js';
import { parseStation } from '../../../../src/domain/chainage/station.js';
import { moneyMajor } from '../../../../src/domain/money/money.js';
import { rate } from '../../../../src/domain/money/rate.js';
import { quantity } from '../../../../src/domain/quantity/quantity.js';

const excavation = buildBillItem({
  code: parseItemCode('E423.1'),
  description: 'excavation for cuttings, maximum depth 2 to 5 m',
  rate: rate(4.85, 'GBP', 'm3'),
  quantity: quantity(12_400, 'm3'),
});

describe('buildBillItem', () => {
  it('takes the unit from the billed quantity', () => {
    expect(excavation.unit).toBe('m3');
    expect(excavation.kind).toBe('measured');
    expect(excavation.chainage).toBeNull();
  });

  it('trims the description', () => {
    const item = buildBillItem({
      code: parseItemCode('E423.2'),
      description: '  disposal of excavated material  ',
      rate: rate(3.1, 'GBP', 'm3'),
      quantity: quantity(100, 'm3'),
    });
    expect(item.description).toBe('disposal of excavated material');
  });

  it('keeps a chainage range when the bill gives one', () => {
    const item = buildBillItem({
      code: parseItemCode('R712.1'),
      description: 'sub base, depth 225 mm',
      rate: rate(11.4, 'GBP', 'm2'),
      quantity: quantity(4000, 'm2'),
      chainage: chainageRange(parseStation('12+000'), parseStation('12+800')),
    });
    expect(item.chainage).not.toBeNull();
  });

  it('refuses a rate in a different unit from the quantity', () => {
    expect(() =>
      buildBillItem({
        code: parseItemCode('E423.1'),
        description: 'excavation',
        rate: rate(4.85, 'GBP', 'm2'),
        quantity: quantity(100, 'm3'),
      }),
    ).toThrow(/different units/);
  });

  it('refuses an empty description', () => {
    expect(() =>
      buildBillItem({
        code: parseItemCode('E423.1'),
        description: '   ',
        rate: rate(1, 'GBP', 'm3'),
        quantity: quantity(1, 'm3'),
      }),
    ).toThrow(/needs a description/);
  });

  it('refuses a negative billed quantity', () => {
    expect(() =>
      buildBillItem({
        code: parseItemCode('E423.1'),
        description: 'excavation',
        rate: rate(1, 'GBP', 'm3'),
        quantity: quantity(-1, 'm3'),
      }),
    ).toThrow(/cannot be negative/);
  });

  it('allows a nil quantity item, which a bill often carries', () => {
    const nil = buildBillItem({
      code: parseItemCode('E423.9'),
      description: 'rock excavation, provisional',
      rate: rate(28, 'GBP', 'm3'),
      quantity: quantity(0, 'm3'),
      kind: 'provisional',
    });
    expect(isNilQuantityItem(nil)).toBe(true);
    expect(billedValue(nil).minor).toBe(0);
  });

  it('refuses a lump item measured in anything but a sum', () => {
    expect(() =>
      buildBillItem({
        code: parseItemCode('A213.1'),
        description: 'establish site',
        rate: rate(50_000, 'GBP', 'm3'),
        quantity: quantity(1, 'm3'),
        kind: 'lump',
      }),
    ).toThrow(/billed as a sum/);
  });

  it('refuses a sum item claiming to be measured', () => {
    expect(() =>
      buildBillItem({
        code: parseItemCode('A213.1'),
        description: 'establish site',
        rate: rate(50_000, 'GBP', 'sum'),
        quantity: quantity(1, 'sum'),
        kind: 'measured',
      }),
    ).toThrow(/lump or a prime cost item/);
  });

  it('accepts a lump sum item', () => {
    const lump = buildBillItem({
      code: parseItemCode('A213.1'),
      description: 'establish and remove site accommodation',
      rate: rate(48_500, 'GBP', 'sum'),
      quantity: quantity(1, 'sum'),
      kind: 'lump',
    });
    expect(isLumpItem(lump)).toBe(true);
    expect(moneyMajor(billedValue(lump))).toBe(48_500);
  });
});

describe('billedValue', () => {
  it('is the rate times the billed quantity', () => {
    expect(moneyMajor(billedValue(excavation))).toBe(60_140);
  });

  it('keeps the currency of the rate', () => {
    expect(billedValue(excavation).currency).toBe('GBP');
  });
});

describe('item kinds', () => {
  const provisional = buildBillItem({
    code: parseItemCode('E423.9'),
    description: 'rock excavation',
    rate: rate(28, 'GBP', 'm3'),
    quantity: quantity(500, 'm3'),
    kind: 'provisional',
  });

  const primeCost = buildBillItem({
    code: parseItemCode('A253.1'),
    description: 'prime cost item, statutory undertaker diversion',
    rate: rate(120_000, 'GBP', 'sum'),
    quantity: quantity(1, 'sum'),
    kind: 'prime_cost',
  });

  const star = buildBillItem({
    code: parseItemCode('X400.1'),
    description: 'agreed rate for hand excavation around services',
    rate: rate(94, 'GBP', 'm3'),
    quantity: quantity(40, 'm3'),
    kind: 'star',
  });

  it('treats measured and star items as measured', () => {
    expect(isMeasuredItem(excavation)).toBe(true);
    expect(isMeasuredItem(star)).toBe(true);
    expect(isMeasuredItem(provisional)).toBe(false);
  });

  it('treats provisional and prime cost as not yet earnable', () => {
    expect(isProvisionalItem(provisional)).toBe(true);
    expect(isProvisionalItem(primeCost)).toBe(true);
    expect(isEarnable(provisional)).toBe(false);
    expect(isEarnable(primeCost)).toBe(false);
  });

  it('treats a measured and a lump item as earnable', () => {
    expect(isEarnable(excavation)).toBe(true);
    expect(isEarnable(star)).toBe(true);
  });
});

describe('describeBillItem', () => {
  it('reads like a bill line', () => {
    expect(describeBillItem(excavation)).toBe(
      'E423.1 excavation for cuttings, maximum depth 2 to 5 m 12400.00 m3 at £4.85/m3',
    );
  });

  it('shows the chainage when the item has one', () => {
    const item = buildBillItem({
      code: parseItemCode('R712.1'),
      description: 'sub base',
      rate: rate(11.4, 'GBP', 'm2'),
      quantity: quantity(4000, 'm2'),
      chainage: chainageRange(parseStation('12+000'), parseStation('12+800')),
    });
    expect(describeBillItem(item)).toContain('[12+000.000 to 12+800.000]');
  });
});

describe('reQuantify', () => {
  it('makes a copy at a new billed quantity', () => {
    const revised = reQuantify(excavation, 13_000);
    expect(revised.quantity.milli).toBe(13_000_000);
    expect(revised.code).toEqual(excavation.code);
    expect(revised.rate).toEqual(excavation.rate);
    expect(excavation.quantity.milli).toBe(12_400_000);
  });

  it('still refuses a negative quantity', () => {
    expect(() => reQuantify(excavation, -1)).toThrow(BadInputError);
  });
});
