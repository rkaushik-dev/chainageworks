import { BadInputError } from '../errors.js';
import type { Money } from '../domain/money/money.js';
import { minMoney, subtractMoney, zeroMoney } from '../domain/money/money.js';
import type { Percent } from '../domain/money/percent.js';
import {
  applyPercent,
  complementPercent,
  FULL_PERCENT,
  percent,
  ZERO_PERCENT,
} from '../domain/money/percent.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { compareSiteDates } from '../domain/time/siteDate.js';

/**
 * The retention terms of the contract.
 *
 * Two percentages that are easy to run together and mean different things. The
 * rate is what is held off each valuation. The limit is a cap on the running
 * total, expressed against the contract sum, and once it bites the rate stops
 * mattering entirely.
 */
export interface RetentionTerms {
  readonly rate: Percent;
  /** Cap on the total held, as a share of the contract sum. */
  readonly limit: Percent;
  /** Share of the held total released at substantial completion. */
  readonly releaseOnCompletion: Percent;
}

export const DEFAULT_RETENTION: RetentionTerms = {
  rate: percent(5),
  limit: percent(3),
  releaseOnCompletion: percent(50),
};

export function buildRetentionTerms(input: RetentionTerms): RetentionTerms {
  for (const [name, value] of [
    ['rate', input.rate],
    ['limit', input.limit],
    ['releaseOnCompletion', input.releaseOnCompletion],
  ] as const) {
    if (value.basisPoints < 0 || value.basisPoints > FULL_PERCENT.basisPoints) {
      throw new BadInputError('a retention percentage runs from 0 to 100', { field: name });
    }
  }
  return input;
}

export interface RetentionQuery {
  readonly grossValuation: Money;
  readonly contractSum: Money;
  readonly terms: RetentionTerms;
  readonly substantialCompletionOn?: SiteDate | null;
  readonly defectsCertificateOn?: SiteDate | null;
  readonly asAt: SiteDate;
}

export interface RetentionState {
  /** What the rate alone would hold. */
  readonly uncapped: Money;
  /** What the limit allows to be held at all. */
  readonly cap: Money;
  /** The lower of the two, which is what accrues. */
  readonly accrued: Money;
  readonly releasedOnCompletion: Money;
  readonly releasedOnDefects: Money;
  readonly held: Money;
  readonly capReached: boolean;
}

/**
 * How much is being held at a cut-off.
 *
 * The two releases are separate on purpose. Half comes back when the works are
 * taken over and the rest only after the defects period, and a valuation that
 * releases the lot at completion overpays for a year.
 */
export function computeRetention(query: RetentionQuery): RetentionState {
  const currency = query.grossValuation.currency;
  const uncapped = applyPercent(query.grossValuation, query.terms.rate);
  const cap = applyPercent(query.contractSum, query.terms.limit);
  const accrued = minMoney(uncapped, cap);

  const takenOver = hasPassed(query.substantialCompletionOn, query.asAt);
  const defectsCleared = hasPassed(query.defectsCertificateOn, query.asAt);

  const releasedOnCompletion = takenOver
    ? applyPercent(accrued, query.terms.releaseOnCompletion)
    : zeroMoney(currency);
  const releasedOnDefects = defectsCleared
    ? subtractMoney(accrued, releasedOnCompletion)
    : zeroMoney(currency);

  return {
    uncapped,
    cap,
    accrued,
    releasedOnCompletion,
    releasedOnDefects,
    held: subtractMoney(subtractMoney(accrued, releasedOnCompletion), releasedOnDefects),
    capReached: uncapped.minor > cap.minor,
  };
}

function hasPassed(date: SiteDate | null | undefined, asAt: SiteDate): boolean {
  return date !== null && date !== undefined && compareSiteDates(date, asAt) <= 0;
}

/** The share of the gross valuation actually being held, after the cap. */
export function effectiveRate(state: RetentionState, gross: Money): Percent {
  if (gross.minor === 0) return ZERO_PERCENT;
  const scaled = (state.held.minor * FULL_PERCENT.basisPoints) / gross.minor;
  return { basisPoints: Math.round(scaled) };
}

/** What is left to release, for the defects liability report. */
export function outstandingRelease(state: RetentionState): Money {
  return state.held;
}

/** The proportion of a valuation that is paid across after retention. */
export function payableShare(terms: RetentionTerms): Percent {
  return complementPercent(terms.rate);
}
