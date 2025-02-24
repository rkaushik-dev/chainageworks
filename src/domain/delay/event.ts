import { BadInputError } from '../../errors.js';
import type { ChainageRange } from '../chainage/range.js';
import type { SiteCalendar } from '../time/calendar.js';
import { workingDaysIn } from '../time/calendar.js';
import type { DateRange } from '../time/dateRange.js';
import { dateRange, rangeDays } from '../time/dateRange.js';
import type { SiteDate } from '../time/siteDate.js';
import { compareSiteDates, diffDays, formatSiteDate } from '../time/siteDate.js';

/**
 * Why the works stopped.
 *
 * The cause decides the entitlement, and the entitlement is what the argument
 * is actually about. Weather buys time and no money; a variation buys both;
 * anything the contractor caused buys neither, and recording it is still worth
 * doing because a delay with no entitlement still moves the programme.
 */
export type DelayCause =
  | 'weather'
  | 'variation'
  | 'access'
  | 'design'
  | 'utilities'
  | 'ground'
  | 'contractor';

export type Entitlement = 'time_and_money' | 'time_only' | 'none';

const ENTITLEMENT: Readonly<Record<DelayCause, Entitlement>> = {
  weather: 'time_only',
  variation: 'time_and_money',
  access: 'time_and_money',
  design: 'time_and_money',
  utilities: 'time_and_money',
  ground: 'time_and_money',
  contractor: 'none',
};

export function entitlementFor(cause: DelayCause): Entitlement {
  return ENTITLEMENT[cause];
}

export interface DelayEvent {
  readonly id: string;
  readonly reference: string;
  readonly cause: DelayCause;
  readonly description: string;
  readonly period: DateRange;
  /** Where on the alignment, when the event has a place. */
  readonly extent: ChainageRange | null;
  readonly packageIds: readonly string[];
  /** When the contractor gave notice. Null means they have not. */
  readonly notifiedOn: SiteDate | null;
  /** Working days awarded by the engineer, or null while it is unassessed. */
  readonly awardedDays: number | null;
}

export interface DelayEventInput {
  readonly id: string;
  readonly reference: string;
  readonly cause: DelayCause;
  readonly description: string;
  readonly from: SiteDate;
  readonly to: SiteDate;
  readonly extent?: ChainageRange | null;
  readonly packageIds?: readonly string[];
  readonly notifiedOn?: SiteDate | null;
  readonly awardedDays?: number | null;
}

/** Days the contract allows for giving notice before a claim is time barred. */
export const DEFAULT_NOTICE_DAYS = 21;

export function buildDelayEvent(input: DelayEventInput): DelayEvent {
  if (input.description.trim() === '') {
    throw new BadInputError('a delay event needs a description', { event: input.id });
  }
  const period = dateRange(input.from, input.to);
  const notifiedOn = input.notifiedOn ?? null;
  if (notifiedOn !== null && compareSiteDates(notifiedOn, input.from) < 0) {
    throw new BadInputError('notice cannot be given before the event started', {
      event: input.id,
      started: formatSiteDate(input.from),
      notified: formatSiteDate(notifiedOn),
    });
  }
  const awardedDays = input.awardedDays ?? null;
  if (awardedDays !== null && (!Number.isInteger(awardedDays) || awardedDays < 0)) {
    throw new BadInputError('an award is a whole number of working days', {
      event: input.id,
      awardedDays,
    });
  }
  return {
    id: input.id,
    reference: input.reference,
    cause: input.cause,
    description: input.description.trim(),
    period,
    extent: input.extent ?? null,
    packageIds: [...(input.packageIds ?? [])],
    notifiedOn,
    awardedDays,
  };
}

export type BarReason = 'not_notified' | 'notified_late' | 'no_entitlement';

/**
 * Why an event cannot carry an award, or null when it can.
 *
 * The time bar is the harshest rule on a civil contract and the one people are
 * most surprised by: an event that genuinely delayed the works buys nothing at
 * all if notice went in a day late. Reporting it as a reason rather than
 * silently awarding nothing is the difference between a contractor who can
 * argue and one who does not know what happened.
 */
export function barReason(
  event: DelayEvent,
  noticeDays: number = DEFAULT_NOTICE_DAYS,
): BarReason | null {
  if (entitlementFor(event.cause) === 'none') return 'no_entitlement';
  if (event.notifiedOn === null) return 'not_notified';
  if (diffDays(event.period.from, event.notifiedOn) > noticeDays) return 'notified_late';
  return null;
}

export function isClaimable(event: DelayEvent, noticeDays = DEFAULT_NOTICE_DAYS): boolean {
  return barReason(event, noticeDays) === null;
}

export function carriesMoney(event: DelayEvent, noticeDays = DEFAULT_NOTICE_DAYS): boolean {
  return isClaimable(event, noticeDays) && entitlementFor(event.cause) === 'time_and_money';
}

/** Calendar days the event ran for, whether or not anybody was working. */
export function elapsedDays(event: DelayEvent): number {
  return rangeDays(event.period);
}

/** Working days lost, which is what an award is measured in. */
export function workingDaysLost(event: DelayEvent, calendar: SiteCalendar): number {
  return workingDaysIn(calendar, event.period);
}

/**
 * The award, or what the event is worth if it has not been assessed yet.
 *
 * An unassessed event is worth the working days it ran for, which is the
 * contractor's own case rather than the engineer's. Reporting that separately
 * from an award keeps the forecast honest about what is still to be argued.
 */
export function assessedDays(
  event: DelayEvent,
  calendar: SiteCalendar,
  noticeDays = DEFAULT_NOTICE_DAYS,
): number {
  if (!isClaimable(event, noticeDays)) return 0;
  return event.awardedDays ?? workingDaysLost(event, calendar);
}

export function isAssessed(event: DelayEvent): boolean {
  return event.awardedDays !== null;
}

/** Events that have run by a date, so a report does not include future ones. */
export function eventsTo(events: readonly DelayEvent[], asAt: SiteDate): DelayEvent[] {
  return events
    .filter((event) => compareSiteDates(event.period.from, asAt) <= 0)
    .sort((a, b) => compareSiteDates(a.period.from, b.period.from) || (a.id < b.id ? -1 : 1));
}

export function describeDelayEvent(event: DelayEvent): string {
  const bar = barReason(event);
  const state = bar === null ? 'claimable' : bar.replace(/_/g, ' ');
  return `${event.reference} ${event.cause}, ${formatSiteDate(event.period.from)} to ${formatSiteDate(
    event.period.to,
  )}, ${state}`;
}
