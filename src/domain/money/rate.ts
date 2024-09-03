import { BadInputError } from '../../errors.js';
import type { Result } from '../../result.js';
import { err, ok } from '../../result.js';
import type { Quantity } from '../quantity/quantity.js';
import { MILLI } from '../quantity/quantity.js';
import type { UnitCode } from '../quantity/unit.js';
import { assertSameUnit, parseUnit } from '../quantity/unit.js';
import type { CurrencyCode, Money } from './money.js';
import { currencySymbol, isCurrencyCode, minorUnitsPerMajor, money } from './money.js';

/** A rate carries three more decimal places than the money it produces. */
export const RATE_SCALE = 1000;

/**
 * A bill rate: what one unit of an item is worth.
 *
 * Rates are held to a thousandth of a minor unit because tendered rates are
 * quoted to more places than money is paid in. A rate of 0.0035 per kilogram is
 * ordinary on a steel item, and rounding it to the penny before the extension
 * would lose most of the value of the item.
 */
export interface Rate {
  readonly milliMinor: number;
  readonly currency: CurrencyCode;
  readonly unit: UnitCode;
}

export function rateFromMilliMinor(
  milliMinor: number,
  currency: CurrencyCode,
  unit: UnitCode,
): Rate {
  if (!Number.isFinite(milliMinor)) {
    throw new BadInputError('rate must be a finite number', { value: String(milliMinor) });
  }
  if (!Number.isInteger(milliMinor)) {
    throw new BadInputError('rate must be a whole number of rate units', {
      value: milliMinor,
    });
  }
  return { milliMinor, currency, unit };
}

/** Build from what the bill actually says, for example 12.34 per cubic metre. */
export function rate(major: number, currency: CurrencyCode, unit: UnitCode): Rate {
  if (!Number.isFinite(major)) {
    throw new BadInputError('rate must be a finite number', { value: String(major) });
  }
  const scaled = major * minorUnitsPerMajor(currency) * RATE_SCALE;
  const rounded = scaled < 0 ? -Math.round(-scaled) : Math.round(scaled);
  return rateFromMilliMinor(rounded, currency, unit);
}

export function zeroRate(currency: CurrencyCode, unit: UnitCode): Rate {
  return { milliMinor: 0, currency, unit };
}

export function rateMajor(r: Rate): number {
  return r.milliMinor / (minorUnitsPerMajor(r.currency) * RATE_SCALE);
}

export function isZeroRate(r: Rate): boolean {
  return r.milliMinor === 0;
}

export function isNegativeRate(r: Rate): boolean {
  return r.milliMinor < 0;
}

/** A rate for one whole unit, taken from a money figure. */
export function rateFromMoney(m: Money, unit: UnitCode): Rate {
  return rateFromMilliMinor(m.minor * RATE_SCALE, m.currency, unit);
}

/**
 * The extension: rate times quantity, rounded to the penny once, at the end.
 *
 * Both operands carry three extra places, so the product carries six and the
 * divisor takes them all off together. Rounding the rate first, or the quantity
 * first, gives a different answer on a long bill.
 */
export function extend(r: Rate, q: Quantity): Money {
  assertSameUnit(r.unit, q.unit);
  const product = r.milliMinor * q.milli;
  const divisor = RATE_SCALE * MILLI;
  const scaled = product / divisor;
  const rounded = scaled < 0 ? -Math.round(-scaled) : Math.round(scaled);
  return money(rounded, r.currency);
}

export function scaleRate(r: Rate, factor: number): Rate {
  if (!Number.isFinite(factor)) {
    throw new BadInputError('scale factor must be a finite number', { value: String(factor) });
  }
  const scaled = r.milliMinor * factor;
  const rounded = scaled < 0 ? -Math.round(-scaled) : Math.round(scaled);
  return { milliMinor: rounded, currency: r.currency, unit: r.unit };
}

export function ratesEqual(a: Rate, b: Rate): boolean {
  return a.milliMinor === b.milliMinor && a.currency === b.currency && a.unit === b.unit;
}

export function compareRates(a: Rate, b: Rate): number {
  if (a.currency !== b.currency) {
    throw new BadInputError('cannot compare rates in different currencies', {
      left: a.currency,
      right: b.currency,
    });
  }
  assertSameUnit(a.unit, b.unit);
  return a.milliMinor - b.milliMinor;
}

export interface FormatRateOptions {
  /** Decimal places of the major unit. Defaults to as many as the rate needs. */
  readonly decimals?: number;
  readonly symbol?: boolean;
}

export function formatRate(r: Rate, options: FormatRateOptions = {}): string {
  const decimals = options.decimals ?? significantDecimals(r);
  const body = rateMajor(r).toFixed(decimals);
  const head = options.symbol === false ? '' : currencySymbol(r.currency);
  const tail = options.symbol === false ? ` ${r.currency}` : '';
  return `${head}${body}${tail}/${r.unit}`;
}

/** Two places unless the rate genuinely needs more, up to five. */
function significantDecimals(r: Rate): number {
  const perMajor = minorUnitsPerMajor(r.currency) * RATE_SCALE;
  let remainder = Math.abs(r.milliMinor) % perMajor;
  let decimals = 0;
  let step = perMajor;
  while (remainder > 0 && decimals < 5) {
    step /= 10;
    decimals += 1;
    remainder %= step;
  }
  return Math.max(2, decimals);
}

const RATE_TEXT = /^(.+?)\s*(?:\/|\bper\b)\s*([A-Za-z][A-Za-z0-9²³.\s]*)$/i;
const AMOUNT_TEXT = /^(-?)\s*([£€$])?\s*(-?[\d,]+(?:\.\d+)?)\s*([A-Za-z]{3})?$/;

export function tryParseRate(text: string, fallback: CurrencyCode): Result<Rate> {
  const trimmed = text.trim();
  const parts = RATE_TEXT.exec(trimmed);
  if (parts === null) {
    return err(new BadInputError('a rate is an amount per unit', { text: trimmed }));
  }
  let unit: UnitCode;
  try {
    unit = parseUnit(parts[2] ?? '');
  } catch (e) {
    return err(e as BadInputError);
  }
  const amount = AMOUNT_TEXT.exec((parts[1] ?? '').trim());
  if (amount === null) {
    return err(new BadInputError('a rate is an amount per unit', { text: trimmed }));
  }
  const [, sign, symbol, digits, code] = amount;
  let currency = fallback;
  if (code !== undefined) {
    const upper = code.toUpperCase();
    if (!isCurrencyCode(upper)) {
      return err(new BadInputError('unknown currency', { text: code }));
    }
    currency = upper;
  } else if (symbol !== undefined) {
    currency = symbolCurrency(symbol, fallback);
  }
  const value = Number((digits ?? '').replace(/,/g, ''));
  if (!Number.isFinite(value)) {
    return err(new BadInputError('rate amount is not a number', { text: trimmed }));
  }
  return ok(rate(sign === '-' ? -value : value, currency, unit));
}

function symbolCurrency(symbol: string, fallback: CurrencyCode): CurrencyCode {
  if (symbol === '£') return 'GBP';
  if (symbol === '€') return 'EUR';
  if (symbol === '$') return 'USD';
  return fallback;
}

export function parseRate(text: string, fallback: CurrencyCode = 'GBP'): Rate {
  const r = tryParseRate(text, fallback);
  if (!r.ok) throw r.error;
  return r.value;
}

/** Round trip helper for reports that want the rate as plain money per unit. */
export function rateAsMoneyPerUnit(r: Rate): Money {
  const perMajor = RATE_SCALE;
  const scaled = r.milliMinor / perMajor;
  const rounded = scaled < 0 ? -Math.round(-scaled) : Math.round(scaled);
  return money(rounded, r.currency);
}

export function describeRate(r: Rate): string {
  return `${rateMajor(r).toFixed(4)} ${r.currency} per ${r.unit}`;
}
