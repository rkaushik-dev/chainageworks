import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import {
  addQuantities,
  assertClaimable,
  compareQuantities,
  formatQuantity,
  isNegativeQuantity,
  isZeroQuantity,
  maxQuantity,
  minQuantity,
  negateQuantity,
  parseQuantity,
  quantitiesEqual,
  quantity,
  quantityAmount,
  quantityFromMilli,
  roundToBillPrecision,
  scaleQuantity,
  subtractQuantities,
  sumQuantities,
  tryParseQuantity,
  zeroQuantity,
} from '../../../../src/domain/quantity/quantity.js';

describe('quantity', () => {
  it('scales an amount into thousandths', () => {
    expect(quantity(12.5, 'm3').milli).toBe(12_500);
    expect(quantity(0, 'nr').milli).toBe(0);
  });

  it('rounds half away from zero on both sides', () => {
    expect(quantity(0.0005, 'm3').milli).toBe(1);
    expect(quantity(-0.0005, 'm3').milli).toBe(-1);
  });

  it('refuses a non finite amount', () => {
    expect(() => quantity(Number.NaN, 'm')).toThrow(/finite/);
    expect(() => quantity(Number.POSITIVE_INFINITY, 'm')).toThrow(/finite/);
  });

  it('refuses a fractional thousandth when built from milli', () => {
    expect(() => quantityFromMilli(1.5, 'm')).toThrow(/whole number of thousandths/);
    expect(() => quantityFromMilli(Number.NaN, 'm')).toThrow(/finite/);
  });

  it('reads the amount back', () => {
    expect(quantityAmount(quantity(12.345, 'm3'))).toBe(12.345);
  });

  it('makes a zero that knows its unit', () => {
    expect(zeroQuantity('m2')).toEqual({ milli: 0, unit: 'm2' });
    expect(isZeroQuantity(zeroQuantity('m2'))).toBe(true);
    expect(isZeroQuantity(quantity(0.001, 'm2'))).toBe(false);
  });

  it('reports a negative amount', () => {
    expect(isNegativeQuantity(quantity(-1, 'm'))).toBe(true);
    expect(isNegativeQuantity(quantity(0, 'm'))).toBe(false);
  });
});

describe('arithmetic', () => {
  it('adds and subtracts in the same unit', () => {
    expect(addQuantities(quantity(2.5, 'm3'), quantity(1.25, 'm3'))).toEqual({
      milli: 3_750,
      unit: 'm3',
    });
    expect(subtractQuantities(quantity(2.5, 'm3'), quantity(1.25, 'm3'))).toEqual({
      milli: 1_250,
      unit: 'm3',
    });
  });

  it('refuses to mix units', () => {
    expect(() => addQuantities(quantity(1, 't'), quantity(1, 'kg'))).toThrow(/different units/);
    expect(() => subtractQuantities(quantity(1, 'm'), quantity(1, 'm2'))).toThrow(
      /different units/,
    );
    expect(() => compareQuantities(quantity(1, 'm'), quantity(1, 'm2'))).toThrow(
      /different units/,
    );
  });

  it('goes negative on subtraction, because an overclaim has to be visible', () => {
    expect(subtractQuantities(quantity(1, 'm3'), quantity(4, 'm3')).milli).toBe(-3_000);
  });

  it('negates', () => {
    expect(negateQuantity(quantity(2, 'm'))).toEqual({ milli: -2_000, unit: 'm' });
  });

  it('scales by a fraction and rounds', () => {
    expect(scaleQuantity(quantity(10, 'm3'), 0.25)).toEqual({ milli: 2_500, unit: 'm3' });
    expect(scaleQuantity(quantity(1, 'm3'), 1 / 3).milli).toBe(333);
    expect(scaleQuantity(quantity(-1, 'm3'), 1 / 3).milli).toBe(-333);
  });

  it('refuses a scale factor that is not finite', () => {
    expect(() => scaleQuantity(quantity(1, 'm'), Number.NaN)).toThrow(/finite/);
  });

  it('compares and picks extremes', () => {
    const small = quantity(1, 'm');
    const large = quantity(2, 'm');
    expect(compareQuantities(small, large)).toBeLessThan(0);
    expect(minQuantity(small, large)).toBe(small);
    expect(maxQuantity(small, large)).toBe(large);
    expect(minQuantity(small, small)).toBe(small);
    expect(maxQuantity(small, small)).toBe(small);
  });

  it('tests equality on both the amount and the unit', () => {
    expect(quantitiesEqual(quantity(1, 'm'), quantity(1, 'm'))).toBe(true);
    expect(quantitiesEqual(quantity(1, 'm'), quantity(1, 'm2'))).toBe(false);
    expect(quantitiesEqual(quantity(1, 'm'), quantity(2, 'm'))).toBe(false);
  });
});

describe('sumQuantities', () => {
  it('adds a list', () => {
    expect(
      sumQuantities([quantity(1, 'm'), quantity(2, 'm'), quantity(0.5, 'm')], 'm'),
    ).toEqual({ milli: 3_500, unit: 'm' });
  });

  it('returns a typed zero for an empty list', () => {
    expect(sumQuantities([], 'm3')).toEqual({ milli: 0, unit: 'm3' });
  });

  it('refuses a part in the wrong unit', () => {
    expect(() => sumQuantities([quantity(1, 'm'), quantity(1, 'm2')], 'm')).toThrow(
      /different units/,
    );
  });
});

describe('roundToBillPrecision', () => {
  it('rounds a number item to whole units', () => {
    expect(roundToBillPrecision(quantity(3.6, 'nr'))).toEqual({ milli: 4_000, unit: 'nr' });
    expect(roundToBillPrecision(quantity(3.4, 'nr'))).toEqual({ milli: 3_000, unit: 'nr' });
  });

  it('rounds a volume to two places', () => {
    expect(roundToBillPrecision(quantity(12.3456, 'm3'))).toEqual({
      milli: 12_350,
      unit: 'm3',
    });
  });

  it('leaves a tonne alone, since three places is what it is held to', () => {
    const q = quantity(12.345, 't');
    expect(roundToBillPrecision(q)).toEqual(q);
  });

  it('rounds a negative the same way', () => {
    expect(roundToBillPrecision(quantity(-3.6, 'nr'))).toEqual({ milli: -4_000, unit: 'nr' });
  });
});

describe('formatQuantity', () => {
  it('writes the unit precision by default', () => {
    expect(formatQuantity(quantity(12.5, 'm3'))).toBe('12.50 m3');
    expect(formatQuantity(quantity(4, 'nr'))).toBe('4 nr');
    expect(formatQuantity(quantity(1.5, 't'))).toBe('1.500 t');
  });

  it('honours an explicit precision', () => {
    expect(formatQuantity(quantity(12.5, 'm3'), 3)).toBe('12.500 m3');
  });
});

describe('parseQuantity', () => {
  it('reads a number and a unit', () => {
    expect(parseQuantity('12.5 m3')).toEqual({ milli: 12_500, unit: 'm3' });
  });

  it('reads a site spelling of the unit', () => {
    expect(parseQuantity('12.5 CUM')).toEqual({ milli: 12_500, unit: 'm3' });
    expect(parseQuantity('3 No.')).toEqual({ milli: 3_000, unit: 'nr' });
  });

  it('reads a negative and a missing space', () => {
    expect(parseQuantity('-4m')).toEqual({ milli: -4_000, unit: 'm' });
  });

  it('reports a missing unit', () => {
    const r = tryParseQuantity('12.5');
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.error.message).toMatch(/number and a unit/);
  });

  it('reports an unknown unit', () => {
    const r = tryParseQuantity('12.5 furlongs');
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.error.message).toMatch(/unknown unit/);
  });

  it('throws from the strict form', () => {
    expect(() => parseQuantity('nonsense')).toThrow(BadInputError);
  });
});

describe('assertClaimable', () => {
  it('allows nothing and the whole of a lump sum', () => {
    expect(() => {
      assertClaimable(quantity(0, 'sum'));
    }).not.toThrow();
    expect(() => {
      assertClaimable(quantity(1, 'sum'));
    }).not.toThrow();
  });

  it('refuses part of a lump sum', () => {
    expect(() => {
      assertClaimable(quantity(0.5, 'sum'));
    }).toThrow(/whole or not at all/);
  });

  it('lets a measurable unit hold anything', () => {
    expect(() => {
      assertClaimable(quantity(0.5, 'm3'));
    }).not.toThrow();
  });
});
