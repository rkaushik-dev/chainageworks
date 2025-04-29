import { BadInputError } from '../errors.js';
import { coveredLength } from '../domain/chainage/range.js';
import { MM_PER_METRE } from '../domain/chainage/station.js';
import type { WorkPackage } from '../domain/packages/workPackage.js';
import type { ResourceReturn } from '../domain/resource/return.js';
import { hoursOfKind, returnsFor, returnsTo, totalHours } from '../domain/resource/return.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import type { ProgressContext } from './context.js';
import { profileForPackage } from './context.js';
import { packageStepProgress } from './stepProgress.js';

/**
 * What a step was expected to produce in an hour.
 *
 * Held per step of the rule of credit rather than per gang, because that is
 * how a tender is priced: the estimator allows so many hours to trim so many
 * metres of formation, and never allows hours to a particular gang. Keying it
 * on the gang would make the comparison impossible the first time the foreman
 * moved somebody between two jobs.
 */
export interface OutputAllowance {
  readonly stepId: string;
  /** Metres of the package expected for each labour hour booked. */
  readonly metresPerHour: number;
}

export function buildAllowances(
  allowances: readonly OutputAllowance[],
): readonly OutputAllowance[] {
  const seen = new Set<string>();
  for (const allowance of allowances) {
    if (seen.has(allowance.stepId)) {
      throw new BadInputError('a step has two output allowances', { step: allowance.stepId });
    }
    if (!Number.isFinite(allowance.metresPerHour) || allowance.metresPerHour <= 0) {
      throw new BadInputError('an output allowance has to be more than nothing an hour', {
        step: allowance.stepId,
        metresPerHour: allowance.metresPerHour,
      });
    }
    seen.add(allowance.stepId);
  }
  return [...allowances];
}

export interface StepOutput {
  readonly stepId: string;
  /** Metres of the package built by the cut-off, whatever the gates say. */
  readonly builtMetres: number;
  readonly labourHours: number;
  readonly plantHours: number;
  readonly lostHours: number;
  /** Metres per labour hour actually achieved, null when no hours were booked. */
  readonly achieved: number | null;
  readonly allowed: number | null;
  /** Achieved over allowed. Above one is ahead of the allowance. */
  readonly ratio: number | null;
}

export interface PackageOutput {
  readonly packageId: string;
  readonly asAt: SiteDate;
  readonly steps: readonly StepOutput[];
  /** Hours booked to the package without naming a step. */
  readonly unallocatedHours: number;
  readonly labourHours: number;
  readonly plantHours: number;
}

/**
 * Set what was built against the hours it took.
 *
 * Built extent is used rather than credited extent, and that is the whole
 * point of the report. A gang that laid four hundred metres of pipe was
 * productive whether or not the hold point has been signed, and charging the
 * inspector's diary against the ganger's output would make the number useless
 * for the only thing it is for, which is planning next week.
 */
export function packageOutput(
  context: ProgressContext,
  pkg: WorkPackage,
  returns: readonly ResourceReturn[],
  asAt: SiteDate,
  allowances: readonly OutputAllowance[] = [],
): PackageOutput {
  const mine = returnsTo(returnsFor(returns, pkg.id), asAt);
  const details = packageStepProgress(context, pkg, asAt);
  const allowanceFor = new Map(allowances.map((a) => [a.stepId, a.metresPerHour]));

  const steps = details.map((detail): StepOutput => {
    const onStep = mine.filter((r) => r.stepId === detail.stepId);
    const labour = onStep.flatMap((r) => [...r.labour]);
    const plant = onStep.flatMap((r) => [...r.plant]);
    const labourHours = totalHours(labour);
    const builtMetres = coveredLength(detail.builtExtent) / MM_PER_METRE;
    const achieved = labourHours === 0 ? null : builtMetres / labourHours;
    const allowed = allowanceFor.get(detail.stepId) ?? null;
    return {
      stepId: detail.stepId,
      builtMetres,
      labourHours,
      plantHours: totalHours(plant),
      lostHours:
        hoursOfKind([...labour, ...plant], 'standing') +
        hoursOfKind([...labour, ...plant], 'idle'),
      achieved,
      allowed,
      ratio: achieved === null || allowed === null ? null : achieved / allowed,
    };
  });

  const stepIds = new Set(profileForPackage(context, pkg).steps.map((s) => s.id));
  const loose = mine.filter((r) => r.stepId === null || !stepIds.has(r.stepId));

  return {
    packageId: pkg.id,
    asAt,
    steps,
    unallocatedHours: totalHours([
      ...loose.flatMap((r) => [...r.labour]),
      ...loose.flatMap((r) => [...r.plant]),
    ]),
    labourHours: totalHours(mine.flatMap((r) => [...r.labour])),
    plantHours: totalHours(mine.flatMap((r) => [...r.plant])),
  };
}

/** Steps running behind the allowance, worst first. */
export function behindAllowance(output: PackageOutput): StepOutput[] {
  return output.steps
    .filter((step) => step.ratio !== null && step.ratio < 1)
    .sort((a, b) => (a.ratio ?? 0) - (b.ratio ?? 0));
}

/** Steps with hours booked against them and nothing built to show for it. */
export function nothingToShow(output: PackageOutput): StepOutput[] {
  return output.steps.filter((step) => step.labourHours > 0 && step.builtMetres === 0);
}

export function describeStepOutput(step: StepOutput): string {
  if (step.achieved === null) return `${step.stepId}, no hours booked`;
  const against =
    step.ratio === null
      ? 'no allowance set'
      : `${(step.ratio * 100).toFixed(0)} percent of the allowance`;
  return `${step.stepId}, ${step.achieved.toFixed(2)} metres an hour, ${against}`;
}
