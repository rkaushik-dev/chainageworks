import type { Money } from '../domain/money/money.js';
import { formatMoney, zeroMoney } from '../domain/money/money.js';
import type { BarReason, DelayEvent } from '../domain/delay/event.js';
import { barReason, carriesMoney, entitlementFor, eventsTo } from '../domain/delay/event.js';
import type { CompletionPosition, CompletionTerms } from '../domain/delay/completion.js';
import {
  completionPosition,
  daysLate,
  isDamagesCapped,
  liquidatedDamages,
} from '../domain/delay/completion.js';
import type { SiteCalendar } from '../domain/time/calendar.js';
import { workingDaysIn } from '../domain/time/calendar.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { renderHeading, renderPairs, renderTable } from './format/table.js';

export interface DelayLine {
  readonly id: string;
  readonly reference: string;
  readonly cause: string;
  readonly entitlement: string;
  readonly from: SiteDate;
  readonly to: SiteDate;
  readonly workingDays: number;
  readonly awardedDays: number | null;
  readonly bar: BarReason | null;
  readonly carriesMoney: boolean;
}

export interface DelayReport {
  readonly asAt: SiteDate;
  readonly lines: readonly DelayLine[];
  readonly position: CompletionPosition;
  readonly daysLate: number;
  readonly damages: Money;
  readonly damagesCapped: boolean;
}

/**
 * The extension of time position at a date.
 *
 * The report is built around the gap between what was claimed and what was
 * awarded, because that gap is what the two sides are actually arguing about
 * and it is invisible on a programme.
 */
export function buildDelayReport(
  terms: CompletionTerms | null,
  events: readonly DelayEvent[],
  calendar: SiteCalendar,
  contractSum: Money,
  asAt: SiteDate,
): DelayReport | null {
  if (terms === null) return null;
  const position = completionPosition(terms, events, calendar, asAt);
  const lines = eventsTo(events, asAt).map(
    (event): DelayLine => ({
      id: event.id,
      reference: event.reference,
      cause: event.cause,
      entitlement: entitlementFor(event.cause),
      from: event.period.from,
      to: event.period.to,
      workingDays: workingDaysIn(calendar, event.period),
      awardedDays: event.awardedDays,
      bar: barReason(event, terms.noticeDays),
      carriesMoney: carriesMoney(event, terms.noticeDays),
    }),
  );
  return {
    asAt,
    lines,
    position,
    daysLate: daysLate(position, asAt),
    damages: liquidatedDamages(terms, position, contractSum, asAt),
    damagesCapped: isDamagesCapped(terms, position, contractSum, asAt),
  };
}

/** Events that would have bought time and did not, because of the paperwork. */
export function barredLines(report: DelayReport): DelayLine[] {
  return report.lines.filter((line) => line.bar !== null && line.bar !== 'no_entitlement');
}

/** Events still waiting on an assessment. */
export function unassessedLines(report: DelayReport): DelayLine[] {
  return report.lines.filter((line) => line.bar === null && line.awardedDays === null);
}

export function claimedDays(report: DelayReport): number {
  return report.lines.reduce((total, line) => total + line.workingDays, 0);
}

export function renderDelayReport(report: DelayReport): string {
  const sections: string[] = [
    renderHeading(`extension of time position at ${formatSiteDate(report.asAt)}`),
    '',
    renderPairs([
      ['completion', formatSiteDate(report.position.baseCompletion)],
      ['extended to', formatSiteDate(report.position.extendedCompletion)],
      ['days claimed', String(claimedDays(report))],
      ['days awarded', String(report.position.awardedDays)],
      ['days barred', String(report.position.barredDays)],
      ['days late', String(report.daysLate)],
      ['damages', formatMoney(report.damages)],
    ]),
  ];

  if (report.lines.length > 0) {
    sections.push(
      '',
      renderTable(
        [
          { heading: 'event' },
          { heading: 'cause' },
          { heading: 'from' },
          { heading: 'to' },
          { heading: 'days', align: 'right' },
          { heading: 'awarded', align: 'right' },
          { heading: 'state' },
        ],
        report.lines.map((line) => [
          line.reference,
          line.cause,
          formatSiteDate(line.from),
          formatSiteDate(line.to),
          String(line.workingDays),
          line.awardedDays === null ? '' : String(line.awardedDays),
          line.bar === null ? 'claimable' : line.bar.replace(/_/g, ' '),
        ]),
      ),
    );
  }

  if (report.damagesCapped) {
    sections.push('', 'damages have reached the contract cap');
  }
  return sections.join('\n');
}

export function zeroDamages(contractSum: Money): Money {
  return zeroMoney(contractSum.currency);
}
