import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import { money } from '../../../../src/domain/money/money.js';
import {
  addPercents,
  applyPercent,
  applyPercentToQuantity,
  clampPercent,
  comparePercents,
  complementPercent,
  formatPercent,
  FULL_PERCENT,
  isZeroPercent,
  parsePercent,
  percent,
  percentFraction,
  percentFromBasisPoints,
  percentOf,
  percentsEqual,
  percentValue,
  subtractPercents,
  tryParsePercent,
  ZERO_PERCENT,
} from '../../../../src/domain/money/percent.js';
import { quantity } from '../../../../src/domain/quantity/quantity.js';

describe('percent', () => {
  it('holds basis points', () => {
    expect(percent(5).basisPoints).toBe(500);
    expect(percent(3.75).basisPoints).toBe(375);
    expect(percent(12.5).basisPoints).toBe(1250);
    expect(percent(100).basisPoints).toBe(10_000);
  });

  it('refuses a fraction of a basis point and a non number', () => {
    expect(() => percentFromBasisPoints(1.5)).toThrow(/whole number of basis points/);
    expect(() => percentFromBasisPoints(Number.NaN)).toThrow(/finite/);
    expect(() => percent(Number.NaN)).toThrow(/finite/);
  });

  it('reads back as a percentage and as a fraction', () => {
    expect(percentValue(percent(3.75))).toBe(3.75);
    expect(percentFraction(percent(5))).toBe(0.05);
  });

  it('carries the two constants everything compares against', () => {
    expect(ZERO_PERCENT.basisPoints).toBe(0);
    expect(FULL_PERCENT.basisPoints).toBe(10_000);
    expect(isZeroPercent(ZERO_PERCENT)).toBe(true);
    expect(isZeroPercent(percent(0.01))).toBe(false);
  });
});

describe('comparison and arithmetic', () => {
  it('compares and tests equality', () => {
    expect(percentsEqual(percent(5), percent(5))).toBe(true);
    expect(percentsEqual(percent(5), percent(5.01))).toBe(false);
    expect(comparePercents(percent(3), percent(5))).toBeLessThan(0);
    expect(comparePercents(percent(5), percent(5))).toBe(0);
  });

  it('adds and subtracts', () => {
    expect(addPercents(percent(3), percent(2))).toEqual({ basisPoints: 500 });
    expect(subtractPercents(percent(3), percent(5))).toEqual({ basisPoints: -200 });
  });

  it('takes the complement, which is what retention leaves behind', () => {
    expect(complementPercent(percent(5))).toEqual({ basisPoints: 9500 });
    expect(complementPercent(ZERO_PERCENT)).toEqual(FULL_PERCENT);
  });

  it('clamps into a band', () => {
    expect(clampPercent(percent(-1), ZERO_PERCENT, FULL_PERCENT)).toEqual(ZERO_PERCENT);
    expect(clampPercent(percent(150), ZERO_PERCENT, FULL_PERCENT)).toEqual(FULL_PERCENT);
    expect(clampPercent(percent(50), ZERO_PERCENT, FULL_PERCENT)).toEqual(percent(50));
  });

  it('refuses clamp bounds the wrong way round', () => {
    expect(() => clampPercent(percent(5), FULL_PERCENT, ZERO_PERCENT)).toThrow(
      /wrong way round/,
    );
  });
});

describe('applyPercent', () => {
  it('takes retention off a valuation', () => {
    expect(applyPercent(money(100_000, 'GBP'), percent(5)).minor).toBe(5000);
  });

  it('handles an awkward percentage exactly', () => {
    expect(applyPercent(money(100_000, 'GBP'), percent(3.75)).minor).toBe(3750);
  });

  it('rounds half away from zero', () => {
    expect(applyPercent(money(101, 'GBP'), percent(50)).minor).toBe(51);
    expect(applyPercent(money(-101, 'GBP'), percent(50)).minor).toBe(-51);
  });

  it('keeps the currency', () => {
    expect(applyPercent(money(100, 'EUR'), percent(10)).currency).toBe('EUR');
  });

  it('gives nothing for nothing', () => {
    expect(applyPercent(money(100_000, 'GBP'), ZERO_PERCENT).minor).toBe(0);
    expect(applyPercent(money(0, 'GBP'), percent(5)).minor).toBe(0);
  });
});

describe('applyPercentToQuantity', () => {
  it('takes a share of a measured quantity', () => {
    expect(applyPercentToQuantity(quantity(100, 'm3'), percent(25))).toEqual({
      milli: 25_000,
      unit: 'm3',
    });
  });

  it('rounds to the thousandth', () => {
    expect(applyPercentToQuantity(quantity(1, 'm3'), percent(0.05)).milli).toBe(1);
  });
});

describe('percentOf', () => {
  it('works out the share one part is of a whole', () => {
    expect(percentOf(money(2500, 'GBP'), money(10_000, 'GBP'))).toEqual({
      basisPoints: 2500,
    });
  });

  it('rounds to the nearest basis point', () => {
    expect(percentValue(percentOf(money(1, 'GBP'), money(3, 'GBP')))).toBe(33.33);
  });

  it('refuses to divide by nothing', () => {
    expect(() => percentOf(money(1, 'GBP'), money(0, 'GBP'))).toThrow(/percentage of nothing/);
  });
});

describe('formatPercent', () => {
  it('drops the decimals when there are none', () => {
    expect(formatPercent(percent(5))).toBe('5%');
  });

  it('shows two places when the percentage needs them', () => {
    expect(formatPercent(percent(3.75))).toBe('3.75%');
  });

  it('honours an explicit precision', () => {
    expect(formatPercent(percent(5), 2)).toBe('5.00%');
    expect(formatPercent(percent(3.75), 1)).toBe('3.8%');
  });
});

describe('parsePercent', () => {
  it('reads a number with or without the sign', () => {
    expect(parsePercent('5%')).toEqual({ basisPoints: 500 });
    expect(parsePercent('5')).toEqual({ basisPoints: 500 });
    expect(parsePercent(' 3.75 % ')).toEqual({ basisPoints: 375 });
  });

  it('reads a negative', () => {
    expect(parsePercent('-2.5%').basisPoints).toBe(-250);
  });

  it('reports anything else', () => {
    const r = tryParsePercent('a bit');
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.error.message).toMatch(/must be a number/);
    expect(() => parsePercent('a bit')).toThrow(BadInputError);
  });
});
