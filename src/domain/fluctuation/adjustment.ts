import { BadInputError } from '../../errors.js';
import type { Money } from '../money/money.js';
import { scaleMoney, zeroMoney } from '../money/money.js';
import type { Percent } from '../money/percent.js';
import {
  BASIS_POINTS_WHOLE,
  percent,
  percentFraction,
  percentFromBasisPoints,
} from '../money/percent.js';
import type { SiteDate } from '../time/siteDate.js';
import type { IndexSeries } from './index.js';
import { isProvisionalAt, movementSince, requireSeries } from './index.js';

/**
 * One index and the share of the works it stands for.
 *
 * The shares are set at tender against the make up of the price, which is why
 * they are contract terms rather than anything measured. Nothing about how the
 * work actually went changes them, and the temptation to reweight them when
 * the mix turns out differently is the temptation to reprice the contract.
 */
export interface Weighting {
  readonly indexId: string;
  readonly weight: Percent;
}

export interface AdjustmentTerms {
  /** The month the tender was priced in, which every index is read against. */
  readonly baseDate: SiteDate;
  /** The share of the price that never moves, however the indices do. */
  readonly nonAdjustable: Percent;
  readonly weightings: readonly Weighting[];
}

export interface AdjustmentTermsInput {
  readonly baseDate: SiteDate;
  readonly nonAdjustable?: Percent;
  readonly weightings: readonly Weighting[];
}

/**
 * The weightings and the fixed element have to come to the whole price.
 *
 * Checked rather than normalised. A set that comes to ninety eight percent is
 * a typing mistake in a contract document, and quietly scaling it up would
 * make the adjustment slightly wrong every month for the life of the job with
 * nothing anywhere to say why.
 */
export function buildAdjustmentTerms(input: AdjustmentTermsInput): AdjustmentTerms {
  const nonAdjustable = input.nonAdjustable ?? percent(0);
  const seen = new Set<string>();
  let total = nonAdjustable.basisPoints;

  for (const weighting of input.weightings) {
    if (seen.has(weighting.indexId)) {
      throw new BadInputError('an index carries two weightings', {
        index: weighting.indexId,
      });
    }
    if (weighting.weight.basisPoints < 0) {
      throw new BadInputError('a weighting cannot be negative', {
        index: weighting.indexId,
      });
    }
    seen.add(weighting.indexId);
    total += weighting.weight.basisPoints;
  }

  if (total !== BASIS_POINTS_WHOLE) {
    throw new BadInputError('the weightings and the fixed element have to come to 100', {
      total: total / 100,
    });
  }
  return { baseDate: input.baseDate, nonAdjustable, weightings: [...input.weightings] };
}

export interface FactorLine {
  readonly indexId: string;
  readonly weight: Percent;
  /** How far this index has moved since the base date, as a fraction of one. */
  readonly movement: number;
  /** The weighted contribution to the factor. */
  readonly contribution: number;
  readonly provisional: boolean;
}

export interface AdjustmentFactor {
  readonly asAt: SiteDate;
  readonly lines: readonly FactorLine[];
  /** The price adjustment factor, as a fraction of the adjustable value. */
  readonly factor: number;
  /** True while any contributing figure is still provisional. */
  readonly provisional: boolean;
}

/**
 * Work out the price adjustment factor at a date.
 *
 * The fixed element takes no part in the sum at all rather than contributing
 * zero movement, because those two are only the same while the arithmetic is
 * right, and the difference shows up as a factor that is quietly too small
 * when a weighting is missing.
 */
export function adjustmentFactor(
  terms: AdjustmentTerms,
  series: readonly IndexSeries[],
  asAt: SiteDate,
): AdjustmentFactor {
  const lines = terms.weightings.map((weighting): FactorLine => {
    const found = requireSeries(series, weighting.indexId);
    const movement = movementSince(found, terms.baseDate, asAt);
    return {
      indexId: weighting.indexId,
      weight: weighting.weight,
      movement,
      contribution: percentFraction(weighting.weight) * movement,
      provisional: isProvisionalAt(found, asAt) || isProvisionalAt(found, terms.baseDate),
    };
  });

  return {
    asAt,
    lines,
    factor: lines.reduce((total, line) => total + line.contribution, 0),
    provisional: lines.some((line) => line.provisional),
  };
}

export interface PriceAdjustment {
  readonly asAt: SiteDate;
  readonly base: Money;
  /** The part of the base the formula is allowed to move. */
  readonly adjustable: Money;
  readonly factor: AdjustmentFactor;
  readonly amount: Money;
  readonly provisional: boolean;
}

/**
 * What the formula adds to, or takes off, a valuation.
 *
 * The fixed element is taken off the base before the factor is applied rather
 * than being folded into the factor. Both give the same number when everything
 * is right, but only this order can answer the question a payer always asks,
 * which is how much of the certificate the formula was allowed to touch.
 */
export function priceAdjustment(
  terms: AdjustmentTerms,
  series: readonly IndexSeries[],
  base: Money,
  asAt: SiteDate,
): PriceAdjustment {
  const factor = adjustmentFactor(terms, series, asAt);
  const adjustable = scaleMoney(base, 1 - percentFraction(terms.nonAdjustable));
  return {
    asAt,
    base,
    adjustable,
    factor,
    amount: scaleMoney(adjustable, factor.factor),
    provisional: factor.provisional,
  };
}

/** The factor as a percentage, which is how a certificate prints it. */
export function factorPercent(factor: AdjustmentFactor): Percent {
  return percentFromBasisPoints(Math.round(factor.factor * BASIS_POINTS_WHOLE));
}

/** An adjustment of nothing, for a contract with no fluctuation clause. */
export function noAdjustment(base: Money, asAt: SiteDate): PriceAdjustment {
  return {
    asAt,
    base,
    adjustable: zeroMoney(base.currency),
    factor: { asAt, lines: [], factor: 0, provisional: false },
    amount: zeroMoney(base.currency),
    provisional: false,
  };
}

export function describeAdjustment(adjustment: PriceAdjustment): string {
  const state = adjustment.provisional ? 'provisional' : 'firm';
  return (
    `factor ${(adjustment.factor.factor * 100).toFixed(3)} percent on ` +
    `${String(adjustment.adjustable.minor / 100)} adjustable, ${state}`
  );
}
