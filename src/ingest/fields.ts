import { BadInputError } from '../errors.js';
import type { ItemCode } from '../domain/bill/itemCode.js';
import { tryParseItemCode } from '../domain/bill/itemCode.js';
import type { ChainageRange } from '../domain/chainage/range.js';
import { chainageRange } from '../domain/chainage/range.js';
import type { Station } from '../domain/chainage/station.js';
import { tryParseStation } from '../domain/chainage/station.js';
import type { Money } from '../domain/money/money.js';
import { tryParseMoney } from '../domain/money/money.js';
import type { CurrencyCode } from '../domain/money/money.js';
import type { Quantity } from '../domain/quantity/quantity.js';
import { quantity } from '../domain/quantity/quantity.js';
import { parseUnit } from '../domain/quantity/unit.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { tryParseSiteDate } from '../domain/time/siteDate.js';
import type { RowReader } from '../io/csv.js';

/**
 * Field readers shared by every importer.
 *
 * They throw rather than returning a result, and the importer catches once per
 * row. That is the opposite of the rule everywhere else in this package, and it
 * is right here: a row has ten fields, any one of them can be wrong, and
 * threading a result through ten of them turns a readable constructor call into
 * a staircase.
 *
 * Every failure carries the line and the column, so the catch can turn it
 * straight into an import issue that names where to look.
 */

function fail(reader: RowReader, column: string, message: string): never {
  throw new BadInputError(message, { line: reader.line, column });
}

export function need(reader: RowReader, column: string): string {
  const value = reader.require(column);
  if (!value.ok) throw value.error;
  return value.value;
}

export function optional(reader: RowReader, column: string): string | null {
  return reader.get(column);
}

export function needCode(reader: RowReader, column: string): ItemCode {
  const parsed = tryParseItemCode(need(reader, column));
  if (!parsed.ok) fail(reader, column, parsed.error.message);
  return parsed.value;
}

export function needStation(reader: RowReader, column: string): Station {
  const parsed = tryParseStation(need(reader, column));
  if (!parsed.ok) fail(reader, column, parsed.error.message);
  return parsed.value;
}

export function needDate(reader: RowReader, column: string): SiteDate {
  const parsed = tryParseSiteDate(need(reader, column));
  if (!parsed.ok) fail(reader, column, parsed.error.message);
  return parsed.value;
}

export function optionalDate(reader: RowReader, column: string): SiteDate | null {
  const text = reader.get(column);
  if (text === null) return null;
  const parsed = tryParseSiteDate(text);
  if (!parsed.ok) fail(reader, column, parsed.error.message);
  return parsed.value;
}

export function needNumber(reader: RowReader, column: string): number {
  const value = Number(need(reader, column).replace(/,/g, ''));
  if (!Number.isFinite(value)) fail(reader, column, `the ${column} is not a number`);
  return value;
}

export function needQuantity(
  reader: RowReader,
  amountColumn: string,
  unitColumn: string,
): Quantity {
  try {
    return quantity(needNumber(reader, amountColumn), parseUnit(need(reader, unitColumn)));
  } catch (error) {
    if (error instanceof BadInputError && error.details['line'] === undefined) {
      fail(reader, unitColumn, error.message);
    }
    throw error;
  }
}

export function needMoney(reader: RowReader, column: string, currency: CurrencyCode): Money {
  const parsed = tryParseMoney(need(reader, column), currency);
  if (!parsed.ok) fail(reader, column, parsed.error.message);
  return parsed.value;
}

/** A chainage range from two columns, or from one holding both ends. */
export function needExtent(
  reader: RowReader,
  fromColumn = 'from',
  toColumn = 'to',
): ChainageRange {
  return chainageRange(needStation(reader, fromColumn), needStation(reader, toColumn));
}

/** A yes or no column. Anything unrecognised is a failure, not a silent false. */
export function needFlag(reader: RowReader, column: string, fallback: boolean): boolean {
  const text = reader.get(column);
  if (text === null) return fallback;
  const cleaned = text.trim().toLowerCase();
  if (['y', 'yes', 'true', '1'].includes(cleaned)) return true;
  if (['n', 'no', 'false', '0'].includes(cleaned)) return false;
  return fail(reader, column, `${text} is not a yes or a no`);
}

/** One of a fixed set, matched loosely on case and separators. */
export function needOneOf<T extends string>(
  reader: RowReader,
  column: string,
  allowed: readonly T[],
): T {
  const text = need(reader, column);
  const cleaned = text
    .trim()
    .toLowerCase()
    .replace(/[\s-]+/g, '_');
  const found = allowed.find((value) => value === cleaned);
  if (found === undefined) {
    fail(reader, column, `${text} is not one of ${allowed.join(', ')}`);
  }
  return found;
}
