import { BadInputError } from '../../errors.js';
import type { ChainageRange } from '../chainage/range.js';
import { formatChainageRange } from '../chainage/range.js';
import type { Money } from '../money/money.js';
import type { Rate } from '../money/rate.js';
import { extend, formatRate } from '../money/rate.js';
import type { Quantity } from '../quantity/quantity.js';
import { formatQuantity, isZeroQuantity, quantity } from '../quantity/quantity.js';
import type { UnitCode } from '../quantity/unit.js';
import { isMeasurable } from '../quantity/unit.js';
import type { ItemCode } from './itemCode.js';
import { formatItemCode } from './itemCode.js';

/**
 * What kind of money an item is.
 *
 * The distinction is not cosmetic. A measured item earns as it is built. A
 * provisional item is in the bill so the tender adds up and earns nothing until
 * it is instructed. A star rate is one agreed after the event for work the bill
 * never described, and it is the only kind that can appear mid contract.
 */
export type BillItemKind = 'measured' | 'provisional' | 'prime_cost' | 'lump' | 'star';

export interface BillItem {
  readonly code: ItemCode;
  readonly description: string;
  readonly unit: UnitCode;
  readonly rate: Rate;
  /** The quantity the bill was priced on, not what has been built. */
  readonly quantity: Quantity;
  readonly kind: BillItemKind;
  /** Where on the alignment the item applies, when the bill says. */
  readonly chainage: ChainageRange | null;
}

export interface BillItemInput {
  readonly code: ItemCode;
  readonly description: string;
  readonly rate: Rate;
  readonly quantity: Quantity;
  readonly kind?: BillItemKind;
  readonly chainage?: ChainageRange | null;
}

export function buildBillItem(input: BillItemInput): BillItem {
  const kind = input.kind ?? 'measured';
  if (input.rate.unit !== input.quantity.unit) {
    throw new BadInputError('the rate and the billed quantity are in different units', {
      code: formatItemCode(input.code),
      rate: input.rate.unit,
      quantity: input.quantity.unit,
    });
  }
  if (input.description.trim() === '') {
    throw new BadInputError('a bill item needs a description', {
      code: formatItemCode(input.code),
    });
  }
  if (input.quantity.milli < 0) {
    throw new BadInputError('a billed quantity cannot be negative', {
      code: formatItemCode(input.code),
      quantity: formatQuantity(input.quantity),
    });
  }
  if (kind === 'lump' && isMeasurable(input.quantity.unit)) {
    throw new BadInputError('a lump item is billed as a sum', {
      code: formatItemCode(input.code),
      unit: input.quantity.unit,
    });
  }
  if (!isMeasurable(input.quantity.unit) && kind !== 'lump' && kind !== 'prime_cost') {
    throw new BadInputError('a sum item has to be a lump or a prime cost item', {
      code: formatItemCode(input.code),
      kind,
    });
  }
  return {
    code: input.code,
    description: input.description.trim(),
    unit: input.quantity.unit,
    rate: input.rate,
    quantity: input.quantity,
    kind,
    chainage: input.chainage ?? null,
  };
}

/** What the bill says the item is worth at the billed quantity. */
export function billedValue(item: BillItem): Money {
  return extend(item.rate, item.quantity);
}

export function isMeasuredItem(item: BillItem): boolean {
  return item.kind === 'measured' || item.kind === 'star';
}

/** Provisional and prime cost money is in the total but is not yet earnable. */
export function isProvisionalItem(item: BillItem): boolean {
  return item.kind === 'provisional' || item.kind === 'prime_cost';
}

export function isLumpItem(item: BillItem): boolean {
  return item.kind === 'lump';
}

export function isNilQuantityItem(item: BillItem): boolean {
  return isZeroQuantity(item.quantity);
}

/**
 * Whether an item can be claimed against at all. A provisional item cannot,
 * until an instruction turns it into a measured one, and that instruction is a
 * variation rather than an edit of the bill.
 */
export function isEarnable(item: BillItem): boolean {
  return !isProvisionalItem(item);
}

export function describeBillItem(item: BillItem): string {
  const where = item.chainage === null ? '' : ` [${formatChainageRange(item.chainage)}]`;
  return (
    `${formatItemCode(item.code)} ${item.description}${where} ` +
    `${formatQuantity(item.quantity)} at ${formatRate(item.rate)}`
  );
}

/** A copy of the item with a different billed quantity, used by variations. */
export function reQuantify(item: BillItem, amount: number): BillItem {
  return buildBillItem({
    code: item.code,
    description: item.description,
    rate: item.rate,
    quantity: quantity(amount, item.unit),
    kind: item.kind,
    chainage: item.chainage,
  });
}
