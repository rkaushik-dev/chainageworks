import { describe, expect, it } from 'vitest';
import { NotFoundError } from '../../../../src/errors.js';
import type { Bill } from '../../../../src/domain/bill/bill.js';
import {
  billIsEmpty,
  billItemCount,
  billTotal,
  buildBill,
  emptyBill,
  findItem,
  hasItem,
  itemsUnder,
  measuredTotal,
  provisionalTotal,
  requireItem,
  rollupByClass,
  totalUnder,
  withItem,
  withoutItem,
  zeroForBill,
} from '../../../../src/domain/bill/bill.js';
import { buildBillItem } from '../../../../src/domain/bill/billItem.js';
import { formatItemCode, parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import { moneyMajor } from '../../../../src/domain/money/money.js';
import { rate } from '../../../../src/domain/money/rate.js';
import { quantity } from '../../../../src/domain/quantity/quantity.js';
import { bill, item, sampleBill } from '../../../support/billFixtures.js';

const codes = (b: Bill) => b.items.map((i) => formatItemCode(i.code));

describe('buildBill', () => {
  it('sorts the items into bill order however they arrive', () => {
    expect(codes(sampleBill())).toEqual([
      'A213.1',
      'E423.1',
      'E423.2',
      'E423.9',
      'E532.1',
      'F624.1',
      'R712.1',
      'R712.2',
    ]);
  });

  it('refuses the same code twice', () => {
    expect(() =>
      bill([
        { code: 'E423.1', rate: 1, quantity: 1, unit: 'm3' },
        { code: 'E 4 2 3 . 1', rate: 2, quantity: 2, unit: 'm3' },
      ]),
    ).toThrow(/appears twice in one bill/);
  });

  it('refuses an item priced in another currency', () => {
    expect(() =>
      buildBill({
        id: 'B9',
        title: 'mixed',
        currency: 'GBP',
        items: [
          buildBillItem({
            code: parseItemCode('E423.1'),
            description: 'excavation',
            rate: rate(1, 'EUR', 'm3'),
            quantity: quantity(1, 'm3'),
          }),
        ],
      }),
    ).toThrow(/different currency from the bill/);
  });

  it('accepts an empty bill', () => {
    const empty = emptyBill('B0', 'nothing yet', 'GBP');
    expect(billIsEmpty(empty)).toBe(true);
    expect(billItemCount(empty)).toBe(0);
    expect(billTotal(empty).minor).toBe(0);
    expect(zeroForBill(empty)).toEqual({ minor: 0, currency: 'GBP' });
  });
});

describe('looking items up', () => {
  const b = sampleBill();

  it('finds an item by code', () => {
    expect(findItem(b, parseItemCode('E423.1'))?.description).toMatch(/2 to 5 m/);
    expect(hasItem(b, parseItemCode('E423.1'))).toBe(true);
  });

  it('gives nothing for a code the bill does not carry', () => {
    expect(findItem(b, parseItemCode('Z999.1'))).toBeNull();
    expect(hasItem(b, parseItemCode('Z999.1'))).toBe(false);
  });

  it('throws from the strict lookup', () => {
    expect(() => requireItem(b, parseItemCode('Z999.1'))).toThrow(NotFoundError);
    expect(() => requireItem(b, parseItemCode('Z999.1'))).toThrow(
      /no bill item with id Z999.1/,
    );
  });

  it('does not match a heading against an item', () => {
    expect(findItem(b, parseItemCode('E42'))).toBeNull();
  });
});

describe('itemsUnder', () => {
  const b = sampleBill();

  it('collects everything under a heading in bill order', () => {
    expect(itemsUnder(b, parseItemCode('E42')).map((i) => formatItemCode(i.code))).toEqual([
      'E423.1',
      'E423.2',
      'E423.9',
    ]);
  });

  it('collects a whole class', () => {
    expect(itemsUnder(b, parseItemCode('E'))).toHaveLength(4);
    expect(itemsUnder(b, parseItemCode('R'))).toHaveLength(2);
  });

  it('collects nothing for a heading nothing sits under', () => {
    expect(itemsUnder(b, parseItemCode('T'))).toEqual([]);
  });
});

describe('totals', () => {
  const b = sampleBill();

  it('adds every item', () => {
    expect(moneyMajor(billTotal(b))).toBe(312_190);
  });

  it('leaves provisional money out of the measured total', () => {
    expect(moneyMajor(provisionalTotal(b))).toBe(14_000);
    expect(moneyMajor(measuredTotal(b))).toBe(298_190);
  });

  it('totals under a heading', () => {
    expect(moneyMajor(totalUnder(b, parseItemCode('E42')))).toBe(91_420);
    expect(moneyMajor(totalUnder(b, parseItemCode('R')))).toBe(120_600);
    expect(moneyMajor(totalUnder(b, parseItemCode('T')))).toBe(0);
  });
});

describe('rollupByClass', () => {
  const rollup = rollupByClass(sampleBill());

  it('gives one line per class, in class order', () => {
    expect(rollup.map((line) => line.workClass)).toEqual(['A', 'E', 'F', 'R']);
  });

  it('names the class and counts the items', () => {
    expect(rollup[1]?.name).toBe('earthworks');
    expect(rollup[1]?.items).toBe(4);
    expect(rollup[0]?.items).toBe(1);
  });

  it('values each class', () => {
    expect(rollup.map((line) => moneyMajor(line.value))).toEqual([
      48_500, 122_290, 20_800, 120_600,
    ]);
  });

  it('adds back to the bill total', () => {
    const sum = rollup.reduce((total, line) => total + line.value.minor, 0);
    expect(sum).toBe(billTotal(sampleBill()).minor);
  });

  it('gives nothing for an empty bill', () => {
    expect(rollupByClass(emptyBill('B0', 'x', 'GBP'))).toEqual([]);
  });
});

describe('withItem and withoutItem', () => {
  const b = sampleBill();

  it('adds an item and keeps the order', () => {
    const extra = item({ code: 'E511.1', rate: 2.2, quantity: 100, unit: 'm3' });
    const next = withItem(b, extra);
    expect(billItemCount(next)).toBe(9);
    expect(codes(next)[4]).toBe('E511.1');
  });

  it('replaces an item with the same code rather than duplicating it', () => {
    const replacement = item({ code: 'E423.1', rate: 5, quantity: 12_400, unit: 'm3' });
    const next = withItem(b, replacement);
    expect(billItemCount(next)).toBe(8);
    expect(moneyMajor(billTotal(next))).toBe(312_190 - 60_140 + 62_000);
  });

  it('leaves the original bill alone', () => {
    withItem(b, item({ code: 'E511.1', rate: 2.2, quantity: 100, unit: 'm3' }));
    expect(billItemCount(b)).toBe(8);
  });

  it('removes an item', () => {
    const next = withoutItem(b, parseItemCode('E423.9'));
    expect(billItemCount(next)).toBe(7);
    expect(moneyMajor(provisionalTotal(next))).toBe(0);
  });

  it('refuses to remove an item that is not there', () => {
    expect(() => withoutItem(b, parseItemCode('Z999.1'))).toThrow(NotFoundError);
  });
});
