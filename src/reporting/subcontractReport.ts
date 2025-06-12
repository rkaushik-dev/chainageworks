import type { CurrencyCode } from '../domain/money/money.js';
import { formatMoney, zeroMoney } from '../domain/money/money.js';
import type {
  PayLessNotice,
  PaymentApplication,
  PaymentNotice,
} from '../domain/subcontract/payment.js';
import {
  defaultedApplications,
  noticesDue,
  paymentDates,
} from '../domain/subcontract/payment.js';
import type { ContraCharge } from '../domain/subcontract/contraCharge.js';
import type { Subcontract, SubcontractPosition } from '../domain/subcontract/subcontract.js';
import { overOrder, subcontractPosition } from '../domain/subcontract/subcontract.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { compareSiteDates, diffDays, formatSiteDate } from '../domain/time/siteDate.js';
import { renderHeading, renderPairs, renderTable } from './format/table.js';

/** One line of the notice diary: an application and the day the window shuts. */
export interface NoticeDiaryEntry {
  readonly applicationId: string;
  readonly subcontractId: string;
  readonly contractor: string;
  readonly appliedOn: SiteDate;
  readonly noticeBy: SiteDate;
  readonly amount: string;
  /** True once the window has closed with nothing served. */
  readonly missed: boolean;
}

export interface SubcontractReport {
  readonly asAt: SiteDate;
  readonly positions: readonly SubcontractPosition[];
  readonly diary: readonly NoticeDiaryEntry[];
  readonly overOrder: readonly SubcontractPosition[];
  /** What the missed notices have cost, as an amount over the payer's own valuation. */
  readonly exposure: string;
  /** Contra charges that lost their right to be set off, across every order. */
  readonly barredContra: readonly { subcontractId: string; amount: string }[];
}

export interface SubcontractInputs {
  readonly subcontracts: readonly Subcontract[];
  readonly applications: readonly PaymentApplication[];
  readonly notices: readonly PaymentNotice[];
  readonly payLess: readonly PayLessNotice[];
  readonly contra?: readonly ContraCharge[];
  readonly contraNotifyDays?: number;
}

/**
 * The subcontract payment position across the whole job.
 *
 * The diary is the point of this report rather than the totals. A commercial
 * manager can work out what has been certified from any ledger, but nothing
 * else on the job says which notice window shuts on Friday, and that is the
 * one fact that costs real money when it is missed.
 */
export function buildSubcontractReport(
  inputs: SubcontractInputs,
  asAt: SiteDate,
): SubcontractReport {
  const positions = inputs.subcontracts.map((subcontract) =>
    subcontractPosition(
      subcontract,
      {
        applications: inputs.applications,
        notices: inputs.notices,
        payLess: inputs.payLess,
        contra: inputs.contra ?? [],
        ...(inputs.contraNotifyDays === undefined
          ? {}
          : { contraNotifyDays: inputs.contraNotifyDays }),
      },
      asAt,
    ),
  );

  const diary: NoticeDiaryEntry[] = [];
  const currency: CurrencyCode = inputs.subcontracts[0]?.orderValue.currency ?? 'GBP';
  let exposureMinor = 0;

  for (const subcontract of inputs.subcontracts) {
    const mine = inputs.applications.filter((a) => a.subcontractId === subcontract.id);
    const open = noticesDue(mine, subcontract.terms, inputs.notices, asAt);
    const missed = defaultedApplications(mine, subcontract.terms, inputs.notices, asAt);
    for (const application of [...open, ...missed]) {
      const dates = paymentDates(application, subcontract.terms);
      const isMissed = missed.includes(application);
      if (isMissed) exposureMinor += application.gross.minor - application.previouslyPaid.minor;
      diary.push({
        applicationId: application.id,
        subcontractId: subcontract.id,
        contractor: subcontract.contractor,
        appliedOn: application.appliedOn,
        noticeBy: dates.noticeBy,
        amount: formatMoney(application.gross),
        missed: isMissed,
      });
    }
  }

  diary.sort(
    (a, b) =>
      compareSiteDates(a.noticeBy, b.noticeBy) ||
      a.applicationId.localeCompare(b.applicationId),
  );

  return {
    asAt,
    positions,
    diary,
    overOrder: overOrder(positions),
    barredContra: positions
      .filter((position) => position.contra.barred.minor > 0)
      .map((position) => ({
        subcontractId: position.subcontract.id,
        amount: formatMoney(position.contra.barred),
      })),
    exposure: formatMoney({ ...zeroMoney(currency), minor: exposureMinor }),
  };
}

export function renderSubcontractReport(report: SubcontractReport): string {
  const sections: string[] = [
    renderHeading(`subcontract payments at ${formatSiteDate(report.asAt)}`),
    '',
    renderPairs([
      ['orders', String(report.positions.length)],
      ['notices outstanding', String(report.diary.filter((e) => !e.missed).length)],
      ['notices missed', String(report.diary.filter((e) => e.missed).length)],
      ['over the order', String(report.overOrder.length)],
      ['set off barred', String(report.barredContra.length)],
      ['exposed by default', report.exposure],
    ]),
  ];

  if (report.positions.length > 0) {
    sections.push(
      '',
      renderTable(
        [
          { heading: 'order' },
          { heading: 'contractor', width: 24 },
          { heading: 'value', align: 'right' },
          { heading: 'certified', align: 'right' },
          { heading: 'paid', align: 'right' },
          { heading: 'set off', align: 'right' },
          { heading: 'net', align: 'right' },
          { heading: 'left', align: 'right' },
        ],
        report.positions.map((position) => [
          position.subcontract.id,
          position.subcontract.contractor,
          formatMoney(position.subcontract.orderValue),
          formatMoney(position.certifiedToDate),
          formatMoney(position.paidToDate),
          formatMoney(position.contra.deductible),
          formatMoney(position.netOfContra),
          formatMoney(position.remaining),
        ]),
      ),
    );
  }

  if (report.diary.length > 0) {
    sections.push(
      '',
      renderHeading('notice diary', '-'),
      renderTable(
        [
          { heading: 'application' },
          { heading: 'contractor', width: 24 },
          { heading: 'applied' },
          { heading: 'notice by' },
          { heading: 'applied for', align: 'right' },
          { heading: 'state' },
        ],
        report.diary.map((entry) => [
          entry.applicationId,
          entry.contractor,
          formatSiteDate(entry.appliedOn),
          formatSiteDate(entry.noticeBy),
          entry.amount,
          entry.missed ? 'window closed' : 'open',
        ]),
      ),
    );
  }
  return sections.join('\n');
}

/** The applications that have to be answered before the next one closes. */
export function noticesThisWeek(report: SubcontractReport, within: number): string[] {
  return report.diary
    .filter((entry) => !entry.missed && diffDays(report.asAt, entry.noticeBy) <= within)
    .map(
      (entry) =>
        `${entry.applicationId} ${entry.contractor}, notice by ${formatSiteDate(entry.noticeBy)}`,
    );
}
