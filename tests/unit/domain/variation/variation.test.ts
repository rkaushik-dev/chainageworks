import { describe, expect, it } from 'vitest';
import { parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import { chainageRange } from '../../../../src/domain/chainage/range.js';
import { parseStation } from '../../../../src/domain/chainage/station.js';
import { rate } from '../../../../src/domain/money/rate.js';
import { quantity } from '../../../../src/domain/quantity/quantity.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';
import type { VariationInput } from '../../../../src/domain/variation/variation.js';
import {
  buildVariation,
  describeVariation,
  effectiveVariations,
  isAgreed,
  isEffectiveAt,
  isUnagreed,
  itemsFor,
} from '../../../../src/domain/variation/variation.js';

const d = parseSiteDate;

const base: VariationInput = {
  id: 'VO-012',
  reference: 'EI-045',
  title: 'deepen the cutting between 12+400 and 12+600',
  status: 'instructed',
  instructedOn: d('2025-03-14'),
  items: [
    {
      code: parseItemCode('E423.1'),
      quantityChange: quantity(1400, 'm3'),
      rate: null,
      basis: 'bill_rates',
    },
  ],
  extent: chainageRange(parseStation('12+400'), parseStation('12+600')),
};

const deepening = buildVariation(base);

describe('buildVariation', () => {
  it('defaults to proposed with no dates', () => {
    const proposal = buildVariation({ ...base, status: 'proposed', instructedOn: null });
    expect(proposal.status).toBe('proposed');
    expect(proposal.instructedOn).toBeNull();
    expect(proposal.agreedOn).toBeNull();
  });

  it('refuses an instructed variation with no instruction date', () => {
    expect(() => buildVariation({ ...base, instructedOn: null })).toThrow(
      /needs the date it was instructed/,
    );
  });

  it('refuses an agreed variation with no agreement date', () => {
    expect(() => buildVariation({ ...base, status: 'agreed' })).toThrow(
      /needs the date it was agreed/,
    );
  });

  it('refuses agreement before instruction', () => {
    expect(() =>
      buildVariation({ ...base, status: 'agreed', agreedOn: d('2025-03-01') }),
    ).toThrow(/agreed before it was instructed/);
  });

  it('refuses a variation that changes nothing', () => {
    expect(() => buildVariation({ ...base, items: [] })).toThrow(/is not a variation/);
  });

  it('refuses the same item twice', () => {
    expect(() =>
      buildVariation({
        ...base,
        items: [base.items[0]!, { ...base.items[0]!, code: parseItemCode('E 4 2 3 . 1') }],
      }),
    ).toThrow(/same item twice/);
  });

  it('refuses a bill rate item that carries its own rate', () => {
    expect(() =>
      buildVariation({
        ...base,
        items: [{ ...base.items[0]!, rate: rate(5, 'GBP', 'm3') }],
      }),
    ).toThrow(/cannot carry a rate of its own/);
  });

  it('refuses a star rate item with no rate', () => {
    expect(() =>
      buildVariation({
        ...base,
        items: [{ ...base.items[0]!, basis: 'star_rate' }],
      }),
    ).toThrow(/needs its own rate/);
  });

  it('refuses a rate in a different unit from the change', () => {
    expect(() =>
      buildVariation({
        ...base,
        items: [{ ...base.items[0]!, basis: 'star_rate', rate: rate(5, 'GBP', 'm2') }],
      }),
    ).toThrow(/different units/);
  });

  it('accepts an omission as a negative change', () => {
    const omission = buildVariation({
      ...base,
      id: 'VO-013',
      items: [
        {
          code: parseItemCode('E532.1'),
          quantityChange: quantity(-800, 'm3'),
          rate: null,
          basis: 'bill_rates',
        },
      ],
    });
    expect(omission.items[0]?.quantityChange.milli).toBe(-800_000);
  });

  it('accepts a star rate for work the bill never described', () => {
    const star = buildVariation({
      ...base,
      id: 'VO-014',
      items: [
        {
          code: parseItemCode('X400.1'),
          quantityChange: quantity(40, 'm3'),
          rate: rate(94, 'GBP', 'm3'),
          basis: 'star_rate',
        },
      ],
    });
    expect(star.items[0]?.basis).toBe('star_rate');
  });
});

describe('isEffectiveAt', () => {
  it('is false for a proposal however old', () => {
    const proposal = buildVariation({ ...base, status: 'proposed', instructedOn: null });
    expect(isEffectiveAt(proposal, d('2026-01-01'))).toBe(false);
  });

  it('is false for a rejected variation', () => {
    const rejected = buildVariation({ ...base, status: 'rejected' });
    expect(isEffectiveAt(rejected, d('2026-01-01'))).toBe(false);
  });

  it('is true from the day it was instructed', () => {
    expect(isEffectiveAt(deepening, d('2025-03-13'))).toBe(false);
    expect(isEffectiveAt(deepening, d('2025-03-14'))).toBe(true);
  });

  it('counts from instruction and not from agreement', () => {
    const late = buildVariation({ ...base, status: 'agreed', agreedOn: d('2025-06-20') });
    expect(isEffectiveAt(late, d('2025-03-31'))).toBe(true);
  });

  it('separates agreed from merely instructed', () => {
    expect(isUnagreed(deepening)).toBe(true);
    expect(isAgreed(deepening)).toBe(false);
    const settled = buildVariation({ ...base, status: 'agreed', agreedOn: d('2025-06-20') });
    expect(isAgreed(settled)).toBe(true);
    expect(isUnagreed(settled)).toBe(false);
  });
});

describe('effectiveVariations', () => {
  const later = buildVariation({ ...base, id: 'VO-020', instructedOn: d('2025-04-02') });
  const proposal = buildVariation({
    ...base,
    id: 'VO-021',
    status: 'proposed',
    instructedOn: null,
  });

  it('takes the ones in force, in the order they were instructed', () => {
    const all = [later, deepening, proposal];
    expect(effectiveVariations(all, d('2025-04-30')).map((v) => v.id)).toEqual([
      'VO-012',
      'VO-020',
    ]);
  });

  it('leaves out anything instructed after the date', () => {
    expect(effectiveVariations([later, deepening], d('2025-03-31')).map((v) => v.id)).toEqual([
      'VO-012',
    ]);
  });

  it('gives nothing when none are in force', () => {
    expect(effectiveVariations([proposal], d('2026-01-01'))).toEqual([]);
  });
});

describe('itemsFor and describeVariation', () => {
  it('finds the change against an item', () => {
    expect(itemsFor(deepening, parseItemCode('E423.1'))?.basis).toBe('bill_rates');
    expect(itemsFor(deepening, parseItemCode('R712.1'))).toBeNull();
  });

  it('reads like a variation register line', () => {
    expect(describeVariation(deepening)).toBe(
      'VO-012 deepen the cutting between 12+400 and 12+600 (EI-045, instructed 2025-03-14)',
    );
  });

  it('says so when it has not been instructed', () => {
    const proposal = buildVariation({ ...base, status: 'proposed', instructedOn: null });
    expect(describeVariation(proposal)).toContain('not instructed');
  });
});
