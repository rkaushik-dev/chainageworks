import { BadInputError, NotFoundError } from '../../errors.js';
import type { CreditProfile } from '../credit/profile.js';
import { findStep } from '../credit/profile.js';

/**
 * What kind of gate an inspection point is.
 *
 * A hold point stops the works: nothing past it may be credited until somebody
 * signs. A witness point gives the engineer the chance to attend and does not
 * stop anything if they do not. A review point is paperwork, checked later.
 * Treating all three the same is how a job either stalls or overclaims.
 */
export type PointType = 'hold' | 'witness' | 'review';

export interface InspectionPoint {
  readonly id: string;
  readonly name: string;
  readonly type: PointType;
  /** The credit step this point gates. */
  readonly stepId: string;
  readonly discipline: string;
  /** What the inspection is against, quoted on the request form. */
  readonly reference: string;
}

export interface InspectionPlan {
  readonly id: string;
  readonly name: string;
  /** The rule of credit whose steps these points gate. */
  readonly profileId: string;
  readonly points: readonly InspectionPoint[];
}

export interface InspectionPointInput {
  readonly id: string;
  readonly name: string;
  readonly type: PointType;
  readonly stepId: string;
  readonly discipline?: string;
  readonly reference?: string;
}

export interface InspectionPlanInput {
  readonly id: string;
  readonly name: string;
  readonly profileId: string;
  readonly points: readonly InspectionPointInput[];
}

/**
 * The plan is validated against the rule of credit it gates, because a point
 * hung on a step that does not exist gates nothing and nobody finds out until
 * the work it was meant to stop has been buried.
 */
export function buildInspectionPlan(
  input: InspectionPlanInput,
  profile: CreditProfile,
): InspectionPlan {
  if (profile.id !== input.profileId) {
    throw new BadInputError('the plan names a different rule of credit', {
      plan: input.id,
      planProfile: input.profileId,
      profile: profile.id,
    });
  }
  const seen = new Set<string>();
  for (const point of input.points) {
    if (seen.has(point.id)) {
      throw new BadInputError('two inspection points share an id', {
        plan: input.id,
        point: point.id,
      });
    }
    seen.add(point.id);
    if (findStep(profile, point.stepId) === null) {
      throw new BadInputError('an inspection point gates a step the profile does not have', {
        plan: input.id,
        point: point.id,
        step: point.stepId,
      });
    }
  }
  return {
    id: input.id,
    name: input.name,
    profileId: input.profileId,
    points: input.points.map((point) => ({
      id: point.id,
      name: point.name,
      type: point.type,
      stepId: point.stepId,
      discipline: point.discipline ?? 'civil',
      reference: point.reference ?? '',
    })),
  };
}

export function findPoint(plan: InspectionPlan, pointId: string): InspectionPoint | null {
  return plan.points.find((p) => p.id === pointId) ?? null;
}

export function requirePoint(plan: InspectionPlan, pointId: string): InspectionPoint {
  const point = findPoint(plan, pointId);
  if (point === null) {
    throw new NotFoundError('inspection point', `${plan.id}/${pointId}`);
  }
  return point;
}

export function pointsForStep(plan: InspectionPlan, stepId: string): InspectionPoint[] {
  return plan.points.filter((p) => p.stepId === stepId);
}

/** Only these stop credit. Witness and review points never do. */
export function holdPointsForStep(plan: InspectionPlan, stepId: string): InspectionPoint[] {
  return pointsForStep(plan, stepId).filter((p) => p.type === 'hold');
}

export function witnessPointsForStep(plan: InspectionPlan, stepId: string): InspectionPoint[] {
  return pointsForStep(plan, stepId).filter((p) => p.type === 'witness');
}

export function gatedSteps(plan: InspectionPlan): string[] {
  return [...new Set(plan.points.filter((p) => p.type === 'hold').map((p) => p.stepId))];
}

export function disciplines(plan: InspectionPlan): string[] {
  return [...new Set(plan.points.map((p) => p.discipline))].sort();
}

export function describePoint(point: InspectionPoint): string {
  return `${point.id} ${point.type} point, ${point.name}`;
}
