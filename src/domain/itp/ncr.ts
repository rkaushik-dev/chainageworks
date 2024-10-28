import { BadInputError } from '../../errors.js';
import type { ChainageRange } from '../chainage/range.js';
import { coveredLength, subtractRange, unionRanges } from '../chainage/range.js';
import type { SiteDate } from '../time/siteDate.js';
import { compareSiteDates, formatSiteDate } from '../time/siteDate.js';
import type { StepGate } from './signOff.js';

/**
 * How a non conformance was settled.
 *
 * `closed` means the work was put right. `accepted` means it was left as built
 * under a concession. The difference matters for the record and not for the
 * money, because both release the credit that was being withheld, and running
 * them together would lose the concession history the defects list needs.
 */
export type NcrStatus = 'open' | 'closed' | 'accepted';

export interface Ncr {
  readonly id: string;
  readonly packageId: string;
  readonly extent: ChainageRange;
  /** The credit step affected, or null when the whole package is. */
  readonly stepId: string | null;
  readonly raisedOn: SiteDate;
  readonly settledOn: SiteDate | null;
  readonly status: NcrStatus;
  readonly description: string;
  /** Whether credit is held back while it is open. */
  readonly withholdsCredit: boolean;
}

export interface NcrInput {
  readonly id: string;
  readonly packageId: string;
  readonly extent: ChainageRange;
  readonly stepId?: string | null;
  readonly raisedOn: SiteDate;
  readonly settledOn?: SiteDate | null;
  readonly status?: NcrStatus;
  readonly description: string;
  readonly withholdsCredit?: boolean;
}

export function buildNcr(input: NcrInput): Ncr {
  const status = input.status ?? 'open';
  const settledOn = input.settledOn ?? null;
  if (status === 'open' && settledOn !== null) {
    throw new BadInputError('an open non conformance cannot carry a settled date', {
      ncr: input.id,
    });
  }
  if (status !== 'open' && settledOn === null) {
    throw new BadInputError('a settled non conformance needs the date it was settled', {
      ncr: input.id,
      status,
    });
  }
  if (settledOn !== null && compareSiteDates(settledOn, input.raisedOn) < 0) {
    throw new BadInputError('a non conformance cannot be settled before it was raised', {
      ncr: input.id,
      raised: formatSiteDate(input.raisedOn),
      settled: formatSiteDate(settledOn),
    });
  }
  if (input.description.trim() === '') {
    throw new BadInputError('a non conformance needs a description', { ncr: input.id });
  }
  return {
    id: input.id,
    packageId: input.packageId,
    extent: input.extent,
    stepId: input.stepId ?? null,
    raisedOn: input.raisedOn,
    settledOn,
    status,
    description: input.description.trim(),
    withholdsCredit: input.withholdsCredit ?? true,
  };
}

/**
 * Whether the non conformance was open on a date.
 *
 * It counts from the day it was raised and stops counting on the day it was
 * settled, so a report closed on the cut-off releases that month rather than
 * the next one.
 */
export function isOpenAt(ncr: Ncr, date: SiteDate): boolean {
  if (compareSiteDates(ncr.raisedOn, date) > 0) return false;
  if (ncr.settledOn === null) return true;
  return compareSiteDates(ncr.settledOn, date) > 0;
}

export function settledIn(ncrs: readonly Ncr[], from: SiteDate, to: SiteDate): Ncr[] {
  return ncrs.filter(
    (n) =>
      n.settledOn !== null &&
      compareSiteDates(n.settledOn, from) >= 0 &&
      compareSiteDates(n.settledOn, to) <= 0,
  );
}

export interface WithholdQuery {
  readonly ncrs: readonly Ncr[];
  readonly packageId: string;
  readonly stepId: string;
  readonly asAt: SiteDate;
}

/**
 * The stretches credit is being held off on a step. A report with no step named
 * applies to every step in the package, which is what a workmanship report
 * covering a whole bay means in practice.
 */
export function withheldExtent(query: WithholdQuery): ChainageRange[] {
  const biting = query.ncrs.filter(
    (n) =>
      n.packageId === query.packageId &&
      n.withholdsCredit &&
      (n.stepId === null || n.stepId === query.stepId) &&
      isOpenAt(n, query.asAt),
  );
  return unionRanges(biting.map((n) => n.extent));
}

/** Narrow a gate by whatever the open non conformances are holding back. */
export function applyWithholding(gate: StepGate, withheld: readonly ChainageRange[]): StepGate {
  if (withheld.length === 0) return gate;
  let open = [...gate.openExtent];
  for (const range of withheld) {
    open = open.flatMap((r) => subtractRange(r, range));
  }
  const merged = unionRanges(open);
  const remaining = coveredLength(merged);
  const before = coveredLength([...gate.openExtent]);
  const state = remaining === 0 ? 'held' : remaining >= before ? gate.state : 'part_open';
  return { ...gate, state, openExtent: merged };
}

export function describeNcr(ncr: Ncr): string {
  const settled =
    ncr.settledOn === null ? 'open' : `${ncr.status} ${formatSiteDate(ncr.settledOn)}`;
  return `${ncr.id} raised ${formatSiteDate(ncr.raisedOn)}, ${settled}: ${ncr.description}`;
}
