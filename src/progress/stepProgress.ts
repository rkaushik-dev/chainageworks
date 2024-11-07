import type { ChainageRange } from '../domain/chainage/range.js';
import {
  coveredLength,
  intersectRangeLists,
  subtractRange,
  unionRanges,
} from '../domain/chainage/range.js';
import type { StepProgress } from '../domain/credit/profile.js';
import type { GateState, StepGate } from '../domain/itp/signOff.js';
import { gateForStep } from '../domain/itp/signOff.js';
import { applyWithholding, withheldExtent } from '../domain/itp/ncr.js';
import type { WorkPackage } from '../domain/packages/workPackage.js';
import { packageLength } from '../domain/packages/workPackage.js';
import { percentFromBasisPoints, BASIS_POINTS_WHOLE } from '../domain/money/percent.js';
import { effectiveRecords } from '../domain/survey/record.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import type { ProgressContext } from './context.js';
import { planForPackage, profileForPackage, recordsFor } from './context.js';

/**
 * How far a step has got along a package, and why it is not further.
 *
 * The built extent is what the survey book says. The credited extent is what
 * survives the hold points and the open non conformances, and it is the one the
 * money is worked out from. Reporting both is the point: a step that is
 * physically finished and entirely held reads very differently from one that
 * has not been started, and the two look identical if only the credited figure
 * is kept.
 */
export interface StepProgressDetail {
  readonly stepId: string;
  readonly builtExtent: readonly ChainageRange[];
  readonly creditedExtent: readonly ChainageRange[];
  readonly gate: GateState;
  readonly heldPoints: readonly string[];
  readonly withheldExtent: readonly ChainageRange[];
  /** Credited length over package length, as a fraction of one. */
  readonly fraction: number;
}

function fractionOf(pkg: WorkPackage, extent: readonly ChainageRange[]): number {
  const total = packageLength(pkg);
  const covered = coveredLength(extent);
  // A point package has no length to divide by, so any record at all is the
  // whole of it. Manholes and chambers are booked this way.
  if (total === 0) return covered > 0 || extent.length > 0 ? 1 : 0;
  return Math.min(1, covered / total);
}

/** Where the survey book says a step has been built, as at a date. */
export function builtExtentFor(
  context: ProgressContext,
  pkg: WorkPackage,
  stepId: string,
  asAt: SiteDate,
): ChainageRange[] {
  const live = effectiveRecords(recordsFor(context, pkg.id), asAt);
  const forStep = live.filter((r) => r.stepId === stepId);
  return intersectRangeLists(unionRanges(forStep.map((r) => r.extent)), [pkg.extent]);
}

export function gateFor(
  context: ProgressContext,
  pkg: WorkPackage,
  stepId: string,
  asAt: SiteDate,
): StepGate {
  const plan = planForPackage(context, pkg);
  if (plan === null) {
    return { stepId, state: 'open', openExtent: [pkg.extent], heldPoints: [] };
  }
  return gateForStep({
    plan,
    signOffs: context.signOffs,
    stepId,
    packageId: pkg.id,
    extent: pkg.extent,
    asAt,
  });
}

/**
 * One step of one package, worked out in the order the contract works it out:
 * what was built, what the hold points released, what the open reports are
 * still holding back, and only then what may be credited.
 */
export function stepProgressFor(
  context: ProgressContext,
  pkg: WorkPackage,
  stepId: string,
  asAt: SiteDate,
): StepProgressDetail {
  const built = builtExtentFor(context, pkg, stepId, asAt);
  const withheld = withheldExtent({
    ncrs: context.ncrs,
    packageId: pkg.id,
    stepId,
    asAt,
  });
  const gate = applyWithholding(gateFor(context, pkg, stepId, asAt), withheld);
  const credited = intersectRangeLists(built, [...gate.openExtent]);
  return {
    stepId,
    builtExtent: built,
    creditedExtent: credited,
    gate: gate.state,
    heldPoints: gate.heldPoints,
    withheldExtent: withheld,
    fraction: fractionOf(pkg, credited),
  };
}

export function packageStepProgress(
  context: ProgressContext,
  pkg: WorkPackage,
  asAt: SiteDate,
): StepProgressDetail[] {
  return profileForPackage(context, pkg).steps.map((step) =>
    stepProgressFor(context, pkg, step.id, asAt),
  );
}

/** The shape the rule of credit wants, taken from the detail. */
export function toStepProgress(details: readonly StepProgressDetail[]): StepProgress[] {
  return details.map((detail) => ({
    stepId: detail.stepId,
    fraction: percentFromBasisPoints(Math.round(detail.fraction * BASIS_POINTS_WHOLE)),
  }));
}

/** What has been built but cannot be credited yet, for the held work report. */
export function heldBackExtent(detail: StepProgressDetail): ChainageRange[] {
  let remaining: ChainageRange[] = [...detail.builtExtent];
  for (const open of detail.creditedExtent) {
    remaining = remaining.flatMap((r) => subtractRange(r, open));
  }
  return unionRanges(remaining);
}
