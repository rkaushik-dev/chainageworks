import { describe, expect, it } from 'vitest';
import {
  adjustmentFactor,
  buildAdjustmentTerms,
  describeAdjustment,
  factorPercent,
  noAdjustment,
  priceAdjustment,
} from '../../../../src/domain/fluctuation/adjustment.js';
import { buildIndexSeries } from '../../../../src/domain/fluctuation/index.js';
import { moneyFromMajor, moneyMajor } from '../../../../src/domain/money/money.js';
import { percent, percentValue } from '../../../../src/domain/money/percent.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';

const d = parseSiteDate;
const gbp = (major: number) => moneyFromMajor(major, 'GBP');

const reading = (year: number, month: number, value: number, provisional = false) => ({
  year,
  month,
  value,
  provisional,
});

const labour = buildIndexSeries({
  id: 'IDX-LAB',
  name: 'civil engineering labour',
  readings: [reading(2024, 10, 100), reading(2025, 1, 110), reading(2025, 3, 120, true)],
});

const plant = buildIndexSeries({
  id: 'IDX-PLA',
  name: 'plant hire',
  readings: [reading(2024, 10, 200), reading(2025, 1, 210), reading(2025, 3, 205)],
});

const series = [labour, plant];

const terms = buildAdjustmentTerms({
  baseDate: d('2024-10-25'),
  nonAdjustable: percent(10),
  weightings: [
    { indexId: 'IDX-LAB', weight: percent(60) },
    { indexId: 'IDX-PLA', weight: percent(30) },
  ],
});

describe('buildAdjustmentTerms', () => {
  it('takes a set that comes to the whole price', () => {
    expect(terms.weightings).toHaveLength(2);
    expect(percentValue(terms.nonAdjustable)).toBe(10);
  });

  it('takes no fixed element as none at all', () => {
    const none = buildAdjustmentTerms({
      baseDate: d('2024-10-25'),
      weightings: [{ indexId: 'IDX-LAB', weight: percent(100) }],
    });
    expect(percentValue(none.nonAdjustable)).toBe(0);
  });

  it('refuses a set that does not come to a hundred', () => {
    expect(() =>
      buildAdjustmentTerms({
        baseDate: d('2024-10-25'),
        nonAdjustable: percent(10),
        weightings: [{ indexId: 'IDX-LAB', weight: percent(60) }],
      }),
    ).toThrow(/have to come to 100/);
  });

  it('refuses two weightings against one index', () => {
    expect(() =>
      buildAdjustmentTerms({
        baseDate: d('2024-10-25'),
        weightings: [
          { indexId: 'IDX-LAB', weight: percent(50) },
          { indexId: 'IDX-LAB', weight: percent(50) },
        ],
      }),
    ).toThrow(/two weightings/);
  });

  it('refuses a negative weighting', () => {
    expect(() =>
      buildAdjustmentTerms({
        baseDate: d('2024-10-25'),
        weightings: [
          { indexId: 'IDX-LAB', weight: percent(110) },
          { indexId: 'IDX-PLA', weight: percent(-10) },
        ],
      }),
    ).toThrow(/cannot be negative/);
  });
});

describe('adjustmentFactor', () => {
  it('weights each index by its own movement', () => {
    const factor = adjustmentFactor(terms, series, d('2025-01-25'));
    expect(factor.lines[0]!.movement).toBeCloseTo(0.1, 10);
    expect(factor.lines[0]!.contribution).toBeCloseTo(0.06, 10);
    expect(factor.lines[1]!.movement).toBeCloseTo(0.05, 10);
    expect(factor.lines[1]!.contribution).toBeCloseTo(0.015, 10);
    expect(factor.factor).toBeCloseTo(0.075, 10);
  });

  it('lets one index fall while another rises', () => {
    const factor = adjustmentFactor(terms, series, d('2025-03-25'));
    expect(factor.lines[1]!.movement).toBeCloseTo(0.025, 10);
    expect(factor.factor).toBeCloseTo(0.6 * 0.2 + 0.3 * 0.025, 10);
  });

  it('is nothing at the base date itself', () => {
    expect(adjustmentFactor(terms, series, d('2024-10-25')).factor).toBe(0);
  });

  it('says so while any contributing figure is provisional', () => {
    expect(adjustmentFactor(terms, series, d('2025-01-25')).provisional).toBe(false);
    expect(adjustmentFactor(terms, series, d('2025-03-25')).provisional).toBe(true);
  });

  it('refuses a factor against an index nobody loaded', () => {
    expect(() => adjustmentFactor(terms, [labour], d('2025-01-25'))).toThrow(/index series/);
  });
});

describe('priceAdjustment', () => {
  it('takes the fixed element off before applying the factor', () => {
    const adjustment = priceAdjustment(terms, series, gbp(100_000), d('2025-01-25'));
    expect(moneyMajor(adjustment.adjustable)).toBe(90_000);
    expect(moneyMajor(adjustment.amount)).toBe(6750);
  });

  it('moves nothing at all when the fixed element is the whole price', () => {
    const fixed = buildAdjustmentTerms({
      baseDate: d('2024-10-25'),
      nonAdjustable: percent(100),
      weightings: [],
    });
    const adjustment = priceAdjustment(fixed, series, gbp(100_000), d('2025-01-25'));
    expect(adjustment.adjustable.minor).toBe(0);
    expect(adjustment.amount.minor).toBe(0);
  });

  it('takes money off when the indices have fallen back', () => {
    const falling = buildIndexSeries({
      id: 'IDX-LAB',
      readings: [reading(2024, 10, 100), reading(2025, 1, 90)],
    });
    const all = buildAdjustmentTerms({
      baseDate: d('2024-10-25'),
      weightings: [{ indexId: 'IDX-LAB', weight: percent(100) }],
    });
    const adjustment = priceAdjustment(all, [falling], gbp(100_000), d('2025-01-25'));
    expect(moneyMajor(adjustment.amount)).toBe(-10_000);
  });

  it('carries the provisional flag through from the factor', () => {
    expect(priceAdjustment(terms, series, gbp(100_000), d('2025-03-25')).provisional).toBe(
      true,
    );
  });

  it('is nothing on a valuation of nothing', () => {
    expect(priceAdjustment(terms, series, gbp(0), d('2025-01-25')).amount.minor).toBe(0);
  });
});

describe('factorPercent', () => {
  it('reads back as a percentage', () => {
    const factor = adjustmentFactor(terms, series, d('2025-01-25'));
    expect(percentValue(factorPercent(factor))).toBeCloseTo(7.5, 2);
  });
});

describe('noAdjustment', () => {
  it('moves nothing and reports nothing provisional', () => {
    const none = noAdjustment(gbp(100_000), d('2025-01-25'));
    expect(none.amount.minor).toBe(0);
    expect(none.factor.lines).toEqual([]);
    expect(none.provisional).toBe(false);
  });
});

describe('describeAdjustment', () => {
  it('says the factor, what it applied to and whether it is firm', () => {
    const adjustment = priceAdjustment(terms, series, gbp(100_000), d('2025-01-25'));
    expect(describeAdjustment(adjustment)).toBe(
      'factor 7.500 percent on 90000 adjustable, firm',
    );
  });
});
