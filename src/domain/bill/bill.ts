import { BadInputError, NotFoundError } from '../../errors.js';
import type { CurrencyCode, Money } from '../money/money.js';
import { sumMoney, zeroMoney } from '../money/money.js';
import type { BillItem } from './billItem.js';
import { billedValue, isEarnable, isProvisionalItem } from './billItem.js';
import type { ItemCode } from './itemCode.js';
import {
  compareItemCodes,
  formatItemCode,
  isAncestorCode,
  itemCodeKey,
  itemCodesEqual,
  workClassName,
} from './itemCode.js';

/**
 * A priced bill of quantities.
 *
 * Held sorted, because everything downstream reads it in bill order and a bill
 * that arrives in spreadsheet order would put the rollups in the wrong place.
 * Held immutable, because the revision history is what decides which rates
 * apply on a given date, and a bill that can be edited in place has no history.
 */
export interface Bill {
  readonly id: string;
  readonly title: string;
  readonly currency: CurrencyCode;
  readonly items: readonly BillItem[];
}

export interface BillInput {
  readonly id: string;
  readonly title: string;
  readonly currency: CurrencyCode;
  readonly items: readonly BillItem[];
}

export function buildBill(input: BillInput): Bill {
  const seen = new Set<string>();
  for (const item of input.items) {
    const key = itemCodeKey(item.code);
    if (seen.has(key)) {
      throw new BadInputError('the same item code appears twice in one bill', {
        bill: input.id,
        code: formatItemCode(item.code),
      });
    }
    seen.add(key);
    if (item.rate.currency !== input.currency) {
      throw new BadInputError('an item is priced in a different currency from the bill', {
        bill: input.id,
        code: formatItemCode(item.code),
        billCurrency: input.currency,
        itemCurrency: item.rate.currency,
      });
    }
  }
  const items = [...input.items].sort((a, b) => compareItemCodes(a.code, b.code));
  return { id: input.id, title: input.title, currency: input.currency, items };
}

export function billItemCount(bill: Bill): number {
  return bill.items.length;
}

export function findItem(bill: Bill, code: ItemCode): BillItem | null {
  return bill.items.find((item) => itemCodesEqual(item.code, code)) ?? null;
}

export function requireItem(bill: Bill, code: ItemCode): BillItem {
  const item = findItem(bill, code);
  if (item === null) {
    throw new NotFoundError('bill item', formatItemCode(code));
  }
  return item;
}

export function hasItem(bill: Bill, code: ItemCode): boolean {
  return findItem(bill, code) !== null;
}

/** Every item that sits under a heading, in bill order. */
export function itemsUnder(bill: Bill, heading: ItemCode): BillItem[] {
  return bill.items.filter((item) => isAncestorCode(heading, item.code));
}

export function billTotal(bill: Bill): Money {
  return sumMoney(bill.items.map(billedValue), bill.currency);
}

/** What is actually earnable: the total less the provisional and prime cost. */
export function measuredTotal(bill: Bill): Money {
  return sumMoney(bill.items.filter(isEarnable).map(billedValue), bill.currency);
}

export function provisionalTotal(bill: Bill): Money {
  return sumMoney(bill.items.filter(isProvisionalItem).map(billedValue), bill.currency);
}

export function totalUnder(bill: Bill, heading: ItemCode): Money {
  return sumMoney(itemsUnder(bill, heading).map(billedValue), bill.currency);
}

export interface ClassRollup {
  readonly workClass: string;
  readonly name: string;
  readonly items: number;
  readonly value: Money;
}

/** One line per work class, in class order, for the summary page of a bill. */
export function rollupByClass(bill: Bill): ClassRollup[] {
  const byClass = new Map<string, BillItem[]>();
  for (const item of bill.items) {
    const existing = byClass.get(item.code.workClass);
    if (existing === undefined) byClass.set(item.code.workClass, [item]);
    else existing.push(item);
  }
  return [...byClass.entries()]
    .sort(([a], [b]) => (a < b ? -1 : 1))
    .map(([workClass, items]) => ({
      workClass,
      name: workClassName(items[0]?.code ?? { workClass, divisions: [], suffix: null }),
      items: items.length,
      value: sumMoney(items.map(billedValue), bill.currency),
    }));
}

/** A new bill with the item added, or replacing one with the same code. */
export function withItem(bill: Bill, item: BillItem): Bill {
  const kept = bill.items.filter((existing) => !itemCodesEqual(existing.code, item.code));
  return buildBill({ ...bill, items: [...kept, item] });
}

export function withoutItem(bill: Bill, code: ItemCode): Bill {
  const kept = bill.items.filter((existing) => !itemCodesEqual(existing.code, code));
  if (kept.length === bill.items.length) {
    throw new NotFoundError('bill item', formatItemCode(code));
  }
  return buildBill({ ...bill, items: kept });
}

export function emptyBill(id: string, title: string, currency: CurrencyCode): Bill {
  return { id, title, currency, items: [] };
}

export function billIsEmpty(bill: Bill): boolean {
  return bill.items.length === 0;
}

export function zeroForBill(bill: Bill): Money {
  return zeroMoney(bill.currency);
}
