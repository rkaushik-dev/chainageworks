import type { BillChange, BillDiff } from '../domain/bill/diff.js';
import { changesOfKind, diffBills, hasRateChanges } from '../domain/bill/diff.js';
import { formatItemCode } from '../domain/bill/itemCode.js';
import type { BillHistory, BillRevision } from '../domain/bill/revision.js';
import { formatMoney } from '../domain/money/money.js';
import { formatRate } from '../domain/money/rate.js';
import { formatQuantity } from '../domain/quantity/quantity.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import { renderHeading, renderPairs, renderTable } from './format/table.js';

export interface RevisionStep {
  readonly from: BillRevision;
  readonly to: BillRevision;
  readonly diff: BillDiff;
}

export interface RevisionReport {
  readonly steps: readonly RevisionStep[];
  /** Steps that moved a rate, which is what a signatory has to be told. */
  readonly ratesMoved: readonly RevisionStep[];
}

/**
 * Every revision of the bill against the one before it.
 *
 * Stepwise rather than first against last. A rate that went up in Rev B and
 * back down in Rev C nets to nothing across the whole history, and it is
 * exactly the movement somebody has to explain, so the report that hides it is
 * the wrong report.
 */
export function buildRevisionReport(history: BillHistory): RevisionReport {
  const steps: RevisionStep[] = [];
  for (let i = 1; i < history.revisions.length; i += 1) {
    const from = history.revisions[i - 1];
    const to = history.revisions[i];
    if (from === undefined || to === undefined) continue;
    steps.push({ from, to, diff: diffBills(from.bill, to.bill) });
  }
  return { steps, ratesMoved: steps.filter((step) => hasRateChanges(step.diff)) };
}

function changeRow(change: BillChange): string[] {
  return [
    formatItemCode(change.code),
    change.kind.replace(/_/g, ' '),
    change.before === null ? '' : formatRate(change.before.rate),
    change.after === null ? '' : formatRate(change.after.rate),
    change.before === null ? '' : formatQuantity(change.before.quantity),
    change.after === null ? '' : formatQuantity(change.after.quantity),
    formatMoney(change.delta),
  ];
}

export function renderRevisionReport(report: RevisionReport): string {
  const sections: string[] = [
    renderHeading('bill revisions'),
    '',
    renderPairs([
      ['steps', String(report.steps.length)],
      ['steps that moved a rate', String(report.ratesMoved.length)],
    ]),
  ];

  for (const step of report.steps) {
    sections.push(
      '',
      renderHeading(
        `${step.from.reference} to ${step.to.reference}, ` +
          `effective ${formatSiteDate(step.to.effectiveFrom)}`,
        '-',
      ),
      renderPairs([
        ['changes', String(step.diff.changes.length)],
        ['net movement', formatMoney(step.diff.netDelta)],
        ['rates moved', hasRateChanges(step.diff) ? 'yes' : 'no'],
      ]),
    );
    if (step.diff.changes.length > 0) {
      sections.push(
        renderTable(
          [
            { heading: 'code' },
            { heading: 'change' },
            { heading: 'rate was', align: 'right' },
            { heading: 'rate now', align: 'right' },
            { heading: 'quantity was', align: 'right' },
            { heading: 'quantity now', align: 'right' },
            { heading: 'movement', align: 'right' },
          ],
          step.diff.changes.map(changeRow),
        ),
      );
    }
  }
  return sections.join('\n');
}

/**
 * The rate movements a certificate signer has to be told about.
 *
 * Only rate changes, deliberately. A quantity change is remeasurement and it
 * happens every month; a rate change is somebody repricing the contract, and
 * conflating the two buries the second in four hundred instances of the first.
 */
export function rateChanges(report: RevisionReport): string[] {
  return report.ratesMoved.flatMap((step) =>
    [
      ...changesOfKind(step.diff, 'rate_changed'),
      ...changesOfKind(step.diff, 'rate_and_quantity_changed'),
    ].map(
      (change) =>
        `${step.to.reference}: ${formatItemCode(change.code)} ` +
        `${change.before === null ? '' : formatRate(change.before.rate)} to ` +
        `${change.after === null ? '' : formatRate(change.after.rate)}`,
    ),
  );
}
