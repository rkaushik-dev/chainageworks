import { BadInputError } from '../../errors.js';
import type { ChainageRange } from '../chainage/range.js';
import { coveredLength, intersectRangeLists, unionRanges } from '../chainage/range.js';
import type { SiteDate } from '../time/siteDate.js';
import { compareSiteDates, formatSiteDate } from '../time/siteDate.js';
import type { InspectionPlan, InspectionPoint } from './plan.js';
import { holdPointsForStep } from './plan.js';

export type SignOffOutcome = 'passed' | 'passed_with_comment' | 'failed';

/**
 * One inspection actually carried out, over a stretch of the works.
 *
 * A sign-off is not a flag on the package. It covers the chainage that was
 * inspected and nothing else, so a hold point signed for the first two hundred
 * metres opens the first two hundred metres and leaves the rest held.
 */
export interface SignOff {
  readonly id: string;
  readonly pointId: string;
  readonly packageId: string;
  readonly extent: ChainageRange;
  readonly signedOn: SiteDate;
  readonly by: string;
  readonly outcome: SignOffOutcome;
  readonly note: string;
}

export interface SignOffInput {
  readonly id: string;
  readonly pointId: string;
  readonly packageId: string;
  readonly extent: ChainageRange;
  readonly signedOn: SiteDate;
  readonly by: string;
  readonly outcome?: SignOffOutcome;
  readonly note?: string;
}

export function buildSignOff(input: SignOffInput): SignOff {
  if (input.by.trim() === '') {
    throw new BadInputError('a sign off has to name who signed it', { signOff: input.id });
  }
  return {
    id: input.id,
    pointId: input.pointId,
    packageId: input.packageId,
    extent: input.extent,
    signedOn: input.signedOn,
    by: input.by.trim(),
    outcome: input.outcome ?? 'passed',
    note: input.note ?? '',
  };
}

/** A failed inspection is a record of an inspection, not a release. */
export function releases(signOff: SignOff): boolean {
  return signOff.outcome !== 'failed';
}

export type GateState = 'open' | 'part_open' | 'held';

export interface StepGate {
  readonly stepId: string;
  readonly state: GateState;
  /** Stretches every hold point on the step has released. */
  readonly openExtent: readonly ChainageRange[];
  /** Hold points that have released nothing at all inside the extent asked about. */
  readonly heldPoints: readonly string[];
}

export interface GateQuery {
  readonly plan: InspectionPlan;
  readonly signOffs: readonly SignOff[];
  readonly stepId: string;
  readonly packageId: string;
  readonly extent: ChainageRange;
  /** Sign offs after this date do not count. */
  readonly asAt: SiteDate;
}

/**
 * Which part of a stretch a step may be credited over.
 *
 * Every hold point on the step has to have released a length before that length
 * counts, so the answer is the intersection of what each point released. A step
 * with no hold points is open over the whole extent, which is the ordinary case
 * and has to stay cheap.
 *
 * The as-at date is what makes this a valuation question rather than a site
 * question. A sign-off dated after the cut-off exists on site and does not
 * exist in this month's certificate.
 */
export function gateForStep(query: GateQuery): StepGate {
  const holds = holdPointsForStep(query.plan, query.stepId);
  if (holds.length === 0) {
    return {
      stepId: query.stepId,
      state: 'open',
      openExtent: [query.extent],
      heldPoints: [],
    };
  }

  let open: ChainageRange[] = [query.extent];
  const heldPoints: string[] = [];
  for (const point of holds) {
    const released = releasedExtent(query, point);
    if (released.length === 0) heldPoints.push(point.id);
    open = intersectRangeLists(open, released);
  }

  const wanted = coveredLength([query.extent]);
  const got = coveredLength(open);
  const state: GateState = got === 0 ? 'held' : got >= wanted ? 'open' : 'part_open';
  return { stepId: query.stepId, state, openExtent: open, heldPoints };
}

function releasedExtent(query: GateQuery, point: InspectionPoint): ChainageRange[] {
  const relevant = query.signOffs.filter(
    (s) =>
      s.pointId === point.id &&
      s.packageId === query.packageId &&
      releases(s) &&
      compareSiteDates(s.signedOn, query.asAt) <= 0,
  );
  return intersectRangeLists(unionRanges(relevant.map((s) => s.extent)), [query.extent]);
}

/** The share of a stretch a step is free to be credited over, nought to one. */
export function openFraction(gate: StepGate, extent: ChainageRange): number {
  const total = coveredLength([extent]);
  if (total === 0) return gate.state === 'held' ? 0 : 1;
  return coveredLength([...gate.openExtent]) / total;
}

export function describeSignOff(signOff: SignOff): string {
  return (
    `${signOff.pointId} ${signOff.outcome} on ` +
    `${formatSiteDate(signOff.signedOn)} by ${signOff.by}`
  );
}
