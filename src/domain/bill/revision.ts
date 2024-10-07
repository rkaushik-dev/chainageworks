import { BadInputError, NotFoundError } from '../../errors.js';
import type { DateRange } from '../time/dateRange.js';
import { containsDate } from '../time/dateRange.js';
import type { SiteDate } from '../time/siteDate.js';
import { compareSiteDates, formatSiteDate } from '../time/siteDate.js';
import type { Rate } from '../money/rate.js';
import type { Bill } from './bill.js';
import { findItem } from './bill.js';
import type { BillItem } from './billItem.js';
import type { ItemCode } from './itemCode.js';
import { formatItemCode } from './itemCode.js';

/**
 * One issue of the bill, with the date it takes effect.
 *
 * A bill is not edited. Every change is a new revision with its own effective
 * date, because a valuation has to be priced against the revision that was in
 * force at its cut-off, not against whatever the bill says today. Repricing
 * last month's certificate because a rate changed this month is the single
 * most common way an interim valuation goes wrong.
 */
export interface BillRevision {
  readonly reference: string;
  readonly effectiveFrom: SiteDate;
  readonly bill: Bill;
  readonly note: string;
}

export interface BillHistory {
  readonly revisions: readonly BillRevision[];
}

export function buildBillHistory(revisions: readonly BillRevision[]): BillHistory {
  if (revisions.length === 0) {
    throw new BadInputError('a bill history needs at least one revision');
  }
  const sorted = [...revisions].sort((a, b) =>
    compareSiteDates(a.effectiveFrom, b.effectiveFrom),
  );
  for (let i = 1; i < sorted.length; i += 1) {
    const previous = sorted[i - 1];
    const current = sorted[i];
    if (previous === undefined || current === undefined) continue;
    if (previous.effectiveFrom === current.effectiveFrom) {
      throw new BadInputError('two bill revisions take effect on the same day', {
        date: formatSiteDate(current.effectiveFrom),
        first: previous.reference,
        second: current.reference,
      });
    }
  }
  const seen = new Set<string>();
  for (const revision of sorted) {
    if (seen.has(revision.reference)) {
      throw new BadInputError('two bill revisions share a reference', {
        reference: revision.reference,
      });
    }
    seen.add(revision.reference);
  }
  return { revisions: sorted };
}

export function firstRevision(history: BillHistory): BillRevision {
  const first = history.revisions[0];
  if (first === undefined) {
    throw new BadInputError('a bill history needs at least one revision');
  }
  return first;
}

export function latestRevision(history: BillHistory): BillRevision {
  const last = history.revisions[history.revisions.length - 1];
  if (last === undefined) {
    throw new BadInputError('a bill history needs at least one revision');
  }
  return last;
}

/**
 * The revision in force on a date: the latest one that had taken effect by
 * then. A date before the first revision has no bill at all, which is an error
 * rather than an empty bill, because valuing against an empty bill would report
 * a total of nothing instead of refusing.
 */
export function revisionAsAt(history: BillHistory, date: SiteDate): BillRevision {
  let found: BillRevision | null = null;
  for (const revision of history.revisions) {
    if (compareSiteDates(revision.effectiveFrom, date) <= 0) found = revision;
    else break;
  }
  if (found === null) {
    throw new BadInputError('no bill revision was in force on that date', {
      date: formatSiteDate(date),
      firstRevision: formatSiteDate(firstRevision(history).effectiveFrom),
    });
  }
  return found;
}

export function billAsAt(history: BillHistory, date: SiteDate): Bill {
  return revisionAsAt(history, date).bill;
}

export function itemAsAt(history: BillHistory, date: SiteDate, code: ItemCode): BillItem {
  const item = findItem(billAsAt(history, date), code);
  if (item === null) {
    throw new NotFoundError('bill item', formatItemCode(code));
  }
  return item;
}

/** The rate that values work measured on a given day. */
export function rateAsAt(history: BillHistory, date: SiteDate, code: ItemCode): Rate {
  return itemAsAt(history, date, code).rate;
}

export function hasRevisionAsAt(history: BillHistory, date: SiteDate): boolean {
  return compareSiteDates(firstRevision(history).effectiveFrom, date) <= 0;
}

/** Revisions that take effect inside a period, which a valuation has to flag. */
export function revisionsIn(history: BillHistory, range: DateRange): BillRevision[] {
  return history.revisions.filter((r) => containsDate(range, r.effectiveFrom));
}

export function revisionCount(history: BillHistory): number {
  return history.revisions.length;
}

export function findRevision(history: BillHistory, reference: string): BillRevision {
  const found = history.revisions.find((r) => r.reference === reference);
  if (found === undefined) {
    throw new NotFoundError('bill revision', reference);
  }
  return found;
}

/** A new history with the revision added. The original is left alone. */
export function withRevision(history: BillHistory, revision: BillRevision): BillHistory {
  return buildBillHistory([...history.revisions, revision]);
}

export function describeRevision(revision: BillRevision): string {
  return `${revision.reference} from ${formatSiteDate(revision.effectiveFrom)}: ${revision.note}`;
}
