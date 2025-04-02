import type { CertificateBlock, Defect, DefectSummary } from '../domain/defects/defect.js';
import {
  certificateBlock,
  describeDefect,
  outstanding,
  overdue,
  summarise,
} from '../domain/defects/defect.js';
import { formatChainageRange } from '../domain/chainage/range.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import { renderHeading, renderPairs, renderTable } from './format/table.js';

export interface DefectsReport {
  readonly asAt: SiteDate;
  readonly periodEnds: SiteDate;
  readonly summary: DefectSummary;
  readonly outstanding: readonly Defect[];
  readonly overdue: readonly Defect[];
  readonly block: CertificateBlock | null;
}

/**
 * The defects position at a date.
 *
 * Built around what is stopping the certificate rather than around the count,
 * because a list of forty minor items and a list of one safety item read the
 * same on a count and mean completely different things.
 */
export function buildDefectsReport(
  defects: readonly Defect[],
  periodEnds: SiteDate,
  asAt: SiteDate,
): DefectsReport {
  return {
    asAt,
    periodEnds,
    summary: summarise(defects, asAt),
    outstanding: outstanding(defects, asAt),
    overdue: overdue(defects, asAt),
    block: certificateBlock(defects, periodEnds, asAt),
  };
}

export function renderDefectsReport(report: DefectsReport): string {
  const sections: string[] = [
    renderHeading(`defects at ${formatSiteDate(report.asAt)}`),
    '',
    renderPairs([
      ['period ends', formatSiteDate(report.periodEnds)],
      ['outstanding', String(report.summary.outstanding)],
      ['overdue', String(report.summary.overdue)],
      ['safety', String(report.summary.bySeverity.safety)],
      ['major', String(report.summary.bySeverity.major)],
      ['minor', String(report.summary.bySeverity.minor)],
      ['cause not agreed', String(report.summary.disputed)],
      [
        'certificate',
        report.block === null ? 'can be issued' : report.block.replace(/_/g, ' '),
      ],
    ]),
  ];

  if (report.outstanding.length > 0) {
    sections.push(
      '',
      renderTable(
        [
          { heading: 'defect' },
          { heading: 'package' },
          { heading: 'where' },
          { heading: 'severity' },
          { heading: 'due' },
          { heading: 'cause' },
          { heading: 'description', width: 40 },
        ],
        report.outstanding.map((defect) => [
          defect.reference,
          defect.packageId,
          defect.extent === null ? '' : formatChainageRange(defect.extent),
          defect.severity,
          formatSiteDate(defect.dueBy),
          defect.attribution,
          defect.description,
        ]),
      ),
    );
  }
  return sections.join('\n');
}

/** The chase list: overdue first, then everything else by due date. */
export function chaseList(report: DefectsReport): string[] {
  return [
    ...report.overdue,
    ...report.outstanding.filter((d) => !report.overdue.includes(d)),
  ].map(describeDefect);
}
