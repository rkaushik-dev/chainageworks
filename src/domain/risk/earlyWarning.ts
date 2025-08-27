import { BadInputError } from '../../errors.js';
import type { ChainageRange } from '../chainage/range.js';
import type { Money } from '../money/money.js';
import { scaleMoney, sumMoney, zeroMoney } from '../money/money.js';
import type { Percent } from '../money/percent.js';
import { percent, percentFraction } from '../money/percent.js';
import type { SiteDate } from '../time/siteDate.js';
import { addDays, compareSiteDates, formatSiteDate } from '../time/siteDate.js';

/**
 * Who carries the risk if it happens.
 *
 * The whole point of an early warning register is that this is written down
 * before anybody knows the answer to the money. A risk logged with no owner is
 * a risk nobody is working on, which is why the field has no default.
 */
export type RiskOwner = 'contractor' | 'employer' | 'shared' | 'unresolved';

export type RiskState = 'open' | 'mitigated' | 'happened' | 'avoided';

export interface EarlyWarning {
  readonly id: string;
  readonly reference: string;
  readonly description: string;
  readonly owner: RiskOwner;
  readonly state: RiskState;
  /** Where on the alignment, when it is a place rather than a whole job risk. */
  readonly extent: ChainageRange | null;
  readonly raisedOn: SiteDate;
  /** The meeting the contract requires. Null means nobody has held one. */
  readonly meetingOn: SiteDate | null;
  readonly closedOn: SiteDate | null;
  /** What it costs if it happens, before any weighting. */
  readonly costIfItHappens: Money;
  readonly likelihood: Percent;
  /** Working days it would add to the programme. */
  readonly delayDays: number;
}

export interface EarlyWarningInput {
  readonly id: string;
  readonly reference?: string;
  readonly description: string;
  readonly owner: RiskOwner;
  readonly state?: RiskState;
  readonly extent?: ChainageRange | null;
  readonly raisedOn: SiteDate;
  readonly meetingOn?: SiteDate | null;
  readonly closedOn?: SiteDate | null;
  readonly costIfItHappens: Money;
  readonly likelihood?: Percent;
  readonly delayDays?: number;
}

/** Days from raising a warning to the meeting the contract wants. */
export const DEFAULT_MEETING_DAYS = 14;

export function buildEarlyWarning(input: EarlyWarningInput): EarlyWarning {
  if (input.description.trim() === '') {
    throw new BadInputError('an early warning has to say what the risk is', {
      warning: input.id,
    });
  }
  if (input.costIfItHappens.minor < 0) {
    throw new BadInputError('a risk cannot cost less than nothing', { warning: input.id });
  }
  const likelihood = input.likelihood ?? percent(50);
  if (likelihood.basisPoints < 0 || likelihood.basisPoints > 10_000) {
    throw new BadInputError('a likelihood runs from 0 to 100', { warning: input.id });
  }
  const delayDays = input.delayDays ?? 0;
  if (!Number.isInteger(delayDays) || delayDays < 0) {
    throw new BadInputError('a delay is a whole number of working days', {
      warning: input.id,
      delayDays,
    });
  }
  const closedOn = input.closedOn ?? null;
  if (closedOn !== null && compareSiteDates(closedOn, input.raisedOn) < 0) {
    throw new BadInputError('a warning cannot close before it was raised', {
      warning: input.id,
    });
  }
  return {
    id: input.id,
    reference: input.reference ?? input.id,
    description: input.description.trim(),
    owner: input.owner,
    state: input.state ?? 'open',
    extent: input.extent ?? null,
    raisedOn: input.raisedOn,
    meetingOn: input.meetingOn ?? null,
    closedOn,
    costIfItHappens: input.costIfItHappens,
    likelihood,
    delayDays,
  };
}

export function isOpenAt(warning: EarlyWarning, asAt: SiteDate): boolean {
  if (compareSiteDates(warning.raisedOn, asAt) > 0) return false;
  return warning.closedOn === null || compareSiteDates(warning.closedOn, asAt) > 0;
}

/**
 * A warning whose meeting was never held, or was held late.
 *
 * Held separately from the risk itself because it is a different failure. The
 * risk may still be small; the process failure is what the other side quotes
 * back when the claim arrives.
 */
export function meetingOverdue(
  warning: EarlyWarning,
  asAt: SiteDate,
  meetingDays = DEFAULT_MEETING_DAYS,
): boolean {
  const due = addDays(warning.raisedOn, meetingDays);
  if (warning.meetingOn === null) return compareSiteDates(due, asAt) < 0;
  return compareSiteDates(warning.meetingOn, due) > 0;
}

/** The cost weighted by how likely it is, which is what a forecast carries. */
export function expectedCost(warning: EarlyWarning): Money {
  if (warning.state === 'avoided') return zeroMoney(warning.costIfItHappens.currency);
  if (warning.state === 'happened') return warning.costIfItHappens;
  return scaleMoney(warning.costIfItHappens, percentFraction(warning.likelihood));
}

export interface RiskPosition {
  readonly asAt: SiteDate;
  readonly open: readonly EarlyWarning[];
  /** Everything open, weighted by likelihood. */
  readonly expected: Money;
  /** Everything open at full value, which is the worst case. */
  readonly worstCase: Money;
  /** The part of the expected cost the contractor carries. */
  readonly carried: Money;
  readonly overdueMeetings: readonly EarlyWarning[];
  /** Working days of delay in the open risks, if every one happened. */
  readonly delayDays: number;
}

/**
 * Where the register stands at a date.
 *
 * A shared risk is counted half to the contractor, which is a convention
 * rather than a rule, and it is stated here rather than buried in a report so
 * the two sides can argue about the convention instead of about the number.
 */
export function riskPosition(
  warnings: readonly EarlyWarning[],
  asAt: SiteDate,
  currency: Money['currency'],
  meetingDays = DEFAULT_MEETING_DAYS,
): RiskPosition {
  const open = warnings
    .filter((warning) => isOpenAt(warning, asAt))
    .sort((a, b) => expectedCost(b).minor - expectedCost(a).minor);

  const share = (warning: EarlyWarning): number =>
    warning.owner === 'contractor' ? 1 : warning.owner === 'shared' ? 0.5 : 0;

  return {
    asAt,
    open,
    expected: sumMoney(open.map(expectedCost), currency),
    worstCase: sumMoney(
      open.map((warning) => warning.costIfItHappens),
      currency,
    ),
    carried: sumMoney(
      open.map((warning) => scaleMoney(expectedCost(warning), share(warning))),
      currency,
    ),
    overdueMeetings: open.filter((warning) => meetingOverdue(warning, asAt, meetingDays)),
    delayDays: open.reduce((total, warning) => total + warning.delayDays, 0),
  };
}

/** Risks nobody has agreed an owner for, which is the argument waiting to start. */
export function unowned(position: RiskPosition): EarlyWarning[] {
  return position.open.filter((warning) => warning.owner === 'unresolved');
}

export function describeWarning(warning: EarlyWarning): string {
  const meeting =
    warning.meetingOn === null ? 'no meeting held' : `met ${formatSiteDate(warning.meetingOn)}`;
  return `${warning.reference} ${warning.owner} ${warning.state}, ${meeting}`;
}
