import { formatChainageRange } from '../domain/chainage/range.js';
import { formatMoney } from '../domain/money/money.js';
import { formatPercent } from '../domain/money/percent.js';
import type { RiskPosition } from '../domain/risk/earlyWarning.js';
import { expectedCost, unowned } from '../domain/risk/earlyWarning.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import { renderHeading, renderPairs, renderTable } from './format/table.js';

/**
 * The register as the page that goes to a risk reduction meeting.
 *
 * Ordered by expected cost, which is the order the meeting should work
 * through, and never by reference. A register sorted by reference number is
 * sorted by the order the risks happened to be noticed in, which is the one
 * order that carries no information at all.
 */
export function renderRiskReport(position: RiskPosition): string {
  const sections: string[] = [
    renderHeading(`early warnings at ${formatSiteDate(position.asAt)}`),
    '',
    renderPairs([
      ['open', String(position.open.length)],
      ['expected cost', formatMoney(position.expected)],
      ['worst case', formatMoney(position.worstCase)],
      ['carried by the contractor', formatMoney(position.carried)],
      ['working days at risk', String(position.delayDays)],
      ['meetings overdue', String(position.overdueMeetings.length)],
      ['owner not agreed', String(unowned(position).length)],
    ]),
  ];

  if (position.open.length > 0) {
    sections.push(
      '',
      renderTable(
        [
          { heading: 'warning' },
          { heading: 'description', width: 38 },
          { heading: 'owner' },
          { heading: 'where', width: 24 },
          { heading: 'if it happens', align: 'right' },
          { heading: 'likely', align: 'right' },
          { heading: 'expected', align: 'right' },
          { heading: 'days', align: 'right' },
        ],
        position.open.map((warning) => [
          warning.reference,
          warning.description,
          warning.owner,
          warning.extent === null ? '' : formatChainageRange(warning.extent),
          formatMoney(warning.costIfItHappens),
          formatPercent(warning.likelihood, 0),
          formatMoney(expectedCost(warning)),
          String(warning.delayDays),
        ]),
      ),
    );
  }
  return sections.join('\n');
}

/** The meetings somebody has to arrange, oldest warning first. */
export function meetingsToArrange(position: RiskPosition): string[] {
  return [...position.overdueMeetings]
    .sort((a, b) => a.raisedOn - b.raisedOn)
    .map(
      (warning) =>
        `${warning.reference} raised ${formatSiteDate(warning.raisedOn)}, ` +
        `${warning.meetingOn === null ? 'no meeting held' : 'meeting held late'}`,
    );
}
