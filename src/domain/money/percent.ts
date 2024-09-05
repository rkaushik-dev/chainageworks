import { BadInputError } from '../../errors.js';
import type { Result } from '../../result.js';
import { err, ok } from '../../result.js';
import type { Quantity } from '../quantity/quantity.js';
import type { Money } from './money.js';

/**
 * A percentage in basis points, so a hundredth of one percent is the finest
 * step. Retention is 5 percent, a discount on materials on site is 12.5, and
 * a contract price adjustment factor can be 3.75. All three are exact here,
 * which they would not be as a float.
 */
export interface Percent {
  readonly basisPoints: number;
}

export const BASIS_POINTS_PER_PERCENT = 100;
export const BASIS_POINTS_WHOLE = 10_000;

export function percentFromBasisPoints(basisPoints: number): Percent {
  if (!Number.isFinite(basisPoints)) {
    throw new BadInputError('percentage must be a finite number', {
      value: String(basisPoints),
    });
  }
  if (!Number.isInteger(basisPoints)) {
    throw new BadInputError('percentage must be a whole number of basis points', {
      value: basisPoints,
    });
  }
  return { basisPoints };
}

export function percent(value: number): Percent {
  if (!Number.isFinite(value)) {
    throw new BadInputError('percentage must be a finite number', { value: String(value) });
  }
  const scaled = value * BASIS_POINTS_PER_PERCENT;
  const rounded = scaled < 0 ? -Math.round(-scaled) : Math.round(scaled);
  return percentFromBasisPoints(rounded);
}

export const ZERO_PERCENT: Percent = { basisPoints: 0 };
export const FULL_PERCENT: Percent = { basisPoints: BASIS_POINTS_WHOLE };

export function percentValue(p: Percent): number {
  return p.basisPoints / BASIS_POINTS_PER_PERCENT;
}

export function percentFraction(p: Percent): number {
  return p.basisPoints / BASIS_POINTS_WHOLE;
}

export function isZeroPercent(p: Percent): boolean {
  return p.basisPoints === 0;
}

export function percentsEqual(a: Percent, b: Percent): boolean {
  return a.basisPoints === b.basisPoints;
}

export function comparePercents(a: Percent, b: Percent): number {
  return a.basisPoints - b.basisPoints;
}

export function addPercents(a: Percent, b: Percent): Percent {
  return { basisPoints: a.basisPoints + b.basisPoints };
}

export function subtractPercents(a: Percent, b: Percent): Percent {
  return { basisPoints: a.basisPoints - b.basisPoints };
}

/** What is left when this much is taken off. Retention of 5 leaves 95. */
export function complementPercent(p: Percent): Percent {
  return { basisPoints: BASIS_POINTS_WHOLE - p.basisPoints };
}

export function clampPercent(p: Percent, low: Percent, high: Percent): Percent {
  if (low.basisPoints > high.basisPoints) {
    throw new BadInputError('percentage bounds are the wrong way round', {
      low: percentValue(low),
      high: percentValue(high),
    });
  }
  if (p.basisPoints < low.basisPoints) return low;
  if (p.basisPoints > high.basisPoints) return high;
  return p;
}

/** Take this much of a money figure, rounded to the penny half away from zero. */
export function applyPercent(m: Money, p: Percent): Money {
  const scaled = (m.minor * p.basisPoints) / BASIS_POINTS_WHOLE;
  const rounded = scaled < 0 ? -Math.round(-scaled) : Math.round(scaled);
  return { minor: rounded, currency: m.currency };
}

export function applyPercentToQuantity(q: Quantity, p: Percent): Quantity {
  const scaled = (q.milli * p.basisPoints) / BASIS_POINTS_WHOLE;
  const rounded = scaled < 0 ? -Math.round(-scaled) : Math.round(scaled);
  return { milli: rounded, unit: q.unit };
}

/** The share one part is of a whole, to the nearest basis point. */
export function percentOf(part: Money, whole: Money): Percent {
  if (whole.minor === 0) {
    throw new BadInputError('cannot take a percentage of nothing');
  }
  const scaled = (part.minor * BASIS_POINTS_WHOLE) / whole.minor;
  const rounded = scaled < 0 ? -Math.round(-scaled) : Math.round(scaled);
  return { basisPoints: rounded };
}

export function formatPercent(p: Percent, decimals?: number): string {
  const places = decimals ?? (p.basisPoints % BASIS_POINTS_PER_PERCENT === 0 ? 0 : 2);
  return `${percentValue(p).toFixed(places)}%`;
}

const PERCENT_TEXT = /^(-?\d+(?:\.\d+)?)\s*%?$/;

export function tryParsePercent(text: string): Result<Percent> {
  const trimmed = text.trim();
  const m = PERCENT_TEXT.exec(trimmed);
  if (m === null) {
    return err(new BadInputError('percentage must be a number', { text: trimmed }));
  }
  return ok(percent(Number(m[1])));
}

export function parsePercent(text: string): Percent {
  const r = tryParsePercent(text);
  if (!r.ok) throw r.error;
  return r.value;
}
