import { BadInputError } from '../../errors.js';
import type { Money } from '../money/money.js';
import { minMoney, scaleMoney, zeroMoney } from '../money/money.js';
import type { Percent } from '../money/percent.js';
import { applyPercent } from '../money/percent.js';
import type { SiteCalendar } from '../time/calendar.js';
import { addWorkingDays } from '../time/calendar.js';
import type { SiteDate } from '../time/siteDate.js';
import { compareSiteDates, diffDays, formatSiteDate } from '../time/siteDate.js';
import type { BarReason, DelayEvent } from './event.js';
import { assessedDays, barReason, DEFAULT_NOTICE_DAYS, eventsTo } from './event.js';

/**
 * The completion terms of the contract.
 *
 * Damages are per calendar day late, because the employer's loss does not stop
 * at the weekend. Extensions of time are in working days, because the
 * contractor's loss does. The two units are genuinely different and running
 * them together is the classic way an extension comes out a week short.
 */
export interface CompletionTerms {
  readonly baseCompletion: SiteDate;
  readonly damagesPerDay: Money;
  /** Cap on the total damages, as a share of the contract sum. */
  readonly damagesCap: Percent | null;
  readonly noticeDays: number;
}

export interface CompletionTermsInput {
  readonly baseCompletion: SiteDate;
  readonly damagesPerDay: Money;
  readonly damagesCap?: Percent | null;
  readonly noticeDays?: number;
}

export function buildCompletionTerms(input: CompletionTermsInput): CompletionTerms {
  if (input.damagesPerDay.minor < 0) {
    throw new BadInputError('liquidated damages cannot be negative');
  }
  const noticeDays = input.noticeDays ?? DEFAULT_NOTICE_DAYS;
  if (!Number.isInteger(noticeDays) || noticeDays < 0) {
    throw new BadInputError('a notice period is a whole number of days', { noticeDays });
  }
  return {
    baseCompletion: input.baseCompletion,
    damagesPerDay: input.damagesPerDay,
    damagesCap: input.damagesCap ?? null,
    noticeDays,
  };
}

export interface CompletionPosition {
  readonly baseCompletion: SiteDate;
  readonly extendedCompletion: SiteDate;
  /** Working days granted by the events that were properly notified. */
  readonly awardedDays: number;
  /** Working days lost to events the time bar shut out. */
  readonly barredDays: number;
  readonly barred: readonly { event: DelayEvent; reason: BarReason; days: number }[];
}

/**
 * Where completion stands at a date.
 *
 * The barred days are reported alongside the award rather than dropped,
 * because the difference between the two is the whole of what a contractor
 * loses to poor paperwork, and nobody can act on it if the report only shows
 * the number that survived.
 */
export function completionPosition(
  terms: CompletionTerms,
  events: readonly DelayEvent[],
  calendar: SiteCalendar,
  asAt: SiteDate,
): CompletionPosition {
  let awardedDays = 0;
  let barredDays = 0;
  const barred: { event: DelayEvent; reason: BarReason; days: number }[] = [];

  for (const event of eventsTo(events, asAt)) {
    const reason = barReason(event, terms.noticeDays);
    if (reason === null) {
      awardedDays += assessedDays(event, calendar, terms.noticeDays);
      continue;
    }
    const days = event.awardedDays ?? 0;
    const lost = days > 0 ? days : eventWorkingDays(event, calendar);
    barredDays += lost;
    barred.push({ event, reason, days: lost });
  }

  return {
    baseCompletion: terms.baseCompletion,
    extendedCompletion: addWorkingDays(calendar, terms.baseCompletion, awardedDays),
    awardedDays,
    barredDays,
    barred,
  };
}

function eventWorkingDays(event: DelayEvent, calendar: SiteCalendar): number {
  return assessedDays(
    { ...event, notifiedOn: event.period.from, cause: 'variation' },
    calendar,
  );
}

/**
 * Calendar days late at a date, or nought when the works are still inside the
 * extended completion date.
 */
export function daysLate(position: CompletionPosition, asAt: SiteDate): number {
  const late = diffDays(position.extendedCompletion, asAt);
  return late > 0 ? late : 0;
}

export function isLate(position: CompletionPosition, asAt: SiteDate): boolean {
  return daysLate(position, asAt) > 0;
}

/** Damages at a date, capped where the contract caps them. */
export function liquidatedDamages(
  terms: CompletionTerms,
  position: CompletionPosition,
  contractSum: Money,
  asAt: SiteDate,
): Money {
  const late = daysLate(position, asAt);
  if (late === 0) return zeroMoney(terms.damagesPerDay.currency);
  const raw = scaleMoney(terms.damagesPerDay, late);
  if (terms.damagesCap === null) return raw;
  return minMoney(raw, applyPercent(contractSum, terms.damagesCap));
}

export function isDamagesCapped(
  terms: CompletionTerms,
  position: CompletionPosition,
  contractSum: Money,
  asAt: SiteDate,
): boolean {
  if (terms.damagesCap === null) return false;
  const raw = scaleMoney(terms.damagesPerDay, daysLate(position, asAt));
  return raw.minor > applyPercent(contractSum, terms.damagesCap).minor;
}

export function describeCompletion(position: CompletionPosition): string {
  const moved =
    compareSiteDates(position.extendedCompletion, position.baseCompletion) === 0
      ? 'not moved'
      : `moved to ${formatSiteDate(position.extendedCompletion)}`;
  return (
    `completion ${formatSiteDate(position.baseCompletion)} ${moved}, ` +
    `${String(position.awardedDays)} days awarded, ` +
    `${String(position.barredDays)} days barred`
  );
}
