import type { ResourceReturn } from '../domain/resource/return.js';
import { lostFraction, returnsTo, totalResources } from '../domain/resource/return.js';
import type { ResourceTotals } from '../domain/resource/return.js';
import type { WorkPackage } from '../domain/packages/workPackage.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import type { ProgressContext } from '../progress/context.js';
import type { OutputAllowance, PackageOutput, StepOutput } from '../progress/productivity.js';
import { behindAllowance, nothingToShow, packageOutput } from '../progress/productivity.js';
import { renderHeading, renderPairs, renderTable } from './format/table.js';

export interface OutputReport {
  readonly asAt: SiteDate;
  readonly packages: readonly PackageOutput[];
  readonly totals: ResourceTotals;
  /** The share of all booked hours that bought nothing. */
  readonly lostFraction: number;
  /** Steps running behind their allowance, worst first, across every package. */
  readonly behind: readonly { packageId: string; step: StepOutput }[];
  /** Steps with hours booked and nothing built to show for them. */
  readonly barren: readonly { packageId: string; step: StepOutput }[];
}

/**
 * What the job produced for the hours it booked, package by package.
 *
 * Built on the same cut-off as the valuation so the two can be read side by
 * side. A productivity figure taken to a different date than the certificate
 * invites the argument that the hours were spent on work that has not been
 * measured yet, and that argument is unanswerable when the dates differ.
 */
export function buildOutputReport(
  context: ProgressContext,
  packages: readonly WorkPackage[],
  returns: readonly ResourceReturn[],
  allowances: readonly OutputAllowance[],
  asAt: SiteDate,
): OutputReport {
  const outputs = packages.map((pkg) => packageOutput(context, pkg, returns, asAt, allowances));
  const totals = totalResources(returnsTo(returns, asAt));

  const behind = outputs
    .flatMap((output) =>
      behindAllowance(output).map((step) => ({ packageId: output.packageId, step })),
    )
    .sort((a, b) => (a.step.ratio ?? 0) - (b.step.ratio ?? 0));

  const barren = outputs.flatMap((output) =>
    nothingToShow(output).map((step) => ({ packageId: output.packageId, step })),
  );

  return {
    asAt,
    packages: outputs,
    totals,
    lostFraction: lostFraction(totals),
    behind,
    barren,
  };
}

function rate(value: number | null, places = 2): string {
  return value === null ? '' : value.toFixed(places);
}

export function renderOutputReport(report: OutputReport): string {
  const sections: string[] = [
    renderHeading(`output at ${formatSiteDate(report.asAt)}`),
    '',
    renderPairs([
      ['returns', String(report.totals.returns)],
      ['days worked', String(report.totals.days)],
      ['labour hours', String(report.totals.labourHours)],
      ['plant hours', String(report.totals.plantHours)],
      ['standing hours', String(report.totals.standingHours)],
      ['idle hours', String(report.totals.idleHours)],
      ['hours that bought nothing', `${(report.lostFraction * 100).toFixed(1)} percent`],
      ['steps behind the allowance', String(report.behind.length)],
    ]),
  ];

  const rows = report.packages.flatMap((output) =>
    output.steps
      .filter((step) => step.labourHours > 0 || step.builtMetres > 0)
      .map((step) => [
        output.packageId,
        step.stepId,
        step.builtMetres.toFixed(0),
        String(step.labourHours),
        String(step.plantHours),
        rate(step.achieved),
        rate(step.allowed),
        step.ratio === null ? '' : `${(step.ratio * 100).toFixed(0)}%`,
      ]),
  );

  if (rows.length > 0) {
    sections.push(
      '',
      renderTable(
        [
          { heading: 'package' },
          { heading: 'step' },
          { heading: 'metres', align: 'right' },
          { heading: 'labour hr', align: 'right' },
          { heading: 'plant hr', align: 'right' },
          { heading: 'm per hr', align: 'right' },
          { heading: 'allowed', align: 'right' },
          { heading: 'against', align: 'right' },
        ],
        rows,
      ),
    );
  }

  if (report.barren.length > 0) {
    sections.push(
      '',
      renderHeading('hours with nothing measured against them', '-'),
      ...report.barren.map(
        (entry) =>
          `  ${entry.packageId} ${entry.step.stepId}, ` +
          `${String(entry.step.labourHours)} labour hours`,
      ),
    );
  }
  return sections.join('\n');
}

/** The hours a package booked without naming a step, worst first. */
export function unallocatedHours(report: OutputReport): { packageId: string; hours: number }[] {
  return report.packages
    .filter((output) => output.unallocatedHours > 0)
    .map((output) => ({ packageId: output.packageId, hours: output.unallocatedHours }))
    .sort((a, b) => b.hours - a.hours);
}
