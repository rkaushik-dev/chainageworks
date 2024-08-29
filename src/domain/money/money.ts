import { BadInputError } from '../../errors.js';
import type { Result } from '../../result.js';
import { err, ok } from '../../result.js';

export type CurrencyCode = 'GBP' | 'EUR' | 'USD';

interface CurrencyDef {
  readonly code: CurrencyCode;
  readonly symbol: string;
  readonly minorDigits: 2;
}

const CURRENCIES: Readonly<Record<CurrencyCode, CurrencyDef>> = {
  GBP: { code: 'GBP', symbol: '£', minorDigits: 2 },
  EUR: { code: 'EUR', symbol: '€', minorDigits: 2 },
  USD: { code: 'USD', symbol: '$', minorDigits: 2 },
};

export const ALL_CURRENCIES: readonly CurrencyCode[] = Object.keys(
  CURRENCIES,
) as CurrencyCode[];

/**
 * Money is a whole number of minor units. Nothing in a valuation is ever priced
 * in fractions of a penny: rates carry the extra places, and the extension to
 * money is where they are rounded away, once, on purpose.
 */
export interface Money {
  readonly minor: number;
  readonly currency: CurrencyCode;
}

export function isCurrencyCode(value: string): value is CurrencyCode {
  return Object.prototype.hasOwnProperty.call(CURRENCIES, value);
}

export function currencySymbol(currency: CurrencyCode): string {
  return CURRENCIES[currency].symbol;
}

export function minorDigits(currency: CurrencyCode): number {
  return CURRENCIES[currency].minorDigits;
}

export function minorUnitsPerMajor(currency: CurrencyCode): number {
  return 10 ** minorDigits(currency);
}

export function money(minor: number, currency: CurrencyCode): Money {
  if (!Number.isFinite(minor)) {
    throw new BadInputError('money must be a finite number', { value: String(minor) });
  }
  if (!Number.isInteger(minor)) {
    throw new BadInputError('money must be a whole number of minor units', { value: minor });
  }
  return { minor, currency };
}

export function moneyFromMajor(major: number, currency: CurrencyCode): Money {
  if (!Number.isFinite(major)) {
    throw new BadInputError('money must be a finite number', { value: String(major) });
  }
  const scaled = major * minorUnitsPerMajor(currency);
  return money(scaled < 0 ? -Math.round(-scaled) : Math.round(scaled), currency);
}

export function zeroMoney(currency: CurrencyCode): Money {
  return { minor: 0, currency };
}

export function moneyMajor(m: Money): number {
  return m.minor / minorUnitsPerMajor(m.currency);
}

export function isZeroMoney(m: Money): boolean {
  return m.minor === 0;
}

export function isNegativeMoney(m: Money): boolean {
  return m.minor < 0;
}

export function assertSameCurrency(a: CurrencyCode, b: CurrencyCode): void {
  if (a !== b) {
    throw new BadInputError('cannot combine money in different currencies', {
      left: a,
      right: b,
    });
  }
}

export function addMoney(a: Money, b: Money): Money {
  assertSameCurrency(a.currency, b.currency);
  return { minor: a.minor + b.minor, currency: a.currency };
}

export function subtractMoney(a: Money, b: Money): Money {
  assertSameCurrency(a.currency, b.currency);
  return { minor: a.minor - b.minor, currency: a.currency };
}

export function negateMoney(m: Money): Money {
  return { minor: -m.minor, currency: m.currency };
}

export function absMoney(m: Money): Money {
  return { minor: Math.abs(m.minor), currency: m.currency };
}

/** Percentages and retention factors land here. Rounds half away from zero. */
export function scaleMoney(m: Money, factor: number): Money {
  if (!Number.isFinite(factor)) {
    throw new BadInputError('scale factor must be a finite number', { value: String(factor) });
  }
  const scaled = m.minor * factor;
  return {
    minor: scaled < 0 ? -Math.round(-scaled) : Math.round(scaled),
    currency: m.currency,
  };
}

export function compareMoney(a: Money, b: Money): number {
  assertSameCurrency(a.currency, b.currency);
  return a.minor - b.minor;
}

export function moneyEqual(a: Money, b: Money): boolean {
  return a.currency === b.currency && a.minor === b.minor;
}

export function minMoney(a: Money, b: Money): Money {
  return compareMoney(a, b) <= 0 ? a : b;
}

export function maxMoney(a: Money, b: Money): Money {
  return compareMoney(a, b) >= 0 ? a : b;
}

export function sumMoney(parts: readonly Money[], currency: CurrencyCode): Money {
  let minor = 0;
  for (const part of parts) {
    assertSameCurrency(currency, part.currency);
    minor += part.minor;
  }
  return { minor, currency };
}

export interface FormatMoneyOptions {
  /** Write the symbol rather than the code. On by default. */
  readonly symbol?: boolean;
  /** Group the major units in threes. On by default. */
  readonly grouped?: boolean;
}

export function formatMoney(m: Money, options: FormatMoneyOptions = {}): string {
  const digits = minorDigits(m.currency);
  const negative = m.minor < 0;
  const magnitude = Math.abs(m.minor);
  const per = minorUnitsPerMajor(m.currency);
  const major = Math.floor(magnitude / per);
  const minor = magnitude - major * per;
  const majorText =
    options.grouped === false ? String(major) : String(major).replace(GROUPS, ',');
  const body = `${majorText}.${String(minor).padStart(digits, '0')}`;
  if (options.symbol === false) {
    return `${negative ? '-' : ''}${body} ${m.currency}`;
  }
  return `${negative ? '-' : ''}${currencySymbol(m.currency)}${body}`;
}

const GROUPS = /\B(?=(\d{3})+(?!\d))/g;

const MONEY_TEXT = /^(-?)\s*([£€$])?\s*(-?[\d,]+(?:\.\d+)?)\s*([A-Za-z]{3})?$/;
const SYMBOL_TO_CODE: Readonly<Record<string, CurrencyCode>> = {
  '£': 'GBP',
  '€': 'EUR',
  $: 'USD',
};

export function tryParseMoney(text: string, fallback: CurrencyCode): Result<Money> {
  const trimmed = text.trim();
  const m = MONEY_TEXT.exec(trimmed);
  if (m === null) {
    return err(
      new BadInputError('money must be an amount with an optional currency', { text: trimmed }),
    );
  }
  const [, leadingSign, symbol, digits, code] = m;
  let currency = fallback;
  if (code !== undefined) {
    const upper = code.toUpperCase();
    if (!isCurrencyCode(upper)) {
      return err(new BadInputError('unknown currency', { text: code }));
    }
    currency = upper;
  } else if (symbol !== undefined) {
    currency = SYMBOL_TO_CODE[symbol] ?? fallback;
  }
  const cleaned = (digits ?? '').replace(/,/g, '');
  const value = Number(cleaned);
  if (!Number.isFinite(value)) {
    return err(new BadInputError('money amount is not a number', { text: trimmed }));
  }
  return ok(moneyFromMajor(leadingSign === '-' ? -value : value, currency));
}

export function parseMoney(text: string, fallback: CurrencyCode = 'GBP'): Money {
  const r = tryParseMoney(text, fallback);
  if (!r.ok) throw r.error;
  return r.value;
}
