import { BadInputError } from '../../errors.js';
import type { ItemCode } from '../../domain/bill/itemCode.js';
import { formatItemCode, parseItemCode } from '../../domain/bill/itemCode.js';
import type { ChainageRange } from '../../domain/chainage/range.js';
import { chainageRange } from '../../domain/chainage/range.js';
import type { Station } from '../../domain/chainage/station.js';
import { formatStation, parseStation } from '../../domain/chainage/station.js';
import type { CurrencyCode, Money } from '../../domain/money/money.js';
import { isCurrencyCode, money } from '../../domain/money/money.js';
import type { Percent } from '../../domain/money/percent.js';
import { percentFromBasisPoints } from '../../domain/money/percent.js';
import type { Rate } from '../../domain/money/rate.js';
import { rateFromMilliMinor } from '../../domain/money/rate.js';
import type { Quantity } from '../../domain/quantity/quantity.js';
import { quantityFromMilli } from '../../domain/quantity/quantity.js';
import type { UnitCode } from '../../domain/quantity/unit.js';
import { isUnitCode } from '../../domain/quantity/unit.js';
import type { SiteDate } from '../../domain/time/siteDate.js';
import { formatSiteDate, parseSiteDate } from '../../domain/time/siteDate.js';

/**
 * How the domain values are written to a file.
 *
 * Chainage and dates go out as the strings a person would write, because the
 * file is read by people when a valuation is argued over and `12+340.000` can
 * be checked against a drawing while `12340500` cannot. Money and quantities go
 * out as the integers they are held as, because those have to come back exactly
 * and a decimal string would invite a reader to round it.
 */

export interface MoneyJson {
  readonly minor: number;
  readonly currency: string;
}

export interface QuantityJson {
  readonly milli: number;
  readonly unit: string;
}

export interface RateJson {
  readonly milliMinor: number;
  readonly currency: string;
  readonly unit: string;
}

export interface RangeJson {
  readonly from: string;
  readonly to: string;
}

function need(value: unknown, what: string): Record<string, unknown> {
  if (typeof value !== 'object' || value === null) {
    throw new BadInputError(`expected an object for the ${what}`, { got: typeof value });
  }
  return value as Record<string, unknown>;
}

function needNumber(value: unknown, what: string): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new BadInputError(`expected a number for the ${what}`, { got: String(value) });
  }
  return value;
}

export function needString(value: unknown, what: string): string {
  if (typeof value !== 'string') {
    throw new BadInputError(`expected a string for the ${what}`, { got: typeof value });
  }
  return value;
}

export function stationToJson(station: Station): string {
  return formatStation(station);
}

export function stationFromJson(value: unknown): Station {
  return parseStation(needString(value, 'chainage'));
}

export function rangeToJson(range: ChainageRange): RangeJson {
  return { from: formatStation(range.from), to: formatStation(range.to) };
}

export function rangeFromJson(value: unknown): ChainageRange {
  const raw = need(value, 'chainage range');
  return chainageRange(stationFromJson(raw['from']), stationFromJson(raw['to']));
}

export function dateToJson(date: SiteDate): string {
  return formatSiteDate(date);
}

export function dateFromJson(value: unknown): SiteDate {
  return parseSiteDate(needString(value, 'date'));
}

export function optionalDateToJson(date: SiteDate | null): string | null {
  return date === null ? null : formatSiteDate(date);
}

export function optionalDateFromJson(value: unknown): SiteDate | null {
  return value === null || value === undefined ? null : dateFromJson(value);
}

export function moneyToJson(value: Money): MoneyJson {
  return { minor: value.minor, currency: value.currency };
}

export function moneyFromJson(value: unknown): Money {
  const raw = need(value, 'money');
  const currency = needString(raw['currency'], 'currency');
  if (!isCurrencyCode(currency)) {
    throw new BadInputError('unknown currency', { currency });
  }
  return money(needNumber(raw['minor'], 'money'), currency);
}

export function currencyFromJson(value: unknown): CurrencyCode {
  const code = needString(value, 'currency');
  if (!isCurrencyCode(code)) {
    throw new BadInputError('unknown currency', { currency: code });
  }
  return code;
}

export function unitFromJson(value: unknown): UnitCode {
  const code = needString(value, 'unit');
  if (!isUnitCode(code)) {
    throw new BadInputError('unknown unit', { unit: code });
  }
  return code;
}

export function quantityToJson(value: Quantity): QuantityJson {
  return { milli: value.milli, unit: value.unit };
}

export function quantityFromJson(value: unknown): Quantity {
  const raw = need(value, 'quantity');
  return quantityFromMilli(needNumber(raw['milli'], 'quantity'), unitFromJson(raw['unit']));
}

export function rateToJson(value: Rate): RateJson {
  return { milliMinor: value.milliMinor, currency: value.currency, unit: value.unit };
}

export function rateFromJson(value: unknown): Rate {
  const raw = need(value, 'rate');
  return rateFromMilliMinor(
    needNumber(raw['milliMinor'], 'rate'),
    currencyFromJson(raw['currency']),
    unitFromJson(raw['unit']),
  );
}

export function percentToJson(value: Percent): number {
  return value.basisPoints;
}

export function percentFromJson(value: unknown): Percent {
  return percentFromBasisPoints(needNumber(value, 'percentage'));
}

export function codeToJson(code: ItemCode): string {
  return formatItemCode(code);
}

export function codeFromJson(value: unknown): ItemCode {
  return parseItemCode(needString(value, 'item code'));
}

export function objectFromJson(value: unknown, what: string): Record<string, unknown> {
  return need(value, what);
}

export function arrayFromJson(value: unknown, what: string): unknown[] {
  if (!Array.isArray(value)) {
    throw new BadInputError(`expected a list for the ${what}`, { got: typeof value });
  }
  return value;
}

export function optionalString(value: unknown, fallback = ''): string {
  return value === null || value === undefined ? fallback : needString(value, 'text');
}

export function booleanFromJson(value: unknown, what: string): boolean {
  if (typeof value !== 'boolean') {
    throw new BadInputError(`expected true or false for the ${what}`, { got: typeof value });
  }
  return value;
}
