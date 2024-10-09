import { describe, expect, it } from 'vitest';
import { buildBill, withItem, withoutItem } from '../../../../src/domain/bill/bill.js';
import {
  changesOfKind,
  describeChange,
  diffBills,
  hasRateChanges,
  isEmptyDiff,
} from '../../../../src/domain/bill/diff.js';
import { formatItemCode, parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import { moneyMajor } from '../../../../src/domain/money/money.js';
import { item, sampleBill } from '../../../support/billFixtures.js';

const base = sampleBill();

describe('diffBills', () => {
  it('reports nothing when the bills match', () => {
    const diff = diffBills(base, sampleBill());
    expect(isEmptyDiff(diff)).toBe(true);
    expect(diff.netDelta.minor).toBe(0);
  });

  it('reports a rate change and what it is worth', () => {
    const after = withItem(
      base,
      item({ code: 'E423.1', rate: 5.2, quantity: 12_400, unit: 'm3' }),
    );
    const diff = diffBills(base, after);
    expect(diff.changes).toHaveLength(1);
    expect(diff.changes[0]?.kind).toBe('rate_changed');
    expect(moneyMajor(diff.netDelta)).toBe(64_480 - 60_140);
    expect(hasRateChanges(diff)).toBe(true);
  });

  it('reports a quantity change on its own', () => {
    const after = withItem(
      base,
      item({ code: 'E423.1', rate: 4.85, quantity: 13_000, unit: 'm3' }),
    );
    const diff = diffBills(base, after);
    expect(diff.changes[0]?.kind).toBe('quantity_changed');
    expect(hasRateChanges(diff)).toBe(false);
  });

  it('reports a rate and quantity move as one change, not two', () => {
    const after = withItem(
      base,
      item({ code: 'E423.1', rate: 5.2, quantity: 13_000, unit: 'm3' }),
    );
    const diff = diffBills(base, after);
    expect(diff.changes).toHaveLength(1);
    expect(diff.changes[0]?.kind).toBe('rate_and_quantity_changed');
    expect(hasRateChanges(diff)).toBe(true);
  });

  it('reports an added item at its full value', () => {
    const after = withItem(base, item({ code: 'X400.1', rate: 94, quantity: 40, unit: 'm3' }));
    const diff = diffBills(base, after);
    expect(changesOfKind(diff, 'added')).toHaveLength(1);
    expect(moneyMajor(diff.netDelta)).toBe(3760);
    expect(diff.changes[0]?.before).toBeNull();
  });

  it('reports a removed item as a negative', () => {
    const after = withoutItem(base, parseItemCode('E423.9'));
    const diff = diffBills(base, after);
    expect(changesOfKind(diff, 'removed')).toHaveLength(1);
    expect(moneyMajor(diff.netDelta)).toBe(-14_000);
    expect(diff.changes[0]?.after).toBeNull();
  });

  it('reports a description change with no money attached', () => {
    const after = withItem(
      base,
      item({
        code: 'E423.1',
        description: 'excavation for cuttings, revised wording',
        rate: 4.85,
        quantity: 12_400,
        unit: 'm3',
      }),
    );
    const diff = diffBills(base, after);
    expect(diff.changes[0]?.kind).toBe('description_changed');
    expect(diff.netDelta.minor).toBe(0);
  });

  it('reports an item that became provisional', () => {
    const after = withItem(
      base,
      item({
        code: 'E423.1',
        description: 'excavation for cuttings, maximum depth 2 to 5 m',
        rate: 4.85,
        quantity: 12_400,
        unit: 'm3',
        kind: 'provisional',
      }),
    );
    const diff = diffBills(base, after);
    expect(diff.changes[0]?.kind).toBe('kind_changed');
  });

  it('reports several changes in bill order', () => {
    let after = withItem(
      base,
      item({ code: 'E423.1', rate: 5.2, quantity: 12_400, unit: 'm3' }),
    );
    after = withoutItem(after, parseItemCode('R712.2'));
    after = withItem(after, item({ code: 'X400.1', rate: 94, quantity: 40, unit: 'm3' }));
    const diff = diffBills(base, after);
    expect(diff.changes.map((c) => formatItemCode(c.code))).toEqual([
      'E423.1',
      'R712.2',
      'X400.1',
    ]);
    expect(moneyMajor(diff.netDelta)).toBe(4340 - 75_000 + 3760);
  });

  it('refuses two bills in different currencies', () => {
    const euro = buildBill({ id: 'B2', title: 'euro bill', currency: 'EUR', items: [] });
    expect(() => diffBills(base, euro)).toThrow(/different currencies/);
  });
});

describe('describeChange', () => {
  it('reads the change kind as words', () => {
    const after = withItem(
      base,
      item({ code: 'E423.1', rate: 5.2, quantity: 13_000, unit: 'm3' }),
    );
    const diff = diffBills(base, after);
    expect(describeChange(diff.changes[0]!)).toBe('E423.1 rate and quantity changed');
  });
});
