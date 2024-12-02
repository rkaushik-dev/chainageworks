import { ConflictError, NotFoundError } from '../../errors.js';
import type { Bill } from '../bill/bill.js';
import { findItem, withItem } from '../bill/bill.js';
import type { BillItem, BillItemKind } from '../bill/billItem.js';
import { buildBillItem } from '../bill/billItem.js';
import type { BillChange } from '../bill/diff.js';
import { diffBills } from '../bill/diff.js';
import { formatItemCode } from '../bill/itemCode.js';
import type { Money } from '../money/money.js';
import { addQuantities } from '../quantity/quantity.js';
import { isMeasurable } from '../quantity/unit.js';
import type { SiteDate } from '../time/siteDate.js';
import type { Variation, VariationItem } from './variation.js';
import { effectiveVariations } from './variation.js';

export interface VariationApplication {
  readonly bill: Bill;
  readonly applied: readonly string[];
  readonly changes: readonly BillChange[];
  readonly netValue: Money;
}

/**
 * Fold the variations in force at a date into the bill.
 *
 * A change priced at bill rates moves the quantity of an item that already
 * exists. Anything else brings its own rate, and if the bill has no item for it
 * one is created, because a star rate is by definition for work the bill never
 * described.
 *
 * Instructing a provisional item is the case worth naming. It does not change
 * the quantity or the rate at all: it changes the kind, and that is what makes
 * the item earnable. Nothing else in the system can turn provisional money into
 * money somebody can claim.
 */
export function applyVariations(
  bill: Bill,
  variations: readonly Variation[],
  asAt: SiteDate,
): VariationApplication {
  const inForce = effectiveVariations(variations, asAt);
  let working = bill;
  for (const variation of inForce) {
    for (const item of variation.items) {
      working = applyItem(working, variation, item);
    }
  }
  const diff = diffBills(bill, working);
  return {
    bill: working,
    applied: inForce.map((v) => v.id),
    changes: diff.changes,
    netValue: diff.netDelta,
  };
}

function applyItem(bill: Bill, variation: Variation, change: VariationItem): Bill {
  const existing = findItem(bill, change.code);
  if (change.basis === 'bill_rates') {
    if (existing === null) {
      throw new NotFoundError('bill item', formatItemCode(change.code));
    }
    return withItem(bill, requantified(existing, change));
  }
  if (existing === null) {
    return withItem(bill, createdFrom(variation, change));
  }
  return withItem(bill, repriced(existing, change));
}

/** Instructing a provisional item is what makes it earnable. */
function nextKind(kind: BillItemKind): BillItemKind {
  return kind === 'provisional' ? 'measured' : kind;
}

function requantified(item: BillItem, change: VariationItem): BillItem {
  const quantity = addQuantities(item.quantity, change.quantityChange);
  if (quantity.milli < 0) {
    throw new ConflictError('a variation would take an item below nothing', {
      code: formatItemCode(item.code),
    });
  }
  return buildBillItem({
    code: item.code,
    description: item.description,
    rate: item.rate,
    quantity,
    kind: nextKind(item.kind),
    chainage: item.chainage,
  });
}

function repriced(item: BillItem, change: VariationItem): BillItem {
  const rate = change.rate ?? item.rate;
  const quantity = addQuantities(item.quantity, change.quantityChange);
  if (quantity.milli < 0) {
    throw new ConflictError('a variation would take an item below nothing', {
      code: formatItemCode(item.code),
    });
  }
  return buildBillItem({
    code: item.code,
    description: item.description,
    rate,
    quantity,
    kind: nextKind(item.kind === 'measured' ? 'star' : item.kind),
    chainage: item.chainage,
  });
}

function createdFrom(variation: Variation, change: VariationItem): BillItem {
  const rate = change.rate;
  if (rate === null) {
    throw new ConflictError('a new variation item needs a rate', {
      variation: variation.id,
      code: formatItemCode(change.code),
    });
  }
  if (change.quantityChange.milli < 0) {
    throw new ConflictError('a variation cannot omit an item the bill does not carry', {
      variation: variation.id,
      code: formatItemCode(change.code),
    });
  }
  const measurable = isMeasurable(change.quantityChange.unit);
  return buildBillItem({
    code: change.code,
    description: `${variation.title} (${variation.reference})`,
    rate,
    quantity: change.quantityChange,
    kind: measurable ? 'star' : 'lump',
    chainage: variation.extent,
  });
}

/** What the variations in force are worth against the unvaried bill. */
export function variationValue(
  bill: Bill,
  variations: readonly Variation[],
  asAt: SiteDate,
): Money {
  return applyVariations(bill, variations, asAt).netValue;
}
