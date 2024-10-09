import { BadInputError } from '../../errors.js';
import type { Money } from '../money/money.js';
import { subtractMoney, sumMoney, zeroMoney } from '../money/money.js';
import { ratesEqual } from '../money/rate.js';
import { quantitiesEqual } from '../quantity/quantity.js';
import type { Bill } from './bill.js';
import { findItem } from './bill.js';
import type { BillItem } from './billItem.js';
import { billedValue } from './billItem.js';
import type { ItemCode } from './itemCode.js';
import { compareItemCodes, formatItemCode, itemCodeKey } from './itemCode.js';

export type BillChangeKind =
  | 'added'
  | 'removed'
  | 'rate_changed'
  | 'quantity_changed'
  | 'rate_and_quantity_changed'
  | 'description_changed'
  | 'kind_changed';

export interface BillChange {
  readonly code: ItemCode;
  readonly kind: BillChangeKind;
  readonly before: BillItem | null;
  readonly after: BillItem | null;
  /** What the change is worth: the value after less the value before. */
  readonly delta: Money;
}

export interface BillDiff {
  readonly changes: readonly BillChange[];
  readonly netDelta: Money;
}

/**
 * What one revision of the bill did to the last one.
 *
 * The valuation needs this for two reasons. A rate change has to be flagged to
 * whoever signs the certificate, and a quantity change is what tells the
 * variation account whether the movement was priced or measured.
 */
export function diffBills(before: Bill, after: Bill): BillDiff {
  if (before.currency !== after.currency) {
    throw new BadInputError('cannot compare bills in different currencies', {
      before: before.currency,
      after: after.currency,
    });
  }
  const currency = before.currency;
  const changes: BillChange[] = [];

  for (const item of before.items) {
    const now = findItem(after, item.code);
    if (now === null) {
      changes.push({
        code: item.code,
        kind: 'removed',
        before: item,
        after: null,
        delta: subtractMoney(zeroMoney(currency), billedValue(item)),
      });
      continue;
    }
    const kind = classifyChange(item, now);
    if (kind === null) continue;
    changes.push({
      code: item.code,
      kind,
      before: item,
      after: now,
      delta: subtractMoney(billedValue(now), billedValue(item)),
    });
  }

  const beforeKeys = new Set(before.items.map((i) => itemCodeKey(i.code)));
  for (const item of after.items) {
    if (beforeKeys.has(itemCodeKey(item.code))) continue;
    changes.push({
      code: item.code,
      kind: 'added',
      before: null,
      after: item,
      delta: billedValue(item),
    });
  }

  changes.sort((a, b) => compareItemCodes(a.code, b.code));
  return {
    changes,
    netDelta: sumMoney(
      changes.map((c) => c.delta),
      currency,
    ),
  };
}

/**
 * A rate change and a quantity change together get their own name rather than
 * being reported twice, because a reader who sees two lines for one item
 * assumes the item moved twice.
 */
function classifyChange(before: BillItem, after: BillItem): BillChangeKind | null {
  const rateMoved = !ratesEqual(before.rate, after.rate);
  const quantityMoved = !quantitiesEqual(before.quantity, after.quantity);
  if (rateMoved && quantityMoved) return 'rate_and_quantity_changed';
  if (rateMoved) return 'rate_changed';
  if (quantityMoved) return 'quantity_changed';
  if (before.kind !== after.kind) return 'kind_changed';
  if (before.description !== after.description) return 'description_changed';
  return null;
}

export function changesOfKind(diff: BillDiff, kind: BillChangeKind): BillChange[] {
  return diff.changes.filter((c) => c.kind === kind);
}

export function hasRateChanges(diff: BillDiff): boolean {
  return diff.changes.some(
    (c) => c.kind === 'rate_changed' || c.kind === 'rate_and_quantity_changed',
  );
}

export function isEmptyDiff(diff: BillDiff): boolean {
  return diff.changes.length === 0;
}

export function describeChange(change: BillChange): string {
  return `${formatItemCode(change.code)} ${change.kind.replace(/_/g, ' ')}`;
}
