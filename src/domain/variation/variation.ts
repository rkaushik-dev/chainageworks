import { BadInputError } from '../../errors.js';
import type { ChainageRange } from '../chainage/range.js';
import type { ItemCode } from '../bill/itemCode.js';
import { formatItemCode, itemCodeKey } from '../bill/itemCode.js';
import type { Rate } from '../money/rate.js';
import type { Quantity } from '../quantity/quantity.js';
import type { SiteDate } from '../time/siteDate.js';
import { compareSiteDates, formatSiteDate } from '../time/siteDate.js';

/**
 * How far along a variation is.
 *
 * Only `instructed` and `agreed` change the works. A proposal has been priced
 * and not ordered, and a rejected one is kept so the file shows it was
 * considered, but neither of them may reach a valuation.
 */
export type VariationStatus = 'proposed' | 'instructed' | 'agreed' | 'rejected';

/**
 * What the change is priced on.
 *
 * The order here is the order the contract asks them to be tried in: the bill
 * rate if the work is the same, a pro rata rate if it is similar, a star rate
 * agreed for the job if it is not, and daywork only when the work cannot be
 * measured at all.
 */
export type ValuationBasis = 'bill_rates' | 'pro_rata' | 'star_rate' | 'daywork' | 'lump_sum';

export interface VariationItem {
  readonly code: ItemCode;
  /** Signed: an omission is negative. */
  readonly quantityChange: Quantity;
  /** A rate of its own, or null to use whatever the bill says. */
  readonly rate: Rate | null;
  readonly basis: ValuationBasis;
}

export interface Variation {
  readonly id: string;
  /** The engineer's instruction this arises from. */
  readonly reference: string;
  readonly title: string;
  readonly status: VariationStatus;
  readonly instructedOn: SiteDate | null;
  readonly agreedOn: SiteDate | null;
  readonly items: readonly VariationItem[];
  readonly extent: ChainageRange | null;
}

export interface VariationInput {
  readonly id: string;
  readonly reference: string;
  readonly title: string;
  readonly status?: VariationStatus;
  readonly instructedOn?: SiteDate | null;
  readonly agreedOn?: SiteDate | null;
  readonly items: readonly VariationItem[];
  readonly extent?: ChainageRange | null;
}

export function buildVariation(input: VariationInput): Variation {
  const status = input.status ?? 'proposed';
  const instructedOn = input.instructedOn ?? null;
  const agreedOn = input.agreedOn ?? null;

  if ((status === 'instructed' || status === 'agreed') && instructedOn === null) {
    throw new BadInputError('an instructed variation needs the date it was instructed', {
      variation: input.id,
      status,
    });
  }
  if (status === 'agreed' && agreedOn === null) {
    throw new BadInputError('an agreed variation needs the date it was agreed', {
      variation: input.id,
    });
  }
  if (
    agreedOn !== null &&
    instructedOn !== null &&
    compareSiteDates(agreedOn, instructedOn) < 0
  ) {
    throw new BadInputError('a variation cannot be agreed before it was instructed', {
      variation: input.id,
      instructed: formatSiteDate(instructedOn),
      agreed: formatSiteDate(agreedOn),
    });
  }
  if (input.items.length === 0) {
    throw new BadInputError('a variation that changes nothing is not a variation', {
      variation: input.id,
    });
  }
  const seen = new Set<string>();
  for (const item of input.items) {
    const key = itemCodeKey(item.code);
    if (seen.has(key)) {
      throw new BadInputError('a variation touches the same item twice', {
        variation: input.id,
        code: formatItemCode(item.code),
      });
    }
    seen.add(key);
    if (item.basis === 'bill_rates' && item.rate !== null) {
      throw new BadInputError('an item priced at bill rates cannot carry a rate of its own', {
        variation: input.id,
        code: formatItemCode(item.code),
      });
    }
    if (item.basis !== 'bill_rates' && item.rate === null) {
      throw new BadInputError('an item not priced at bill rates needs its own rate', {
        variation: input.id,
        code: formatItemCode(item.code),
        basis: item.basis,
      });
    }
    if (item.rate !== null && item.rate.unit !== item.quantityChange.unit) {
      throw new BadInputError('the variation rate and quantity are in different units', {
        variation: input.id,
        code: formatItemCode(item.code),
      });
    }
  }
  return {
    id: input.id,
    reference: input.reference,
    title: input.title,
    status,
    instructedOn,
    agreedOn,
    items: [...input.items],
    extent: input.extent ?? null,
  };
}

/**
 * Whether the variation has reached the works at a date.
 *
 * The instruction date is what counts, not the agreement date. Work ordered in
 * March and priced in June was still built in March, and valuing it from June
 * would leave three certificates short.
 */
export function isEffectiveAt(variation: Variation, asAt: SiteDate): boolean {
  if (variation.status !== 'instructed' && variation.status !== 'agreed') return false;
  if (variation.instructedOn === null) return false;
  return compareSiteDates(variation.instructedOn, asAt) <= 0;
}

export function isAgreed(variation: Variation): boolean {
  return variation.status === 'agreed';
}

/** Instructed but not yet agreed, which is what the risk register wants. */
export function isUnagreed(variation: Variation): boolean {
  return variation.status === 'instructed';
}

export function effectiveVariations(
  variations: readonly Variation[],
  asAt: SiteDate,
): Variation[] {
  return variations
    .filter((v) => isEffectiveAt(v, asAt))
    .sort((a, b) => {
      const left = a.instructedOn ?? 0;
      const right = b.instructedOn ?? 0;
      return left - right || (a.id < b.id ? -1 : 1);
    });
}

export function itemsFor(variation: Variation, code: ItemCode): VariationItem | null {
  const key = itemCodeKey(code);
  return variation.items.find((i) => itemCodeKey(i.code) === key) ?? null;
}

export function describeVariation(variation: Variation): string {
  const when =
    variation.instructedOn === null
      ? 'not instructed'
      : `instructed ${formatSiteDate(variation.instructedOn)}`;
  return `${variation.id} ${variation.title} (${variation.reference}, ${when})`;
}
