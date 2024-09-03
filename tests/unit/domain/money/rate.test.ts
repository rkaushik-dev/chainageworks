import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import { money, moneyMajor } from '../../../../src/domain/money/money.js';
import {
  compareRates,
  describeRate,
  extend,
  formatRate,
  isNegativeRate,
  isZeroRate,
  parseRate,
  rate,
  rateAsMoneyPerUnit,
  rateFromMilliMinor,
  rateFromMoney,
  rateMajor,
  ratesEqual,
  scaleRate,
  tryParseRate,
  zeroRate,
} from '../../../../src/domain/money/rate.js';
import { quantity } from '../../../../src/domain/quantity/quantity.js';

describe('rate', () => {
  it('holds three more places than money does', () => {
    expect(rate(12.34, 'GBP', 'm3').milliMinor).toBe(1_234_000);
    expect(rate(0.0035, 'GBP', 'kg').milliMinor).toBe(350);
  });

  it('reads the major amount back', () => {
    expect(rateMajor(rate(12.34, 'GBP', 'm3'))).toBe(12.34);
    expect(rateMajor(rate(0.0035, 'GBP', 'kg'))).toBe(0.0035);
  });

  it('refuses a fractional rate unit and a non number', () => {
    expect(() => rateFromMilliMinor(1.5, 'GBP', 'm')).toThrow(/whole number of rate units/);
    expect(() => rateFromMilliMinor(Number.NaN, 'GBP', 'm')).toThrow(/finite/);
    expect(() => rate(Number.NaN, 'GBP', 'm')).toThrow(/finite/);
  });

  it('knows zero and negative', () => {
    expect(isZeroRate(zeroRate('GBP', 'm'))).toBe(true);
    expect(isNegativeRate(rate(-1, 'GBP', 'm'))).toBe(true);
    expect(isNegativeRate(zeroRate('GBP', 'm'))).toBe(false);
  });

  it('builds from a money figure for one unit', () => {
    expect(rateMajor(rateFromMoney(money(1234, 'GBP'), 'm3'))).toBe(12.34);
  });
});

describe('extend', () => {
  it('multiplies rate by quantity and rounds once', () => {
    const value = extend(rate(12.34, 'GBP', 'm3'), quantity(10.5, 'm3'));
    expect(value).toEqual({ minor: 12_957, currency: 'GBP' });
    expect(moneyMajor(value)).toBe(129.57);
  });

  it('keeps the value of a rate with four decimal places', () => {
    const value = extend(rate(0.0035, 'GBP', 'kg'), quantity(1000, 'kg'));
    expect(moneyMajor(value)).toBe(3.5);
  });

  it('would lose the whole item if the rate were rounded first', () => {
    const tiny = rate(0.004, 'GBP', 'kg');
    expect(moneyMajor(extend(tiny, quantity(10_000, 'kg')))).toBe(40);
  });

  it('rounds half away from zero at the penny', () => {
    expect(extend(rate(0.005, 'GBP', 'nr'), quantity(1, 'nr')).minor).toBe(1);
    expect(extend(rate(-0.005, 'GBP', 'nr'), quantity(1, 'nr')).minor).toBe(-1);
    expect(extend(rate(0.004, 'GBP', 'nr'), quantity(1, 'nr')).minor).toBe(0);
  });

  it('gives nothing for nothing', () => {
    expect(extend(rate(12.34, 'GBP', 'm3'), quantity(0, 'm3')).minor).toBe(0);
    expect(extend(zeroRate('GBP', 'm3'), quantity(10, 'm3')).minor).toBe(0);
  });

  it('carries a negative quantity through, so an omission reduces the value', () => {
    expect(extend(rate(10, 'GBP', 'm3'), quantity(-2, 'm3')).minor).toBe(-2000);
  });

  it('refuses a rate whose unit is not the quantity unit', () => {
    expect(() => extend(rate(1, 'GBP', 'm3'), quantity(1, 'm2'))).toThrow(/different units/);
  });
});

describe('scaleRate', () => {
  it('scales and rounds', () => {
    expect(scaleRate(rate(10, 'GBP', 'm'), 1.05).milliMinor).toBe(1_050_000);
    expect(scaleRate(rate(0.001, 'GBP', 'm'), 0.5).milliMinor).toBe(50);
  });

  it('refuses a factor that is not finite', () => {
    expect(() => scaleRate(rate(1, 'GBP', 'm'), Number.NaN)).toThrow(/finite/);
  });
});

describe('ratesEqual and compareRates', () => {
  it('compares every field for equality', () => {
    expect(ratesEqual(rate(1, 'GBP', 'm'), rate(1, 'GBP', 'm'))).toBe(true);
    expect(ratesEqual(rate(1, 'GBP', 'm'), rate(1, 'EUR', 'm'))).toBe(false);
    expect(ratesEqual(rate(1, 'GBP', 'm'), rate(1, 'GBP', 'm2'))).toBe(false);
  });

  it('orders two rates for the same item', () => {
    expect(compareRates(rate(1, 'GBP', 'm'), rate(2, 'GBP', 'm'))).toBeLessThan(0);
  });

  it('refuses to order across currencies or units', () => {
    expect(() => compareRates(rate(1, 'GBP', 'm'), rate(1, 'EUR', 'm'))).toThrow(
      /different currencies/,
    );
    expect(() => compareRates(rate(1, 'GBP', 'm'), rate(1, 'GBP', 'm2'))).toThrow(
      /different units/,
    );
  });
});

describe('formatRate', () => {
  it('writes two places for an ordinary rate', () => {
    expect(formatRate(rate(12.34, 'GBP', 'm3'))).toBe('£12.34/m3');
    expect(formatRate(rate(12, 'GBP', 'm3'))).toBe('£12.00/m3');
  });

  it('keeps the places a fine rate needs', () => {
    expect(formatRate(rate(0.0035, 'GBP', 'kg'))).toBe('£0.0035/kg');
    expect(formatRate(rate(1.005, 'GBP', 'kg'))).toBe('£1.005/kg');
  });

  it('honours an explicit precision', () => {
    expect(formatRate(rate(12.3456, 'GBP', 'm3'), { decimals: 2 })).toBe('£12.35/m3');
  });

  it('can write the code instead of the symbol', () => {
    expect(formatRate(rate(12.34, 'EUR', 'm3'), { symbol: false })).toBe('12.34 EUR/m3');
  });

  it('describes itself in words for a report line', () => {
    expect(describeRate(rate(12.34, 'GBP', 'm3'))).toBe('12.3400 GBP per m3');
  });
});

describe('parseRate', () => {
  it('reads the slash form', () => {
    expect(parseRate('£12.34/m3')).toEqual({
      milliMinor: 1_234_000,
      currency: 'GBP',
      unit: 'm3',
    });
  });

  it('reads the word form', () => {
    expect(parseRate('12.34 per m3').milliMinor).toBe(1_234_000);
    expect(parseRate('12.34 PER cu m').unit).toBe('m3');
  });

  it('reads a fine rate', () => {
    expect(parseRate('£0.0035 per kg').milliMinor).toBe(350);
  });

  it('strips separators and takes the currency from a code', () => {
    const r = parseRate('1,234.56 usd / m');
    expect(r.currency).toBe('USD');
    expect(rateMajor(r)).toBe(1234.56);
  });

  it('reads a negative rate', () => {
    expect(parseRate('-£10.00/m').milliMinor).toBe(-1_000_000);
  });

  it('reports a missing unit', () => {
    const r = tryParseRate('12.34', 'GBP');
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.error.message).toMatch(/amount per unit/);
  });

  it('reports an unknown unit and an unknown currency', () => {
    const badUnit = tryParseRate('12.34 per furlong', 'GBP');
    expect(badUnit.ok).toBe(false);
    if (!badUnit.ok) expect(badUnit.error.message).toMatch(/unknown unit/);

    const badCurrency = tryParseRate('12.34 jpy per m', 'GBP');
    expect(badCurrency.ok).toBe(false);
    if (!badCurrency.ok) expect(badCurrency.error.message).toMatch(/unknown currency/);
  });

  it('reports an amount that is not a number', () => {
    const r = tryParseRate('lots per m', 'GBP');
    expect(r.ok).toBe(false);
    expect(() => parseRate('lots per m')).toThrow(BadInputError);
  });
});

describe('rateAsMoneyPerUnit', () => {
  it('drops the extra places at the penny', () => {
    expect(rateAsMoneyPerUnit(rate(12.34, 'GBP', 'm3')).minor).toBe(1234);
    expect(rateAsMoneyPerUnit(rate(0.0035, 'GBP', 'kg')).minor).toBe(0);
    expect(rateAsMoneyPerUnit(rate(0.006, 'GBP', 'kg')).minor).toBe(1);
  });
});
