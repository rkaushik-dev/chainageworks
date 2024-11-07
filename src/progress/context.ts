import { BadInputError } from '../errors.js';
import type { BillHistory } from '../domain/bill/revision.js';
import type { ProfileRegistry } from '../domain/credit/catalogue.js';
import { requireProfile } from '../domain/credit/catalogue.js';
import type { CreditProfile } from '../domain/credit/profile.js';
import type { InspectionPlan } from '../domain/itp/plan.js';
import type { Ncr } from '../domain/itp/ncr.js';
import type { SignOff } from '../domain/itp/signOff.js';
import type { PackageRegister } from '../domain/packages/register.js';
import type { WorkPackage } from '../domain/packages/workPackage.js';
import type { SurveyRecord } from '../domain/survey/record.js';
import type { ToleranceSet } from '../domain/survey/tolerance.js';
import { DEFAULT_TOLERANCES } from '../domain/survey/tolerance.js';
import type { SiteCalendar } from '../domain/time/calendar.js';

/**
 * Everything the progress engine reads.
 *
 * Gathered into one value on purpose. The calculation is a composition of six
 * separate registers and passing them one at a time down four call levels made
 * it far too easy to answer a question against last month's sign-offs and this
 * month's bill.
 */
export interface ProgressContext {
  readonly billHistory: BillHistory;
  readonly register: PackageRegister;
  readonly profiles: ProfileRegistry;
  /** Inspection plans keyed by the rule of credit they gate. */
  readonly plans: ReadonlyMap<string, InspectionPlan>;
  readonly signOffs: readonly SignOff[];
  readonly ncrs: readonly Ncr[];
  readonly records: readonly SurveyRecord[];
  readonly calendar: SiteCalendar;
  readonly tolerances: ToleranceSet;
}

export interface ProgressContextInput {
  readonly billHistory: BillHistory;
  readonly register: PackageRegister;
  readonly profiles: ProfileRegistry;
  readonly plans?: readonly InspectionPlan[];
  readonly signOffs?: readonly SignOff[];
  readonly ncrs?: readonly Ncr[];
  readonly records?: readonly SurveyRecord[];
  readonly calendar: SiteCalendar;
  readonly tolerances?: ToleranceSet;
}

export function buildProgressContext(input: ProgressContextInput): ProgressContext {
  const plans = new Map<string, InspectionPlan>();
  for (const plan of input.plans ?? []) {
    if (plans.has(plan.profileId)) {
      throw new BadInputError('two inspection plans gate the same rule of credit', {
        profile: plan.profileId,
      });
    }
    plans.set(plan.profileId, plan);
  }
  return {
    billHistory: input.billHistory,
    register: input.register,
    profiles: input.profiles,
    plans,
    signOffs: input.signOffs ?? [],
    ncrs: input.ncrs ?? [],
    records: input.records ?? [],
    calendar: input.calendar,
    tolerances: input.tolerances ?? DEFAULT_TOLERANCES,
  };
}

export function profileForPackage(context: ProgressContext, pkg: WorkPackage): CreditProfile {
  return requireProfile(context.profiles, pkg.profileId);
}

/** A package with no plan is simply ungated, which is the ordinary case. */
export function planForPackage(
  context: ProgressContext,
  pkg: WorkPackage,
): InspectionPlan | null {
  return context.plans.get(pkg.profileId) ?? null;
}

export function signOffsFor(context: ProgressContext, packageId: string): SignOff[] {
  return context.signOffs.filter((s) => s.packageId === packageId);
}

export function ncrsFor(context: ProgressContext, packageId: string): Ncr[] {
  return context.ncrs.filter((n) => n.packageId === packageId);
}

export function recordsFor(context: ProgressContext, packageId: string): SurveyRecord[] {
  return context.records.filter((r) => r.packageId === packageId);
}
