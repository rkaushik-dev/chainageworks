import { describe, expect, it } from 'vitest';
import {
  apportionMoney,
  apportionMoneyByQuantity,
  apportionQuantity,
  apportionUnits,
  sharesBalance,
  splitMoneyEvenly,
} from '../../../../src/domain/money/apportion.js';
import { money } from '../../../../src/domain/money/money.js';
import { quantity } from '../../../../src/domain/quantity/quantity.js';

describe('apportionUnits', () => {
  it('splits evenly when it divides', () => {
    expect(apportionUnits(100, [1, 1, 1, 1])).toEqual([25, 25, 25, 25]);
  });

  it('gives the leftover to the largest remainders, not to the last share', () => {
    expect(apportionUnits(100, [1, 1, 1])).toEqual([34, 33, 33]);
  });

  it('always adds back to the total', () => {
    for (const total of [1, 7, 99, 100, 1234, 100_000]) {
      for (const weights of [[1, 1, 1], [2, 3, 5], [1], [7, 11, 13, 17]]) {
        const shares = apportionUnits(total, weights);
        expect(shares.reduce((a, b) => a + b, 0)).toBe(total);
      }
    }
  });

  it('follows the weights', () => {
    expect(apportionUnits(1000, [1, 4])).toEqual([200, 800]);
    expect(apportionUnits(1000, [0, 1])).toEqual([0, 1000]);
  });

  it('breaks a tie in favour of the earlier share', () => {
    expect(apportionUnits(1, [1, 1])).toEqual([1, 0]);
    expect(apportionUnits(2, [1, 1, 1])).toEqual([1, 1, 0]);
  });

  it('handles a negative total and still balances', () => {
    const shares = apportionUnits(-100, [1, 1, 1]);
    expect(shares.reduce((a, b) => a + b, 0)).toBe(-100);
  });

  it('splits nothing into nothing', () => {
    expect(apportionUnits(0, [1, 2, 3])).toEqual([0, 0, 0]);
    expect(apportionUnits(0, [])).toEqual([]);
    expect(apportionUnits(0, [0, 0])).toEqual([0, 0]);
  });

  it('refuses a fractional total', () => {
    expect(() => apportionUnits(1.5, [1])).toThrow(/whole number/);
  });

  it('refuses to lose a total with nowhere to put it', () => {
    expect(() => apportionUnits(100, [])).toThrow(/nothing to apportion/);
    expect(() => apportionUnits(100, [0, 0])).toThrow(/weights that are all zero/);
  });

  it('refuses a negative or non finite weight', () => {
    expect(() => apportionUnits(100, [1, -1])).toThrow(/not negative/);
    expect(() => apportionUnits(100, [1, Number.NaN])).toThrow(/finite/);
  });

  it('accepts fractional weights', () => {
    expect(apportionUnits(100, [0.5, 1.5])).toEqual([25, 75]);
  });
});

describe('apportionMoney', () => {
  it('keeps the currency on every share', () => {
    const shares = apportionMoney(money(100, 'EUR'), [1, 1, 1]);
    expect(shares).toEqual([
      { minor: 34, currency: 'EUR' },
      { minor: 33, currency: 'EUR' },
      { minor: 33, currency: 'EUR' },
    ]);
  });

  it('never loses a penny on an awkward split', () => {
    const total = money(1_000_01, 'GBP');
    const shares = apportionMoney(total, [3, 3, 3, 1]);
    expect(sharesBalance(total, shares)).toBe(true);
  });
});

describe('apportionQuantity', () => {
  it('splits a measured quantity and keeps the unit', () => {
    expect(apportionQuantity(quantity(10, 'm3'), [1, 1])).toEqual([
      { milli: 5000, unit: 'm3' },
      { milli: 5000, unit: 'm3' },
    ]);
  });
});

describe('apportionMoneyByQuantity', () => {
  it('weights the split by what was measured', () => {
    const shares = apportionMoneyByQuantity(money(10_000, 'GBP'), [
      quantity(30, 'm3'),
      quantity(70, 'm3'),
    ]);
    expect(shares.map((s) => s.minor)).toEqual([3000, 7000]);
  });

  it('leaves nothing for an item that measured nothing', () => {
    const shares = apportionMoneyByQuantity(money(100, 'GBP'), [
      quantity(0, 'm3'),
      quantity(1, 'm3'),
    ]);
    expect(shares.map((s) => s.minor)).toEqual([0, 100]);
  });
});

describe('splitMoneyEvenly', () => {
  it('divides into equal shares', () => {
    expect(splitMoneyEvenly(money(1000, 'GBP'), 4).map((s) => s.minor)).toEqual([
      250, 250, 250, 250,
    ]);
  });

  it('puts the odd pennies on the earliest shares', () => {
    expect(splitMoneyEvenly(money(1001, 'GBP'), 4).map((s) => s.minor)).toEqual([
      251, 250, 250, 250,
    ]);
  });

  it('refuses a share count that is not a positive whole number', () => {
    expect(() => splitMoneyEvenly(money(100, 'GBP'), 0)).toThrow(/at least one share/);
    expect(() => splitMoneyEvenly(money(100, 'GBP'), 1.5)).toThrow(/at least one share/);
  });
});

describe('sharesBalance', () => {
  it('is true when the shares add up', () => {
    expect(sharesBalance(money(100, 'GBP'), [money(60, 'GBP'), money(40, 'GBP')])).toBe(true);
  });

  it('is false when a penny is missing', () => {
    expect(sharesBalance(money(100, 'GBP'), [money(60, 'GBP'), money(39, 'GBP')])).toBe(false);
  });

  it('is false when a share is in the wrong currency', () => {
    expect(sharesBalance(money(100, 'GBP'), [money(60, 'GBP'), money(40, 'EUR')])).toBe(false);
  });

  it('is true for nothing split into nothing', () => {
    expect(sharesBalance(money(0, 'GBP'), [])).toBe(true);
  });
});
