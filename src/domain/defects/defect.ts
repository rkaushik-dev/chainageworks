import { BadInputError } from '../../errors.js';
import type { ChainageRange } from '../chainage/range.js';
import type { SiteCalendar } from '../time/calendar.js';
import { addWorkingDays } from '../time/calendar.js';
import type { SiteDate } from '../time/siteDate.js';
import { compareSiteDates, formatSiteDate } from '../time/siteDate.js';

/**
 * How bad a defect is, which decides how long there is to put it right.
 *
 * A safety defect is separated from a major one because it does more than set a
 * shorter clock: it stops the defects certificate outright, however small the
 * work to fix it. Nobody signs off a job with an open safety item on it.
 */
export type DefectSeverity = 'minor' | 'major' | 'safety';

export type Attribution = 'contractor' | 'employer' | 'unresolved';

export interface Defect {
  readonly id: string;
  readonly reference: string;
  readonly packageId: string;
  readonly extent: ChainageRange | null;
  readonly description: string;
  readonly severity: DefectSeverity;
  readonly attribution: Attribution;
  readonly raisedOn: SiteDate;
  /** The date the contract gives for putting it right. */
  readonly dueBy: SiteDate;
  readonly rectifiedOn: SiteDate | null;
}

export interface DefectInput {
  readonly id: string;
  readonly reference: string;
  readonly packageId: string;
  readonly extent?: ChainageRange | null;
  readonly description: string;
  readonly severity?: DefectSeverity;
  readonly attribution?: Attribution;
  readonly raisedOn: SiteDate;
  readonly dueBy: SiteDate;
  readonly rectifiedOn?: SiteDate | null;
}

/** Working days allowed to put a defect right, by how bad it is. */
export const RECTIFICATION_DAYS: Readonly<Record<DefectSeverity, number>> = {
  safety: 1,
  major: 10,
  minor: 20,
};

export function dueDateFor(
  severity: DefectSeverity,
  raisedOn: SiteDate,
  calendar: SiteCalendar,
): SiteDate {
  return addWorkingDays(calendar, raisedOn, RECTIFICATION_DAYS[severity]);
}

export function buildDefect(input: DefectInput): Defect {
  if (input.description.trim() === '') {
    throw new BadInputError('a defect needs a description', { defect: input.id });
  }
  if (compareSiteDates(input.dueBy, input.raisedOn) < 0) {
    throw new BadInputError('a defect cannot be due before it was raised', {
      defect: input.id,
      raised: formatSiteDate(input.raisedOn),
      due: formatSiteDate(input.dueBy),
    });
  }
  const rectifiedOn = input.rectifiedOn ?? null;
  if (rectifiedOn !== null && compareSiteDates(rectifiedOn, input.raisedOn) < 0) {
    throw new BadInputError('a defect cannot be put right before it was raised', {
      defect: input.id,
      raised: formatSiteDate(input.raisedOn),
      rectified: formatSiteDate(rectifiedOn),
    });
  }
  return {
    id: input.id,
    reference: input.reference,
    packageId: input.packageId,
    extent: input.extent ?? null,
    description: input.description.trim(),
    severity: input.severity ?? 'minor',
    attribution: input.attribution ?? 'contractor',
    raisedOn: input.raisedOn,
    dueBy: input.dueBy,
    rectifiedOn,
  };
}

export function isOutstandingAt(defect: Defect, asAt: SiteDate): boolean {
  if (compareSiteDates(defect.raisedOn, asAt) > 0) return false;
  if (defect.rectifiedOn === null) return true;
  return compareSiteDates(defect.rectifiedOn, asAt) > 0;
}

export function isOverdueAt(defect: Defect, asAt: SiteDate): boolean {
  return isOutstandingAt(defect, asAt) && compareSiteDates(defect.dueBy, asAt) < 0;
}

export function outstanding(defects: readonly Defect[], asAt: SiteDate): Defect[] {
  return defects
    .filter((defect) => isOutstandingAt(defect, asAt))
    .sort((a, b) => compareSiteDates(a.dueBy, b.dueBy) || (a.id < b.id ? -1 : 1));
}

export function overdue(defects: readonly Defect[], asAt: SiteDate): Defect[] {
  return outstanding(defects, asAt).filter((defect) => isOverdueAt(defect, asAt));
}

export interface DefectSummary {
  readonly outstanding: number;
  readonly overdue: number;
  readonly bySeverity: Readonly<Record<DefectSeverity, number>>;
  readonly disputed: number;
}

export function summarise(defects: readonly Defect[], asAt: SiteDate): DefectSummary {
  const open = outstanding(defects, asAt);
  const bySeverity: Record<DefectSeverity, number> = { minor: 0, major: 0, safety: 0 };
  for (const defect of open) bySeverity[defect.severity] += 1;
  return {
    outstanding: open.length,
    overdue: overdue(defects, asAt).length,
    bySeverity,
    disputed: open.filter((defect) => defect.attribution === 'unresolved').length,
  };
}

export type CertificateBlock =
  | 'period_not_ended'
  | 'safety_defect_open'
  | 'contractor_defects_open'
  | 'attribution_unresolved';

/**
 * Why the defects certificate cannot be issued, or null when it can.
 *
 * A defect the employer caused does not stop it, and a defect nobody has
 * decided about does, which is the right way round: the certificate cannot be
 * signed while there is an open question about who pays for what.
 */
export function certificateBlock(
  defects: readonly Defect[],
  periodEnds: SiteDate,
  asAt: SiteDate,
): CertificateBlock | null {
  if (compareSiteDates(asAt, periodEnds) < 0) return 'period_not_ended';
  const open = outstanding(defects, asAt);
  if (open.some((defect) => defect.severity === 'safety')) return 'safety_defect_open';
  if (open.some((defect) => defect.attribution === 'unresolved')) {
    return 'attribution_unresolved';
  }
  if (open.some((defect) => defect.attribution === 'contractor')) {
    return 'contractor_defects_open';
  }
  return null;
}

export function canIssueDefectsCertificate(
  defects: readonly Defect[],
  periodEnds: SiteDate,
  asAt: SiteDate,
): boolean {
  return certificateBlock(defects, periodEnds, asAt) === null;
}

export function describeDefect(defect: Defect): string {
  const state =
    defect.rectifiedOn === null
      ? `due ${formatSiteDate(defect.dueBy)}`
      : `put right ${formatSiteDate(defect.rectifiedOn)}`;
  return `${defect.reference} ${defect.severity}, ${state}: ${defect.description}`;
}
