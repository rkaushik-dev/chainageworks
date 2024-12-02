import { describe, expect, it } from 'vitest';
import { ConflictError, NotFoundError } from '../../../../src/errors.js';
import { billTotal, findItem, requireItem } from '../../../../src/domain/bill/bill.js';
import { formatItemCode, parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import { moneyMajor } from '../../../../src/domain/money/money.js';
import { rate, rateMajor } from '../../../../src/domain/money/rate.js';
import { formatQuantity, quantity } from '../../../../src/domain/quantity/quantity.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';
import { applyVariations, variationValue } from '../../../../src/domain/variation/apply.js';
import type { VariationInput } from '../../../../src/domain/variation/variation.js';
import { buildVariation } from '../../../../src/domain/variation/variation.js';
import { sampleBill } from '../../../support/billFixtures.js';

const d = parseSiteDate;
const bill = sampleBill();
const asAt = d('2025-03-31');

const variation = (id: string, input: Partial<VariationInput>) =>
  buildVariation({
    id,
    reference: 'EI-045',
    title: 'deepen the cutting',
    status: 'instructed',
    instructedOn: d('2025-03-14'),
    items: [],
    ...input,
  });

describe('applying a change at bill rates', () => {
  const deepening = variation('VO-012', {
    items: [
      {
        code: parseItemCode('E423.1'),
        quantityChange: quantity(1400, 'm3'),
        rate: null,
        basis: 'bill_rates',
      },
    ],
  });

  it('moves the quantity and leaves the rate alone', () => {
    const applied = applyVariations(bill, [deepening], asAt);
    const item = requireItem(applied.bill, parseItemCode('E423.1'));
    expect(formatQuantity(item.quantity)).toBe('13800.00 m3');
    expect(rateMajor(item.rate)).toBe(4.85);
  });

  it('is worth the change at the bill rate', () => {
    expect(moneyMajor(variationValue(bill, [deepening], asAt))).toBe(6790);
  });

  it('reports the change as a quantity move', () => {
    const applied = applyVariations(bill, [deepening], asAt);
    expect(applied.changes.map((c) => c.kind)).toEqual(['quantity_changed']);
    expect(applied.applied).toEqual(['VO-012']);
  });

  it('takes an omission off', () => {
    const omission = variation('VO-013', {
      items: [
        {
          code: parseItemCode('E532.1'),
          quantityChange: quantity(-800, 'm3'),
          rate: null,
          basis: 'bill_rates',
        },
      ],
    });
    expect(moneyMajor(variationValue(bill, [omission], asAt))).toBe(-2520);
  });

  it('refuses an omission larger than the item', () => {
    const wipe = variation('VO-014', {
      items: [
        {
          code: parseItemCode('E532.1'),
          quantityChange: quantity(-99_999, 'm3'),
          rate: null,
          basis: 'bill_rates',
        },
      ],
    });
    expect(() => applyVariations(bill, [wipe], asAt)).toThrow(ConflictError);
  });

  it('refuses a bill rate change against an item the bill does not carry', () => {
    const stray = variation('VO-015', {
      items: [
        {
          code: parseItemCode('Z999.1'),
          quantityChange: quantity(1, 'm3'),
          rate: null,
          basis: 'bill_rates',
        },
      ],
    });
    expect(() => applyVariations(bill, [stray], asAt)).toThrow(NotFoundError);
  });
});

describe('applying a star rate', () => {
  const handDig = variation('VO-020', {
    title: 'hand excavation around live services',
    items: [
      {
        code: parseItemCode('X400.1'),
        quantityChange: quantity(40, 'm3'),
        rate: rate(94, 'GBP', 'm3'),
        basis: 'star_rate',
      },
    ],
  });

  it('creates the item the bill never carried', () => {
    const applied = applyVariations(bill, [handDig], asAt);
    const item = requireItem(applied.bill, parseItemCode('X400.1'));
    expect(item.kind).toBe('star');
    expect(rateMajor(item.rate)).toBe(94);
    expect(item.description).toBe('hand excavation around live services (EI-045)');
  });

  it('is worth the new item in full', () => {
    expect(moneyMajor(variationValue(bill, [handDig], asAt))).toBe(3760);
  });

  it('reprices an item the bill already had', () => {
    const reprice = variation('VO-021', {
      items: [
        {
          code: parseItemCode('E423.2'),
          quantityChange: quantity(0, 'm3'),
          rate: rate(7.2, 'GBP', 'm3'),
          basis: 'pro_rata',
        },
      ],
    });
    const applied = applyVariations(bill, [reprice], asAt);
    const item = requireItem(applied.bill, parseItemCode('E423.2'));
    expect(rateMajor(item.rate)).toBe(7.2);
    expect(item.kind).toBe('star');
    expect(moneyMajor(applied.netValue)).toBe(5760);
  });

  it('refuses to omit an item the bill does not carry', () => {
    const nonsense = variation('VO-022', {
      items: [
        {
          code: parseItemCode('X400.9'),
          quantityChange: quantity(-5, 'm3'),
          rate: rate(1, 'GBP', 'm3'),
          basis: 'star_rate',
        },
      ],
    });
    expect(() => applyVariations(bill, [nonsense], asAt)).toThrow(/cannot omit an item/);
  });

  it('creates a lump item when the change is a sum', () => {
    const settlement = variation('VO-023', {
      title: 'agreed lump sum for the temporary diversion',
      items: [
        {
          code: parseItemCode('X410.1'),
          quantityChange: quantity(1, 'sum'),
          rate: rate(22_500, 'GBP', 'sum'),
          basis: 'lump_sum',
        },
      ],
    });
    const applied = applyVariations(bill, [settlement], asAt);
    expect(requireItem(applied.bill, parseItemCode('X410.1')).kind).toBe('lump');
    expect(moneyMajor(applied.netValue)).toBe(22_500);
  });
});

describe('instructing a provisional item', () => {
  const instruct = variation('VO-030', {
    title: 'rock excavation instructed',
    items: [
      {
        code: parseItemCode('E423.9'),
        quantityChange: quantity(0, 'm3'),
        rate: null,
        basis: 'bill_rates',
      },
    ],
  });

  it('turns it into measured money without moving a figure', () => {
    expect(findItem(bill, parseItemCode('E423.9'))?.kind).toBe('provisional');
    const applied = applyVariations(bill, [instruct], asAt);
    const item = requireItem(applied.bill, parseItemCode('E423.9'));
    expect(item.kind).toBe('measured');
    expect(formatQuantity(item.quantity)).toBe('500.00 m3');
    expect(applied.netValue.minor).toBe(0);
  });

  it('shows up as a kind change and nothing else', () => {
    const applied = applyVariations(bill, [instruct], asAt);
    expect(applied.changes.map((c) => c.kind)).toEqual(['kind_changed']);
  });
});

describe('what is in force', () => {
  const early = variation('VO-040', {
    instructedOn: d('2025-02-01'),
    items: [
      {
        code: parseItemCode('E423.1'),
        quantityChange: quantity(100, 'm3'),
        rate: null,
        basis: 'bill_rates',
      },
    ],
  });
  const late = variation('VO-041', {
    instructedOn: d('2025-05-01'),
    items: [
      {
        code: parseItemCode('E423.1'),
        quantityChange: quantity(200, 'm3'),
        rate: null,
        basis: 'bill_rates',
      },
    ],
  });
  const proposal = variation('VO-042', {
    status: 'proposed',
    instructedOn: null,
    items: [
      {
        code: parseItemCode('E423.1'),
        quantityChange: quantity(400, 'm3'),
        rate: null,
        basis: 'bill_rates',
      },
    ],
  });

  it('applies only the ones instructed by the date', () => {
    const applied = applyVariations(bill, [early, late, proposal], asAt);
    expect(applied.applied).toEqual(['VO-040']);
    expect(moneyMajor(applied.netValue)).toBe(485);
  });

  it('applies them in instruction order and stacks the effects', () => {
    const applied = applyVariations(bill, [early, late, proposal], d('2025-05-31'));
    expect(applied.applied).toEqual(['VO-040', 'VO-041']);
    expect(moneyMajor(applied.netValue)).toBe(1455);
  });

  it('leaves the bill alone when nothing is in force', () => {
    const applied = applyVariations(bill, [proposal], asAt);
    expect(applied.changes).toEqual([]);
    expect(moneyMajor(billTotal(applied.bill))).toBe(moneyMajor(billTotal(bill)));
  });

  it('does not mutate the bill it was given', () => {
    applyVariations(bill, [early], asAt);
    expect(formatQuantity(requireItem(bill, parseItemCode('E423.1')).quantity)).toBe(
      '12400.00 m3',
    );
  });

  it('reports every touched code once', () => {
    const applied = applyVariations(bill, [early, late], d('2025-05-31'));
    expect(applied.changes.map((c) => formatItemCode(c.code))).toEqual(['E423.1']);
  });
});
