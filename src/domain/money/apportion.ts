import { BadInputError } from '../../errors.js';
import type { Quantity } from '../quantity/quantity.js';
import type { Money } from './money.js';

/**
 * Split a total across weighted shares so the shares add back to the total
 * exactly.
 *
 * The naive way, rounding each share on its own, loses or gains pennies: a
 * retention release split three ways is a penny short about a third of the
 * time, and on a valuation that penny has to be chased. This uses largest
 * remainder, so the leftover pennies go to the shares with the biggest
 * fractional part and the sum is right by construction.
 */
export function apportionUnits(total: number, weights: readonly number[]): number[] {
  if (!Number.isInteger(total)) {
    throw new BadInputError('can only apportion a whole number', { total });
  }
  if (weights.length === 0) {
    if (total !== 0) {
      throw new BadInputError('nothing to apportion a total across', { total });
    }
    return [];
  }
  let weightTotal = 0;
  for (const [index, weight] of weights.entries()) {
    if (!Number.isFinite(weight) || weight < 0) {
      throw new BadInputError('apportionment weights must be finite and not negative', {
        index,
        weight: String(weight),
      });
    }
    weightTotal += weight;
  }
  if (weightTotal === 0) {
    if (total !== 0) {
      throw new BadInputError('cannot apportion a total across weights that are all zero', {
        total,
      });
    }
    return weights.map(() => 0);
  }

  const shares: number[] = [];
  const fractions: { index: number; fraction: number }[] = [];
  let allocated = 0;
  for (const [index, weight] of weights.entries()) {
    const exact = (total * weight) / weightTotal;
    const floor = Math.floor(exact);
    shares.push(floor);
    fractions.push({ index, fraction: exact - floor });
    allocated += floor;
  }

  // Ties go to the earlier share, so the same input always gives the same split.
  fractions.sort((a, b) => b.fraction - a.fraction || a.index - b.index);
  let remainder = total - allocated;
  for (const entry of fractions) {
    if (remainder === 0) break;
    const current = shares[entry.index];
    if (current === undefined) continue;
    shares[entry.index] = current + 1;
    remainder -= 1;
  }
  return shares;
}

export function apportionMoney(total: Money, weights: readonly number[]): Money[] {
  return apportionUnits(total.minor, weights).map((minor) => ({
    minor,
    currency: total.currency,
  }));
}

export function apportionQuantity(total: Quantity, weights: readonly number[]): Quantity[] {
  return apportionUnits(total.milli, weights).map((milli) => ({ milli, unit: total.unit }));
}

/** Weight the split by measured quantities, which is the usual case. */
export function apportionMoneyByQuantity(
  total: Money,
  quantities: readonly Quantity[],
): Money[] {
  return apportionMoney(
    total,
    quantities.map((q) => q.milli),
  );
}

/** Equal shares, with the odd pennies going to the earliest shares. */
export function splitMoneyEvenly(total: Money, parts: number): Money[] {
  if (!Number.isInteger(parts) || parts < 1) {
    throw new BadInputError('need at least one share to split into', { parts });
  }
  return apportionMoney(total, new Array<number>(parts).fill(1));
}

/**
 * Check that a set of shares really does add back to the total. Used by the
 * valuation assembler, which builds shares from several sources and has no
 * other way to know they still balance.
 */
export function sharesBalance(total: Money, shares: readonly Money[]): boolean {
  let sum = 0;
  for (const share of shares) {
    if (share.currency !== total.currency) return false;
    sum += share.minor;
  }
  return sum === total.minor;
}
