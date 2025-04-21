import { BadInputError } from '../../errors.js';
import type { SiteDate } from '../time/siteDate.js';
import { compareSiteDates, formatSiteDate } from '../time/siteDate.js';
import type { DateRange } from '../time/dateRange.js';
import { containsDate } from '../time/dateRange.js';

/**
 * How an hour on site was spent.
 *
 * The four states are the ones a foreman actually writes on a return, and the
 * difference between them is what makes the record worth keeping. Working
 * hours bought progress, standing hours were paid for and bought nothing,
 * travelling hours were neither, and idle hours are the ones a delay claim is
 * eventually argued over.
 */
export type HourKind = 'working' | 'standing' | 'travelling' | 'idle';

export const HOUR_KINDS: readonly HourKind[] = ['working', 'standing', 'travelling', 'idle'];

export interface ResourceLine {
  /** The gang or the plant item, as the resource schedule names it. */
  readonly resourceId: string;
  readonly description: string;
  /** How many of them, so one line covers a gang of six. */
  readonly count: number;
  readonly hours: number;
  readonly kind: HourKind;
}

export interface ResourceReturn {
  readonly id: string;
  readonly packageId: string;
  readonly stepId: string | null;
  readonly date: SiteDate;
  readonly shift: 'day' | 'night';
  readonly labour: readonly ResourceLine[];
  readonly plant: readonly ResourceLine[];
  readonly returnedBy: string;
  readonly note: string;
}

export interface ResourceReturnInput {
  readonly id: string;
  readonly packageId: string;
  readonly stepId?: string | null;
  readonly date: SiteDate;
  readonly shift?: 'day' | 'night';
  readonly labour?: readonly ResourceLine[];
  readonly plant?: readonly ResourceLine[];
  readonly returnedBy: string;
  readonly note?: string;
}

function checkLine(line: ResourceLine, where: string): void {
  if (!Number.isInteger(line.count) || line.count < 1) {
    throw new BadInputError('a resource line covers at least one of something', {
      where,
      resource: line.resourceId,
      count: line.count,
    });
  }
  if (!Number.isFinite(line.hours) || line.hours < 0) {
    throw new BadInputError('hours on a resource line cannot be negative', {
      where,
      resource: line.resourceId,
      hours: line.hours,
    });
  }
  if (line.hours > 24) {
    throw new BadInputError('nobody works more than twenty four hours in a day', {
      where,
      resource: line.resourceId,
      hours: line.hours,
    });
  }
}

export function buildResourceReturn(input: ResourceReturnInput): ResourceReturn {
  const labour = [...(input.labour ?? [])];
  const plant = [...(input.plant ?? [])];
  if (labour.length === 0 && plant.length === 0) {
    throw new BadInputError('a resource return with nothing on it says nothing', {
      return: input.id,
    });
  }
  for (const line of labour) checkLine(line, `${input.id} labour`);
  for (const line of plant) checkLine(line, `${input.id} plant`);
  return {
    id: input.id,
    packageId: input.packageId,
    stepId: input.stepId ?? null,
    date: input.date,
    shift: input.shift ?? 'day',
    labour,
    plant,
    returnedBy: input.returnedBy,
    note: input.note ?? '',
  };
}

/** Resource hours multiplied out: six men for eight hours is forty eight. */
export function lineHours(line: ResourceLine): number {
  return line.count * line.hours;
}

export function hoursOfKind(lines: readonly ResourceLine[], kind: HourKind): number {
  return lines
    .filter((line) => line.kind === kind)
    .reduce((total, line) => total + lineHours(line), 0);
}

export function totalHours(lines: readonly ResourceLine[]): number {
  return lines.reduce((total, line) => total + lineHours(line), 0);
}

export interface ResourceTotals {
  readonly labourHours: number;
  readonly plantHours: number;
  readonly workingHours: number;
  readonly standingHours: number;
  readonly idleHours: number;
  readonly returns: number;
  readonly days: number;
}

/**
 * Add up a set of returns.
 *
 * Labour and plant are kept apart because they are not interchangeable: an
 * excavator standing costs money whether or not the gang is working, and a
 * single combined hours figure hides which of the two the job is losing.
 */
export function totalResources(returns: readonly ResourceReturn[]): ResourceTotals {
  const labour = returns.flatMap((r) => [...r.labour]);
  const plant = returns.flatMap((r) => [...r.plant]);
  const all = [...labour, ...plant];
  return {
    labourHours: totalHours(labour),
    plantHours: totalHours(plant),
    workingHours: hoursOfKind(all, 'working'),
    standingHours: hoursOfKind(all, 'standing'),
    idleHours: hoursOfKind(all, 'idle'),
    returns: returns.length,
    days: new Set(returns.map((r) => r.date)).size,
  };
}

export function returnsFor(
  returns: readonly ResourceReturn[],
  packageId: string,
): ResourceReturn[] {
  return returns.filter((r) => r.packageId === packageId);
}

export function returnsIn(
  returns: readonly ResourceReturn[],
  range: DateRange,
): ResourceReturn[] {
  return returns.filter((r) => containsDate(range, r.date));
}

export function returnsTo(
  returns: readonly ResourceReturn[],
  asAt: SiteDate,
): ResourceReturn[] {
  return returns.filter((r) => compareSiteDates(r.date, asAt) <= 0);
}

/**
 * The share of the hours that bought nothing.
 *
 * Reported as a fraction rather than a count so two packages of different
 * sizes can be set against each other, which is the only comparison anybody
 * makes with this number.
 */
export function lostFraction(totals: ResourceTotals): number {
  const all = totals.workingHours + totals.standingHours + totals.idleHours;
  return all === 0 ? 0 : (totals.standingHours + totals.idleHours) / all;
}

export function describeReturn(entry: ResourceReturn): string {
  const totals = totalResources([entry]);
  return (
    `${entry.id} ${entry.packageId} ${formatSiteDate(entry.date)} ${entry.shift}, ` +
    `${String(totals.labourHours)} labour hours, ${String(totals.plantHours)} plant hours`
  );
}
