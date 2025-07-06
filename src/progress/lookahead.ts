import { BadInputError } from '../errors.js';
import type { ChainageRange } from '../domain/chainage/range.js';
import { coveredLength, subtractRange, unionRanges } from '../domain/chainage/range.js';
import { MM_PER_METRE } from '../domain/chainage/station.js';
import type { WorkPackage } from '../domain/packages/workPackage.js';
import type { SiteCalendar } from '../domain/time/calendar.js';
import { addWorkingDays, workingDaysIn } from '../domain/time/calendar.js';
import { dateRange } from '../domain/time/dateRange.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { addDays, compareSiteDates, formatSiteDate } from '../domain/time/siteDate.js';
import type { ProgressContext } from './context.js';
import { profileForPackage } from './context.js';
import type { OutputAllowance } from './productivity.js';
import { packageStepProgress } from './stepProgress.js';

/**
 * What is left to build on one step, and how long it should take.
 *
 * The remaining extent is what has not been built, not what has not been
 * credited. A length held back by an unsigned hold point has already been
 * built, and putting it on a lookahead sends a gang to do it twice.
 */
export interface StepLookahead {
  readonly packageId: string;
  readonly stepId: string;
  readonly remaining: readonly ChainageRange[];
  readonly remainingMetres: number;
  /** Working days at the allowance, null when no allowance is set. */
  readonly days: number | null;
  /** Steps of this package that have to finish first and have not. */
  readonly waitingOn: readonly string[];
  /** Hold points holding the step up rather than the work itself. */
  readonly heldPoints: readonly string[];
}

export interface Lookahead {
  readonly from: SiteDate;
  readonly to: SiteDate;
  readonly workingDays: number;
  readonly steps: readonly StepLookahead[];
  /** Total working days of work in front of the job, at the allowances. */
  readonly daysOfWork: number;
}

export interface LookaheadInput {
  readonly context: ProgressContext;
  readonly packages: readonly WorkPackage[];
  readonly calendar: SiteCalendar;
  readonly allowances: readonly OutputAllowance[];
  /** Gangs available for a step, so a two gang push halves the days. */
  readonly gangs?: number;
  readonly from: SiteDate;
  readonly weeks?: number;
}

/**
 * What the next few weeks have to get through.
 *
 * Held in working days rather than calendar days, and measured against the
 * site calendar, because a lookahead that says four days over a bank holiday
 * weekend is a lookahead nobody on site will use twice.
 */
export function buildLookahead(input: LookaheadInput): Lookahead {
  const weeks = input.weeks ?? 4;
  const gangs = input.gangs ?? 1;
  if (!Number.isInteger(weeks) || weeks < 1) {
    throw new BadInputError('a lookahead runs at least one week', { weeks });
  }
  if (!Number.isInteger(gangs) || gangs < 1) {
    throw new BadInputError('a lookahead needs at least one gang', { gangs });
  }

  const to = addDays(input.from, weeks * 7 - 1);
  const allowanceFor = new Map(input.allowances.map((a) => [a.stepId, a.metresPerHour]));
  const steps: StepLookahead[] = [];

  for (const pkg of input.packages) {
    const details = packageStepProgress(input.context, pkg, input.from);
    const profile = profileForPackage(input.context, pkg);
    const done = new Set<string>();

    for (const [index, detail] of details.entries()) {
      const remaining = unionRanges([...detail.builtExtent]).reduce<ChainageRange[]>(
        (left, built) => left.flatMap((part) => subtractRange(part, built)),
        [pkg.extent],
      );
      const remainingMetres = coveredLength(remaining) / MM_PER_METRE;
      if (remainingMetres === 0) {
        done.add(detail.stepId);
        continue;
      }
      const allowance = allowanceFor.get(detail.stepId) ?? null;
      const step = profile.steps[index];
      const waitingOn =
        step?.requiresPrevious === true
          ? details
              .slice(0, index)
              .filter((earlier) => !done.has(earlier.stepId))
              .map((earlier) => earlier.stepId)
          : [];

      steps.push({
        packageId: pkg.id,
        stepId: detail.stepId,
        remaining,
        remainingMetres,
        // Eight hours to a shift, which is the shift the allowances are set on.
        days: allowance === null ? null : remainingMetres / (allowance * 8 * gangs),
        waitingOn,
        heldPoints: detail.heldPoints,
      });
    }
  }

  return {
    from: input.from,
    to,
    workingDays: workingDaysIn(input.calendar, dateRange(input.from, to)),
    steps,
    daysOfWork: steps.reduce((total, step) => total + (step.days ?? 0), 0),
  };
}

/** Steps that can be worked now, because nothing is in front of them. */
export function readyNow(lookahead: Lookahead): StepLookahead[] {
  return lookahead.steps.filter(
    (step) => step.waitingOn.length === 0 && step.heldPoints.length === 0,
  );
}

/** Steps waiting on an inspector rather than on a gang. */
export function waitingOnInspection(lookahead: Lookahead): StepLookahead[] {
  return lookahead.steps.filter((step) => step.heldPoints.length > 0);
}

/**
 * Whether the work in front of the job fits in the window.
 *
 * Compared against the whole of what is left rather than what a lookahead
 * would sequence, because the useful answer at this stage is whether the job
 * is going to run out of window, and the sequencing only makes that worse.
 */
export function fitsInWindow(lookahead: Lookahead): boolean {
  return lookahead.daysOfWork <= lookahead.workingDays;
}

/** The date the work in front would finish, at the allowances. */
export function finishBy(lookahead: Lookahead, calendar: SiteCalendar): SiteDate {
  return addWorkingDays(calendar, lookahead.from, Math.ceil(lookahead.daysOfWork));
}

export function describeLookahead(lookahead: Lookahead): string {
  const fits = fitsInWindow(lookahead) ? 'fits the window' : 'does not fit the window';
  return (
    `${formatSiteDate(lookahead.from)} to ${formatSiteDate(lookahead.to)}, ` +
    `${lookahead.daysOfWork.toFixed(1)} days of work in ` +
    `${String(lookahead.workingDays)} working days, ${fits}`
  );
}

/** Steps with work left and no allowance to price the time with. */
export function unpriced(lookahead: Lookahead): StepLookahead[] {
  return lookahead.steps.filter((step) => step.days === null);
}

export function isInWindow(lookahead: Lookahead, date: SiteDate): boolean {
  return (
    compareSiteDates(date, lookahead.from) >= 0 && compareSiteDates(date, lookahead.to) <= 0
  );
}
