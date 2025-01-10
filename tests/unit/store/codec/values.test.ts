import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import { parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import { chainageRange } from '../../../../src/domain/chainage/range.js';
import { parseStation } from '../../../../src/domain/chainage/station.js';
import { moneyFromMajor } from '../../../../src/domain/money/money.js';
import { percent } from '../../../../src/domain/money/percent.js';
import { rate } from '../../../../src/domain/money/rate.js';
import { quantity } from '../../../../src/domain/quantity/quantity.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';
import {
  arrayFromJson,
  booleanFromJson,
  codeFromJson,
  codeToJson,
  currencyFromJson,
  dateFromJson,
  dateToJson,
  moneyFromJson,
  moneyToJson,
  objectFromJson,
  optionalDateFromJson,
  optionalDateToJson,
  optionalString,
  percentFromJson,
  percentToJson,
  quantityFromJson,
  quantityToJson,
  rangeFromJson,
  rangeToJson,
  rateFromJson,
  rateToJson,
  stationFromJson,
  stationToJson,
  unitFromJson,
} from '../../../../src/store/codec/values.js';

const roundTrip = <T>(value: T, to: (v: T) => unknown, from: (v: unknown) => T) =>
  from(JSON.parse(JSON.stringify(to(value))) as unknown);

describe('chainage', () => {
  it('writes a station the way a drawing does', () => {
    expect(stationToJson(parseStation('12+340.5'))).toBe('12+340.500');
  });

  it('round trips a station and a range', () => {
    const station = parseStation('12+340.5');
    expect(roundTrip(station, stationToJson, stationFromJson)).toBe(station);
    const range = chainageRange(parseStation('12+000'), parseStation('12+800'));
    expect(roundTrip(range, rangeToJson, rangeFromJson)).toEqual(range);
  });

  it('refuses a station that is not a string or not a chainage', () => {
    expect(() => stationFromJson(12_340_500)).toThrow(/expected a string/);
    expect(() => stationFromJson('nonsense')).toThrow(BadInputError);
  });

  it('refuses a range that is not an object', () => {
    expect(() => rangeFromJson('12+000')).toThrow(/expected an object/);
    expect(() => rangeFromJson(null)).toThrow(/expected an object/);
  });

  it('refuses a range that runs backwards', () => {
    expect(() => rangeFromJson({ from: '12+800', to: '12+000' })).toThrow(/runs forwards/);
  });
});

describe('dates', () => {
  it('round trips', () => {
    const date = parseSiteDate('2025-03-14');
    expect(dateToJson(date)).toBe('2025-03-14');
    expect(roundTrip(date, dateToJson, dateFromJson)).toBe(date);
  });

  it('carries a null through', () => {
    expect(optionalDateToJson(null)).toBeNull();
    expect(optionalDateFromJson(null)).toBeNull();
    expect(optionalDateFromJson(undefined)).toBeNull();
    expect(optionalDateFromJson('2025-03-14')).toBe(parseSiteDate('2025-03-14'));
  });

  it('refuses a date that does not exist', () => {
    expect(() => dateFromJson('2025-02-30')).toThrow(/not in that month/);
  });
});

describe('money, rates, quantities and percentages', () => {
  it('round trips money exactly', () => {
    const value = moneyFromMajor(1234.56, 'EUR');
    expect(moneyToJson(value)).toEqual({ minor: 123_456, currency: 'EUR' });
    expect(roundTrip(value, moneyToJson, moneyFromJson)).toEqual(value);
  });

  it('round trips a rate with four decimal places', () => {
    const value = rate(0.0035, 'GBP', 'kg');
    expect(rateToJson(value)).toEqual({ milliMinor: 350, currency: 'GBP', unit: 'kg' });
    expect(roundTrip(value, rateToJson, rateFromJson)).toEqual(value);
  });

  it('round trips a quantity', () => {
    const value = quantity(12.345, 'm3');
    expect(quantityToJson(value)).toEqual({ milli: 12_345, unit: 'm3' });
    expect(roundTrip(value, quantityToJson, quantityFromJson)).toEqual(value);
  });

  it('round trips a percentage as basis points', () => {
    expect(percentToJson(percent(3.75))).toBe(375);
    expect(percentFromJson(375)).toEqual(percent(3.75));
  });

  it('refuses an unknown currency and an unknown unit', () => {
    expect(() => moneyFromJson({ minor: 1, currency: 'JPY' })).toThrow(/unknown currency/);
    expect(() => currencyFromJson('JPY')).toThrow(/unknown currency/);
    expect(() => unitFromJson('furlong')).toThrow(/unknown unit/);
    expect(() => quantityFromJson({ milli: 1, unit: 'furlong' })).toThrow(/unknown unit/);
  });

  it('refuses an amount that is not a number', () => {
    expect(() => moneyFromJson({ minor: '100', currency: 'GBP' })).toThrow(/expected a number/);
    expect(() => percentFromJson('5')).toThrow(/expected a number/);
    expect(() => moneyFromJson({ minor: Number.NaN, currency: 'GBP' })).toThrow(
      /expected a number/,
    );
  });
});

describe('item codes', () => {
  it('round trips', () => {
    const code = parseItemCode('E423.1');
    expect(codeToJson(code)).toBe('E423.1');
    expect(roundTrip(code, codeToJson, codeFromJson)).toEqual(code);
  });

  it('refuses a code it cannot read', () => {
    expect(() => codeFromJson('423')).toThrow(BadInputError);
  });
});

describe('the shape helpers', () => {
  it('checks objects, lists and flags', () => {
    expect(objectFromJson({ a: 1 }, 'thing')).toEqual({ a: 1 });
    expect(arrayFromJson([1, 2], 'things')).toEqual([1, 2]);
    expect(booleanFromJson(true, 'flag')).toBe(true);
  });

  it('names what it wanted when the shape is wrong', () => {
    expect(() => objectFromJson([], 'thing')).not.toThrow();
    expect(() => objectFromJson('x', 'thing')).toThrow(/expected an object for the thing/);
    expect(() => arrayFromJson({}, 'things')).toThrow(/expected a list for the things/);
    expect(() => booleanFromJson('yes', 'flag')).toThrow(/expected true or false for the flag/);
  });

  it('defaults an absent string', () => {
    expect(optionalString(null)).toBe('');
    expect(optionalString(undefined, 'tape')).toBe('tape');
    expect(optionalString('gps', 'tape')).toBe('gps');
  });
});
