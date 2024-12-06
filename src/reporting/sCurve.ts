import { BadInputError } from '../errors.js';
import type { Money } from '../domain/money/money.js';
import { subtractMoney, sumMoney, zeroMoney } from '../domain/money/money.js';
import type { Percent } from '../domain/money/percent.js';
import { percentOf, ZERO_PERCENT } from '../domain/money/percent.js';
import { apportionMoney } from '../domain/money/apportion.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import type { ProgressContext } from '../progress/context.js';
import { earnAll } from '../progress/earn.js';
import type { PeriodSchedule } from '../valuation/period.js';

/** What the programme said would be earned by the end of each period. */
export interface Baseline {
  readonly cumulative: readonly Money[];
}

export interface CurvePoint {
  readonly period: number;
  readonly cutOff: SiteDate;
  readonly planned: Money;
  readonly earned: Money;
  /** Earned less planned. Negative is behind. */
  readonly variance: Money;
  readonly plannedShare: Percent;
  readonly earnedShare: Percent;
}

/**
 * Spread a total across periods on an S shape.
 *
 * A civil job does not earn in a straight line. It starts slowly while the site
 * is set up, runs hard through the middle, and tails off through testing and
 * snagging. A straight line baseline therefore reports the job as behind for
 * the first third of its life and ahead for the last third, whatever actually
 * happened.
 */
export function sCurveBaseline(total: Money, periods: number): Baseline {
  if (!Number.isInteger(periods) || periods < 1) {
    throw new BadInputError('a baseline needs at least one period', { periods });
  }
  const weights: number[] = [];
  for (let i = 0; i < periods; i += 1) {
    const t = (i + 0.5) / periods;
    weights.push(Math.sin(Math.PI * t) ** 2);
  }
  const shares = apportionMoney(total, weights);
  const cumulative: Money[] = [];
  let running = zeroMoney(total.currency);
  for (const share of shares) {
    running = sumMoney([running, share], total.currency);
    cumulative.push(running);
  }
  return { cumulative };
}

/** An even spread, for a job that really does run flat. */
export function linearBaseline(total: Money, periods: number): Baseline {
  if (!Number.isInteger(periods) || periods < 1) {
    throw new BadInputError('a baseline needs at least one period', { periods });
  }
  const shares = apportionMoney(total, new Array<number>(periods).fill(1));
  const cumulative: Money[] = [];
  let running = zeroMoney(total.currency);
  for (const share of shares) {
    running = sumMoney([running, share], total.currency);
    cumulative.push(running);
  }
  return { cumulative };
}

/**
 * Planned against earned at every cut-off.
 *
 * Earned is recomputed from scratch at each cut-off rather than accumulated, so
 * a remeasure or a late sign-off shows up on the period it belongs to instead
 * of being smeared across the ones after it.
 */
export function buildSCurve(
  context: ProgressContext,
  schedule: PeriodSchedule,
  baseline: Baseline,
): CurvePoint[] {
  const total = baseline.cumulative[baseline.cumulative.length - 1];
  if (total === undefined) {
    throw new BadInputError('a baseline needs at least one period');
  }
  return schedule.periods.map((period, index) => {
    const planned = baseline.cumulative[index] ?? total;
    const earnings = earnAll(context, period.cutOff);
    const earned = sumMoney(
      earnings.map((e) => e.earnedValue),
      total.currency,
    );
    return {
      period: period.number,
      cutOff: period.cutOff,
      planned,
      earned,
      variance: subtractMoney(earned, planned),
      plannedShare: shareOf(planned, total),
      earnedShare: shareOf(earned, total),
    };
  });
}

function shareOf(part: Money, whole: Money): Percent {
  return whole.minor === 0 ? ZERO_PERCENT : percentOf(part, whole);
}

/** The variance at the last cut-off on the curve. */
export function closingVariance(points: readonly CurvePoint[]): Money | null {
  return points[points.length - 1]?.variance ?? null;
}

/** The first period the job fell behind its baseline, if it did. */
export function firstBehindPeriod(points: readonly CurvePoint[]): number | null {
  return points.find((p) => p.variance.minor < 0)?.period ?? null;
}

export function isBehind(point: CurvePoint): boolean {
  return point.variance.minor < 0;
}

/** Periods where the earned figure went down, which needs explaining. */
export function regressions(points: readonly CurvePoint[]): CurvePoint[] {
  const out: CurvePoint[] = [];
  for (let i = 1; i < points.length; i += 1) {
    const before = points[i - 1];
    const now = points[i];
    if (before === undefined || now === undefined) continue;
    if (now.earned.minor < before.earned.minor) out.push(now);
  }
  return out;
}
