import { formatMoney } from '../../domain/money/money.js';
import { formatSiteDate } from '../../domain/time/siteDate.js';
import type { Certificate } from '../../valuation/certificate.js';
import { certificateLines, certificateNotes } from '../../valuation/certificate.js';
import type { ProgressReport } from '../progressReport.js';
import type { SubcontractReport } from '../subcontractReport.js';
import type { Column } from './table.js';

/**
 * Escape the characters that would otherwise be markup.
 *
 * A pipe is the one that matters: a bill description reading "300 mm | 450 mm"
 * silently splits a table row into the wrong number of cells, and the resulting
 * document renders with the columns shifted from that row down.
 */
export function escapeCell(text: string): string {
  return text.replace(/\|/g, '\\|').replace(/\r?\n/g, ' ');
}

export function markdownTable(
  columns: readonly Column[],
  rows: readonly (readonly string[])[],
): string {
  const heading = `| ${columns.map((c) => escapeCell(c.heading)).join(' | ')} |`;
  const rule = `| ${columns.map((c) => (c.align === 'right' ? '---:' : ':---')).join(' | ')} |`;
  const body = rows.map((row) => `| ${row.map(escapeCell).join(' | ')} |`);
  return [heading, rule, ...body].join('\n');
}

/** Lift the first letter, because a markdown table reads as prose. */
function sentenceCase(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function markdownHeading(text: string, level = 2): string {
  return `${'#'.repeat(level)} ${text}`;
}

export function markdownPairs(pairs: readonly (readonly [string, string])[]): string {
  return pairs
    .map(([name, value]) => `- **${escapeCell(name)}**: ${escapeCell(value)}`)
    .join('\n');
}

/**
 * A certificate as a markdown document.
 *
 * The same figures as the plain text version, in the same order, because the
 * two are printed from one job and somebody always compares them. The only
 * differences are the ones markdown forces.
 */
export function certificateMarkdown(certificate: Certificate): string {
  const sections = [
    markdownHeading(`Payment certificate ${String(certificate.period.number)}`, 1),
    '',
    markdownPairs([
      ['Valuation to', formatSiteDate(certificate.period.cutOff)],
      ['Issued', formatSiteDate(certificate.period.issuedOn)],
      ['Payment due', formatSiteDate(certificate.period.dueOn)],
      ['Bill revision', certificate.billRevision],
    ]),
    '',
    markdownTable(
      [{ heading: 'Item' }, { heading: 'Amount', align: 'right' }],
      certificateLines(certificate).map((line) => {
        const name = sentenceCase(line.name);
        return line.name === 'amount due this certificate'
          ? [`**${name}**`, `**${formatMoney(line.amount)}**`]
          : [name, formatMoney(line.amount)];
      }),
    ),
  ];

  for (const note of certificateNotes(certificate)) {
    sections.push('', `> ${sentenceCase(note)}.`);
  }
  return sections.join('\n');
}

export function progressMarkdown(report: ProgressReport): string {
  return [
    markdownHeading(`Progress at ${formatSiteDate(report.period.cutOff)}`, 1),
    '',
    markdownPairs([
      ['Valuation', String(report.period.number)],
      ['Bill revision', report.billRevision],
      ['Budget', formatMoney(report.budget)],
      ['Earned', formatMoney(report.earned)],
      ['Held by gates and reports', formatMoney(report.heldValue)],
      ['Working days in the period', String(report.workingDays)],
      ['Days stood down', String(report.standdowns.length)],
    ]),
    '',
    markdownHeading('Packages'),
    '',
    markdownTable(
      [
        { heading: 'Package' },
        { heading: 'Section' },
        { heading: 'Budget', align: 'right' },
        { heading: 'Earned', align: 'right' },
        { heading: 'Held', align: 'right' },
      ],
      report.lines.map((line) => [
        line.packageId,
        line.section,
        formatMoney(line.budget),
        formatMoney(line.earned),
        formatMoney(line.heldValue),
      ]),
    ),
  ].join('\n');
}

export function subcontractMarkdown(report: SubcontractReport): string {
  const sections = [
    markdownHeading(`Subcontract payments at ${formatSiteDate(report.asAt)}`, 1),
    '',
    markdownPairs([
      ['Orders', String(report.positions.length)],
      ['Notices missed', String(report.diary.filter((e) => e.missed).length)],
      ['Exposed by default', report.exposure],
      ['Orders with barred set off', String(report.barredContra.length)],
    ]),
  ];

  if (report.positions.length > 0) {
    sections.push(
      '',
      markdownTable(
        [
          { heading: 'Order' },
          { heading: 'Contractor' },
          { heading: 'Certified', align: 'right' },
          { heading: 'Set off', align: 'right' },
          { heading: 'Net', align: 'right' },
        ],
        report.positions.map((position) => [
          position.subcontract.id,
          position.subcontract.contractor,
          formatMoney(position.certifiedToDate),
          formatMoney(position.contra.deductible),
          formatMoney(position.netOfContra),
        ]),
      ),
    );
  }

  if (report.diary.length > 0) {
    sections.push(
      '',
      markdownHeading('Notice diary'),
      '',
      markdownTable(
        [
          { heading: 'Application' },
          { heading: 'Contractor' },
          { heading: 'Notice by' },
          { heading: 'Applied for', align: 'right' },
          { heading: 'State' },
        ],
        report.diary.map((entry) => [
          entry.applicationId,
          entry.contractor,
          formatSiteDate(entry.noticeBy),
          entry.amount,
          entry.missed ? 'window closed' : 'open',
        ]),
      ),
    );
  }
  return sections.join('\n');
}
