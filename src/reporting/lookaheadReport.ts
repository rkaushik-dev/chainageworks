import { formatChainageRange } from '../domain/chainage/range.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import type { Lookahead, StepLookahead } from '../progress/lookahead.js';
import {
  fitsInWindow,
  readyNow,
  unpriced,
  waitingOnInspection,
} from '../progress/lookahead.js';
import { renderHeading, renderPairs, renderTable } from './format/table.js';

function extentOf(step: StepLookahead): string {
  return step.remaining.map(formatChainageRange).join(', ');
}

function stateOf(step: StepLookahead): string {
  if (step.heldPoints.length > 0) return `held by ${step.heldPoints.join(', ')}`;
  if (step.waitingOn.length > 0) return `after ${step.waitingOn.join(', ')}`;
  return 'ready';
}

/**
 * The lookahead as the sheet a site manager marks up on a Monday.
 *
 * Ready work first, then work waiting on somebody else, because those two
 * lists go to different people. The first is a list of gangs to allocate and
 * the second is a list of phone calls to make.
 */
export function renderLookahead(lookahead: Lookahead): string {
  const ready = readyNow(lookahead);
  const held = waitingOnInspection(lookahead);

  const sections: string[] = [
    renderHeading(
      `lookahead ${formatSiteDate(lookahead.from)} to ${formatSiteDate(lookahead.to)}`,
    ),
    '',
    renderPairs([
      ['working days in the window', String(lookahead.workingDays)],
      ['days of work in front', lookahead.daysOfWork.toFixed(1)],
      ['fits the window', fitsInWindow(lookahead) ? 'yes' : 'no'],
      ['ready to start', String(ready.length)],
      ['waiting on an inspection', String(held.length)],
      ['no allowance set', String(unpriced(lookahead).length)],
    ]),
  ];

  const rows = (steps: readonly StepLookahead[]) =>
    steps.map((step) => [
      step.packageId,
      step.stepId,
      extentOf(step),
      step.remainingMetres.toFixed(0),
      step.days === null ? '' : step.days.toFixed(1),
      stateOf(step),
    ]);

  const columns = [
    { heading: 'package' },
    { heading: 'step' },
    { heading: 'where', width: 30 },
    { heading: 'metres', align: 'right' as const },
    { heading: 'days', align: 'right' as const },
    { heading: 'state', width: 28 },
  ];

  if (ready.length > 0) {
    sections.push('', renderHeading('ready to start', '-'), renderTable(columns, rows(ready)));
  }
  const rest = lookahead.steps.filter((step) => !ready.includes(step));
  if (rest.length > 0) {
    sections.push('', renderHeading('waiting', '-'), renderTable(columns, rows(rest)));
  }
  if (lookahead.steps.length === 0) {
    sections.push('', 'nothing left to build');
  }
  return sections.join('\n');
}

/** The phone calls: who has to sign what before a gang can move on. */
export function chaseInspections(lookahead: Lookahead): string[] {
  return waitingOnInspection(lookahead).map(
    (step) =>
      `${step.packageId} ${step.stepId} at ${extentOf(step)}, ` +
      `held by ${step.heldPoints.join(', ')}`,
  );
}
