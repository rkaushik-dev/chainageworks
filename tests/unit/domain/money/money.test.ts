import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import {
  absMoney,
  addMoney,
  ALL_CURRENCIES,
  compareMoney,
  currencySymbol,
  formatMoney,
  isCurrencyCode,
  isNegativeMoney,
  isZeroMoney,
  maxMoney,
  minMoney,
  minorDigits,
  minorUnitsPerMajor,
  money,
  moneyEqual,
  moneyFromMajor,
  moneyMajor,
  negateMoney,
  parseMoney,
  scaleMoney,
  subtractMoney,
  sumMoney,
  tryParseMoney,
  zeroMoney,
} from '../../../../src/domain/money/money.js';

describe('the currency table', () => {
  it('carries the three currencies the contracts are written in', () => {
    expect(ALL_CURRENCIES).toEqual(['GBP', 'EUR', 'USD']);
  });

  it('reports symbol and minor digits', () => {
    expect(currencySymbol('GBP')).toBe('£');
    expect(currencySymbol('EUR')).toBe('€');
    expect(minorDigits('USD')).toBe(2);
    expect(minorUnitsPerMajor('GBP')).toBe(100);
  });

  it('recognises only the codes it knows', () => {
    expect(isCurrencyCode('GBP')).toBe(true);
    expect(isCurrencyCode('gbp')).toBe(false);
    expect(isCurrencyCode('JPY')).toBe(false);
  });
});

describe('money', () => {
  it('holds whole minor units', () => {
    expect(money(1234, 'GBP')).toEqual({ minor: 1234, currency: 'GBP' });
  });

  it('refuses a fraction of a penny', () => {
    expect(() => money(12.5, 'GBP')).toThrow(/whole number of minor units/);
    expect(() => money(Number.NaN, 'GBP')).toThrow(/finite/);
  });

  it('scales from major units', () => {
    expect(moneyFromMajor(12.34, 'GBP').minor).toBe(1234);
    expect(moneyFromMajor(-12.345, 'GBP').minor).toBe(-1235);
    expect(() => moneyFromMajor(Number.NaN, 'GBP')).toThrow(/finite/);
  });

  it('reads the major amount back', () => {
    expect(moneyMajor(money(1234, 'GBP'))).toBe(12.34);
  });

  it('knows zero and negative', () => {
    expect(isZeroMoney(zeroMoney('GBP'))).toBe(true);
    expect(isNegativeMoney(money(-1, 'GBP'))).toBe(true);
    expect(isNegativeMoney(zeroMoney('GBP'))).toBe(false);
  });
});

describe('arithmetic', () => {
  it('adds and subtracts', () => {
    expect(addMoney(money(1000, 'GBP'), money(234, 'GBP')).minor).toBe(1234);
    expect(subtractMoney(money(1000, 'GBP'), money(1234, 'GBP')).minor).toBe(-234);
  });

  it('refuses to mix currencies', () => {
    expect(() => addMoney(money(1, 'GBP'), money(1, 'EUR'))).toThrow(/different currencies/);
    expect(() => subtractMoney(money(1, 'GBP'), money(1, 'USD'))).toThrow(
      /different currencies/,
    );
    expect(() => compareMoney(money(1, 'GBP'), money(1, 'USD'))).toThrow(
      /different currencies/,
    );
  });

  it('negates and takes an absolute value', () => {
    expect(negateMoney(money(-500, 'GBP')).minor).toBe(500);
    expect(absMoney(money(-500, 'GBP')).minor).toBe(500);
    expect(absMoney(money(500, 'GBP')).minor).toBe(500);
  });

  it('scales and rounds half away from zero', () => {
    expect(scaleMoney(money(1000, 'GBP'), 0.05).minor).toBe(50);
    expect(scaleMoney(money(101, 'GBP'), 0.005).minor).toBe(1);
    expect(scaleMoney(money(-101, 'GBP'), 0.005).minor).toBe(-1);
    expect(() => scaleMoney(money(1, 'GBP'), Number.NaN)).toThrow(/finite/);
  });

  it('compares and picks extremes', () => {
    const small = money(1, 'GBP');
    const large = money(2, 'GBP');
    expect(compareMoney(small, large)).toBeLessThan(0);
    expect(minMoney(small, large)).toBe(small);
    expect(maxMoney(small, large)).toBe(large);
  });

  it('tests equality on amount and currency', () => {
    expect(moneyEqual(money(1, 'GBP'), money(1, 'GBP'))).toBe(true);
    expect(moneyEqual(money(1, 'GBP'), money(1, 'EUR'))).toBe(false);
  });

  it('sums a list against a named currency', () => {
    expect(sumMoney([money(100, 'GBP'), money(23, 'GBP')], 'GBP').minor).toBe(123);
    expect(sumMoney([], 'EUR')).toEqual({ minor: 0, currency: 'EUR' });
    expect(() => sumMoney([money(1, 'USD')], 'GBP')).toThrow(/different currencies/);
  });
});

describe('formatMoney', () => {
  it('writes the symbol and two places by default', () => {
    expect(formatMoney(money(123_456, 'GBP'))).toBe('£1,234.56');
  });

  it('pads the minor part', () => {
    expect(formatMoney(money(105, 'GBP'))).toBe('£1.05');
    expect(formatMoney(money(5, 'GBP'))).toBe('£0.05');
  });

  it('keeps the sign outside the symbol', () => {
    expect(formatMoney(money(-105, 'GBP'))).toBe('-£1.05');
  });

  it('can drop the grouping', () => {
    expect(formatMoney(money(123_456, 'GBP'), { grouped: false })).toBe('£1234.56');
  });

  it('can write the code instead of the symbol', () => {
    expect(formatMoney(money(123_456, 'EUR'), { symbol: false })).toBe('1,234.56 EUR');
  });

  it('groups long amounts in threes', () => {
    expect(formatMoney(money(123_456_789_00, 'GBP'))).toBe('£123,456,789.00');
  });
});

describe('parseMoney', () => {
  it('reads a plain amount against the fallback currency', () => {
    expect(parseMoney('1234.56', 'GBP')).toEqual({ minor: 123_456, currency: 'GBP' });
  });

  it('takes the currency from a symbol', () => {
    expect(parseMoney('€10.00', 'GBP').currency).toBe('EUR');
    expect(parseMoney('$10.00', 'GBP').currency).toBe('USD');
  });

  it('takes the currency from a trailing code', () => {
    expect(parseMoney('10.00 usd', 'GBP').currency).toBe('USD');
  });

  it('strips thousands separators', () => {
    expect(parseMoney('1,234,567.89').minor).toBe(123_456_789);
  });

  it('reads a negative written either way round', () => {
    expect(parseMoney('-£10.00').minor).toBe(-1000);
    expect(parseMoney('£-10.00').minor).toBe(-1000);
  });

  it('defaults to sterling when nothing says otherwise', () => {
    expect(parseMoney('10.00').currency).toBe('GBP');
  });

  it('reports an unknown currency code', () => {
    const r = tryParseMoney('10.00 JPY', 'GBP');
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.error.message).toMatch(/unknown currency/);
  });

  it('reports something that is not money at all', () => {
    const r = tryParseMoney('a lot', 'GBP');
    expect(r.ok).toBe(false);
    expect(() => parseMoney('a lot')).toThrow(BadInputError);
  });
});
