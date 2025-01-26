import { formatChainageRange } from '../../domain/chainage/range.js';
import { formatMoney } from '../../domain/money/money.js';
import { formatPercent } from '../../domain/money/percent.js';
import { formatQuantity } from '../../domain/quantity/quantity.js';
import { formatSiteDate } from '../../domain/time/siteDate.js';
import { formatItemCode } from '../../domain/bill/itemCode.js';
import type { Certificate } from '../../valuation/certificate.js';
import { describePeriod } from '../../valuation/period.js';
import type { PackageLine, ProgressReport } from '../progressReport.js';
import { heldLines, outstandingHoldPoints } from '../progressReport.js';
import type { CurvePoint } from '../sCurve.js';
import type { Align } from './table.js';
import { indent, renderHeading, renderPairs, renderTable } from './table.js';

const MONEY: Align = 'right';

/** The package table that sits at the top of a progress report. */
export function renderPackageTable(lines: readonly PackageLine[]): string {
  return renderTable(
    [
      { heading: 'package' },
      { heading: 'name', width: 34 },
      { heading: 'budget', align: MONEY },
      { heading: 'earned', align: MONEY },
      { heading: 'percent', align: MONEY },
      { heading: 'held', align: MONEY },
    ],
    lines.map((line) => [
      line.packageId,
      line.name,
      formatMoney(line.budget),
      formatMoney(line.earned),
      formatPercent(line.fraction, 1),
      formatMoney(line.heldValue),
    ]),
  );
}

/** One package broken down step by step, for the backing sheet. */
export function renderStepTable(line: PackageLine): string {
  return renderTable(
    [
      { heading: 'step' },
      { heading: 'built', align: MONEY },
      { heading: 'credited', align: MONEY },
      { heading: 'gate' },
      { heading: 'held back' },
    ],
    line.steps.map((step) => [
      step.stepId,
      `${(step.builtFraction * 100).toFixed(1)}%`,
      `${(step.creditedFraction * 100).toFixed(1)}%`,
      step.gate.replace(/_/g, ' '),
      step.heldBack.map(formatChainageRange).join(', '),
    ]),
  );
}

/**
 * The whole progress report as plain text.
 *
 * Ordered the way it gets read: the money first, then what is stopping the
 * money, then the housekeeping. A reader who stops after the first two sections
 * has everything they need to act.
 */
export function renderProgressReport(report: ProgressReport): string {
  const sections: string[] = [
    renderHeading(`progress report, ${describePeriod(report.period)}`),
    '',
    renderPairs([
      ['bill revision', report.billRevision],
      ['budget', formatMoney(report.budget)],
      ['earned', formatMoney(report.earned)],
      ['held back', formatMoney(report.heldValue)],
      ['working days', String(report.workingDays)],
      ['productive days', String(report.productiveDays)],
    ]),
    '',
    renderPackageTable(report.lines),
  ];

  const held = heldLines(report);
  if (held.length > 0) {
    sections.push('', renderHeading('work built and not payable', '-'), '');
    for (const line of held) {
      sections.push(`${line.packageId} ${formatMoney(line.heldValue)}`);
      sections.push(indent(renderStepTable(line)));
      sections.push('');
    }
    const points = outstandingHoldPoints(report);
    if (points.length > 0) {
      sections.push(`hold points outstanding: ${points.join(', ')}`);
    }
  }

  if (report.standdowns.length > 0) {
    sections.push('', renderHeading('days lost', '-'), '');
    for (const standdown of report.standdowns) {
      sections.push(
        `${formatSiteDate(standdown.date)}  ${standdown.reason.replace(/_/g, ' ')}` +
          (standdown.note === undefined || standdown.note === '' ? '' : `  ${standdown.note}`),
      );
    }
  }

  if (report.reconciliation.length > 0 || report.overMeasures.length > 0) {
    sections.push('', renderHeading('to look at', '-'), '');
    for (const line of report.reconciliation) sections.push(line.message);
    for (const over of report.overMeasures) sections.push(over.message);
  }

  return sections.join('\n');
}

/** The certificate as the one page a payer actually signs. */
export function renderCertificate(certificate: Certificate): string {
  const retention = certificate.retention;
  return [
    renderHeading(`payment certificate ${String(certificate.period.number)}`),
    '',
    renderPairs([
      ['valuation to', formatSiteDate(certificate.period.cutOff)],
      ['issued', formatSiteDate(certificate.period.issuedOn)],
      ['payment due', formatSiteDate(certificate.period.dueOn)],
      ['bill revision', certificate.billRevision],
    ]),
    '',
    renderTable(
      [{ heading: 'item' }, { heading: 'amount', align: MONEY }],
      [
        ['measured work', formatMoney(certificate.measuredWork)],
        ['materials on site', formatMoney(certificate.materialsOnSite)],
        ['gross valuation', formatMoney(certificate.gross)],
        ['less retention', formatMoney(retention.held)],
        ['net valuation', formatMoney(certificate.netValuation)],
        ['less previously certified', formatMoney(certificate.previouslyCertified)],
        ['amount due this certificate', formatMoney(certificate.amountDue)],
      ],
    ),
    ...(retention.capReached ? ['', 'retention has reached its cap'] : []),
  ].join('\n');
}

/** The bill of quantities as a priced page. */
export function renderCurve(points: readonly CurvePoint[]): string {
  return renderTable(
    [
      { heading: 'period', align: MONEY },
      { heading: 'cut off' },
      { heading: 'planned', align: MONEY },
      { heading: 'earned', align: MONEY },
      { heading: 'variance', align: MONEY },
      { heading: 'earned %', align: MONEY },
    ],
    points.map((point) => [
      String(point.period),
      formatSiteDate(point.cutOff),
      formatMoney(point.planned),
      formatMoney(point.earned),
      formatMoney(point.variance),
      formatPercent(point.earnedShare, 1),
    ]),
  );
}

/** The reconciliation, as the list a surveyor works through. */
export function renderReconciliation(report: ProgressReport): string {
  if (report.reconciliation.length === 0) return 'the packages reconcile against the bill';
  return renderTable(
    [
      { heading: 'code' },
      { heading: 'problem' },
      { heading: 'billed' },
      { heading: 'allocated' },
    ],
    report.reconciliation.map((line) => [
      formatItemCode(line.code),
      line.problem.replace(/_/g, ' '),
      line.billed === null ? '' : formatQuantity(line.billed),
      line.allocated === null ? '' : formatQuantity(line.allocated),
    ]),
  );
}
