import { BadInputError } from '../../errors.js';
import type { Result } from '../../result.js';
import { err, ok } from '../../result.js';
import type { UnitCode } from './unit.js';
import { assertSameUnit, isMeasurable, parseUnit, unitDecimals } from './unit.js';

/** Quantities are held as integer thousandths, for the same reason chainage is. */
export const MILLI = 1000;

export interface Quantity {
  /** The amount, times one thousand, as a whole number. */
  readonly milli: number;
  readonly unit: UnitCode;
}

export function quantityFromMilli(milli: number, unit: UnitCode): Quantity {
  if (!Number.isFinite(milli)) {
    throw new BadInputError('quantity must be a finite number', { value: String(milli) });
  }
  if (!Number.isInteger(milli)) {
    throw new BadInputError('quantity must be a whole number of thousandths', { value: milli });
  }
  return { milli, unit };
}

export function quantity(amount: number, unit: UnitCode): Quantity {
  if (!Number.isFinite(amount)) {
    throw new BadInputError('quantity must be a finite number', { value: String(amount) });
  }
  const scaled = amount * MILLI;
  return quantityFromMilli(scaled < 0 ? -Math.round(-scaled) : Math.round(scaled), unit);
}

export function zeroQuantity(unit: UnitCode): Quantity {
  return { milli: 0, unit };
}

export function quantityAmount(q: Quantity): number {
  return q.milli / MILLI;
}

export function isZeroQuantity(q: Quantity): boolean {
  return q.milli === 0;
}

export function isNegativeQuantity(q: Quantity): boolean {
  return q.milli < 0;
}

export function addQuantities(a: Quantity, b: Quantity): Quantity {
  assertSameUnit(a.unit, b.unit);
  return { milli: a.milli + b.milli, unit: a.unit };
}

export function subtractQuantities(a: Quantity, b: Quantity): Quantity {
  assertSameUnit(a.unit, b.unit);
  return { milli: a.milli - b.milli, unit: a.unit };
}

export function negateQuantity(q: Quantity): Quantity {
  return { milli: -q.milli, unit: q.unit };
}

/** Take a fraction of a quantity. Used by rules of credit, so it rounds half up. */
export function scaleQuantity(q: Quantity, factor: number): Quantity {
  if (!Number.isFinite(factor)) {
    throw new BadInputError('scale factor must be a finite number', { value: String(factor) });
  }
  const scaled = q.milli * factor;
  return { milli: scaled < 0 ? -Math.round(-scaled) : Math.round(scaled), unit: q.unit };
}

export function compareQuantities(a: Quantity, b: Quantity): number {
  assertSameUnit(a.unit, b.unit);
  return a.milli - b.milli;
}

export function quantitiesEqual(a: Quantity, b: Quantity): boolean {
  return a.unit === b.unit && a.milli === b.milli;
}

export function minQuantity(a: Quantity, b: Quantity): Quantity {
  return compareQuantities(a, b) <= 0 ? a : b;
}

export function maxQuantity(a: Quantity, b: Quantity): Quantity {
  return compareQuantities(a, b) >= 0 ? a : b;
}

/**
 * Sum a list. The unit has to be named because an empty list still has to know
 * what it is a zero of, and a running total of nothing in metres is not the
 * same total as nothing in cubic metres.
 */
export function sumQuantities(parts: readonly Quantity[], unit: UnitCode): Quantity {
  let milli = 0;
  for (const part of parts) {
    assertSameUnit(unit, part.unit);
    milli += part.milli;
  }
  return { milli, unit };
}

/**
 * Round to the number of places the bill is written to. A measured quantity is
 * only billed at its unit's precision, so this is what goes on the valuation
 * even when the survey booked more figures than that.
 */
export function roundToBillPrecision(q: Quantity): Quantity {
  const places = unitDecimals(q.unit);
  const step = MILLI / 10 ** places;
  if (step <= 1) return q;
  const halves = q.milli / step;
  const rounded = halves < 0 ? -Math.round(-halves) : Math.round(halves);
  return { milli: rounded * step, unit: q.unit };
}

export function formatQuantity(q: Quantity, places = unitDecimals(q.unit)): string {
  return `${quantityAmount(q).toFixed(places)} ${q.unit}`;
}

const QUANTITY_TEXT = /^(-?\d+(?:\.\d+)?)\s*([A-Za-z][A-Za-z0-9²³.\s]*)$/;

export function tryParseQuantity(text: string): Result<Quantity> {
  const trimmed = text.trim();
  const m = QUANTITY_TEXT.exec(trimmed);
  if (m === null) {
    return err(new BadInputError('quantity must be a number and a unit', { text: trimmed }));
  }
  try {
    return ok(quantity(Number(m[1]), parseUnit(m[2] ?? '')));
  } catch (e) {
    return err(e as BadInputError);
  }
}

export function parseQuantity(text: string): Quantity {
  const r = tryParseQuantity(text);
  if (!r.ok) throw r.error;
  return r.value;
}

/**
 * A lump sum has no measurable quantity, so the only amounts it may carry are
 * nothing and the whole of it.
 */
export function assertClaimable(q: Quantity): void {
  if (!isMeasurable(q.unit) && q.milli !== 0 && q.milli !== MILLI) {
    throw new BadInputError('a lump sum item is claimed whole or not at all', {
      unit: q.unit,
      amount: quantityAmount(q),
    });
  }
}
