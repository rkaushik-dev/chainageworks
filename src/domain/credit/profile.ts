import { BadInputError, NotFoundError } from '../../errors.js';
import type { Percent } from '../money/percent.js';
import {
  addPercents,
  BASIS_POINTS_WHOLE,
  clampPercent,
  formatPercent,
  FULL_PERCENT,
  percentFromBasisPoints,
  ZERO_PERCENT,
} from '../money/percent.js';

/**
 * One step in a rule of credit.
 *
 * A rule of credit is how a measured item earns before it is finished. Nobody
 * pays for a pipe run when the trench is open and nobody waits for the whole
 * run before paying anything, so the value is split across the steps that can
 * actually be seen on site.
 */
export interface CreditStep {
  readonly id: string;
  readonly name: string;
  /** Share of the item's value this step is worth. */
  readonly weight: Percent;
  /**
   * Whether the step is worth nothing until every step before it is finished.
   *
   * This is what stops a lazy claim. Backfill cannot be credited while the pipe
   * is unlaid even though the trench beside it has been backfilled, and a step
   * marked this way is the rule that decides whether the other rules count.
   */
  readonly requiresPrevious: boolean;
}

export interface CreditProfile {
  readonly id: string;
  readonly name: string;
  readonly steps: readonly CreditStep[];
}

export interface StepInput {
  readonly id: string;
  readonly name: string;
  readonly weight: Percent;
  readonly requiresPrevious?: boolean;
}

export interface ProfileInput {
  readonly id: string;
  readonly name: string;
  readonly steps: readonly StepInput[];
}

/** How far through one step the work is. */
export interface StepProgress {
  readonly stepId: string;
  readonly fraction: Percent;
}

export function buildCreditProfile(input: ProfileInput): CreditProfile {
  if (input.steps.length === 0) {
    throw new BadInputError('a rule of credit needs at least one step', { profile: input.id });
  }
  const seen = new Set<string>();
  let total = 0;
  for (const step of input.steps) {
    if (seen.has(step.id)) {
      throw new BadInputError('two steps in one profile share an id', {
        profile: input.id,
        step: step.id,
      });
    }
    seen.add(step.id);
    if (step.weight.basisPoints < 0) {
      throw new BadInputError('a step weight cannot be negative', {
        profile: input.id,
        step: step.id,
      });
    }
    total += step.weight.basisPoints;
  }
  if (total !== BASIS_POINTS_WHOLE) {
    throw new BadInputError('the step weights of a rule of credit must add to 100 percent', {
      profile: input.id,
      total: formatPercent(percentFromBasisPoints(total), 2),
    });
  }
  return {
    id: input.id,
    name: input.name,
    steps: input.steps.map((step) => ({
      id: step.id,
      name: step.name,
      weight: step.weight,
      requiresPrevious: step.requiresPrevious ?? false,
    })),
  };
}

export function stepIndex(profile: CreditProfile, stepId: string): number {
  return profile.steps.findIndex((s) => s.id === stepId);
}

export function findStep(profile: CreditProfile, stepId: string): CreditStep | null {
  return profile.steps.find((s) => s.id === stepId) ?? null;
}

export function requireStep(profile: CreditProfile, stepId: string): CreditStep {
  const step = findStep(profile, stepId);
  if (step === null) {
    throw new NotFoundError('credit step', `${profile.id}/${stepId}`);
  }
  return step;
}

/** Weight of every step up to and including the one named. */
export function cumulativeWeight(profile: CreditProfile, stepId: string): Percent {
  const index = stepIndex(profile, stepId);
  if (index < 0) {
    throw new NotFoundError('credit step', `${profile.id}/${stepId}`);
  }
  return profile.steps
    .slice(0, index + 1)
    .reduce((total, step) => addPercents(total, step.weight), ZERO_PERCENT);
}

function fractionFor(progress: readonly StepProgress[], stepId: string): Percent {
  const found = progress.find((p) => p.stepId === stepId);
  if (found === undefined) return ZERO_PERCENT;
  return clampPercent(found.fraction, ZERO_PERCENT, FULL_PERCENT);
}

/**
 * How much of an item's value the recorded progress has earned.
 *
 * Steps are walked in order. A step that requires the ones before it earns
 * nothing while any of them is short, and it does not block the steps after it
 * from being read, only from counting.
 */
export function earnedFraction(
  profile: CreditProfile,
  progress: readonly StepProgress[],
): Percent {
  let earned = 0;
  let allPreviousComplete = true;
  for (const step of profile.steps) {
    const fraction = fractionFor(progress, step.id);
    const counts = !step.requiresPrevious || allPreviousComplete;
    if (counts) {
      earned += Math.round(
        (step.weight.basisPoints * fraction.basisPoints) / BASIS_POINTS_WHOLE,
      );
    }
    if (fraction.basisPoints < BASIS_POINTS_WHOLE) allPreviousComplete = false;
  }
  return percentFromBasisPoints(earned);
}

/** Steps whose weight is being held back because an earlier step is short. */
export function blockedSteps(
  profile: CreditProfile,
  progress: readonly StepProgress[],
): CreditStep[] {
  const blocked: CreditStep[] = [];
  let allPreviousComplete = true;
  for (const step of profile.steps) {
    const fraction = fractionFor(progress, step.id);
    if (step.requiresPrevious && !allPreviousComplete && fraction.basisPoints > 0) {
      blocked.push(step);
    }
    if (fraction.basisPoints < BASIS_POINTS_WHOLE) allPreviousComplete = false;
  }
  return blocked;
}

/** The first step that is not finished, which is what a lookahead reports. */
export function nextStep(
  profile: CreditProfile,
  progress: readonly StepProgress[],
): CreditStep | null {
  for (const step of profile.steps) {
    if (fractionFor(progress, step.id).basisPoints < BASIS_POINTS_WHOLE) return step;
  }
  return null;
}

export function isProfileComplete(
  profile: CreditProfile,
  progress: readonly StepProgress[],
): boolean {
  return nextStep(profile, progress) === null;
}

export function describeProfile(profile: CreditProfile): string {
  const steps = profile.steps.map((s) => `${s.name} ${formatPercent(s.weight)}`).join(', ');
  return `${profile.name}: ${steps}`;
}
