import { BadInputError } from '../../errors.js';
import type { Money } from '../money/money.js';
import { addMoney, subtractMoney, sumMoney, zeroMoney } from '../money/money.js';
import type { SiteDate } from '../time/siteDate.js';
import { addDays, compareSiteDates, formatSiteDate } from '../time/siteDate.js';

/**
 * What a contra charge is for.
 *
 * The reason matters more than the amount, because the reasons are not equally
 * defensible. Attendance and welfare are usually agreed in the order and go
 * through unopposed; rectification and damage are charged after the event and
 * are the ones that end up in an adjudication.
 */
export type ContraReason =
  | 'attendance'
  | 'welfare'
  | 'plant_hire'
  | 'rectification'
  | 'damage'
  | 'clean_up'
  | 'other';

export const CONTRA_REASONS: readonly ContraReason[] = [
  'attendance',
  'welfare',
  'plant_hire',
  'rectification',
  'damage',
  'clean_up',
  'other',
];

/** Reasons the order normally covers, which the subcontractor cannot dispute. */
const AGREED_IN_ORDER: readonly ContraReason[] = ['attendance', 'welfare', 'plant_hire'];

export interface ContraCharge {
  readonly id: string;
  readonly subcontractId: string;
  readonly reference: string;
  readonly reason: ContraReason;
  readonly description: string;
  readonly amount: Money;
  readonly incurredOn: SiteDate;
  /** When the subcontractor was told. Null means nobody has told them yet. */
  readonly notifiedOn: SiteDate | null;
  readonly disputed: boolean;
}

export interface ContraChargeInput {
  readonly id: string;
  readonly subcontractId: string;
  readonly reference?: string;
  readonly reason?: ContraReason;
  readonly description: string;
  readonly amount: Money;
  readonly incurredOn: SiteDate;
  readonly notifiedOn?: SiteDate | null;
  readonly disputed?: boolean;
}

export function buildContraCharge(input: ContraChargeInput): ContraCharge {
  if (input.amount.minor < 0) {
    throw new BadInputError('a contra charge cannot be negative', { charge: input.id });
  }
  if (input.description.trim() === '') {
    throw new BadInputError('a contra charge has to say what it is for', {
      charge: input.id,
    });
  }
  const notifiedOn = input.notifiedOn ?? null;
  if (notifiedOn !== null && compareSiteDates(notifiedOn, input.incurredOn) < 0) {
    throw new BadInputError('a charge cannot be notified before it was incurred', {
      charge: input.id,
      incurredOn: formatSiteDate(input.incurredOn),
      notifiedOn: formatSiteDate(notifiedOn),
    });
  }
  return {
    id: input.id,
    subcontractId: input.subcontractId,
    reference: input.reference ?? input.id,
    reason: input.reason ?? 'other',
    description: input.description.trim(),
    amount: input.amount,
    incurredOn: input.incurredOn,
    notifiedOn,
    disputed: input.disputed ?? false,
  };
}

/**
 * Whether a charge may be set against a payment at a date.
 *
 * A charge the order already covers stands on the day it is incurred. Anything
 * else has to have been notified, and notified inside the window, because a
 * charge produced for the first time on a final account is the one thing every
 * subcontract dispute has in common.
 */
export function isDeductibleAt(
  charge: ContraCharge,
  asAt: SiteDate,
  notifyDays: number,
): boolean {
  if (compareSiteDates(charge.incurredOn, asAt) > 0) return false;
  if (AGREED_IN_ORDER.includes(charge.reason)) return true;
  if (charge.notifiedOn === null) return false;
  if (compareSiteDates(charge.notifiedOn, asAt) > 0) return false;
  return compareSiteDates(charge.notifiedOn, addDays(charge.incurredOn, notifyDays)) <= 0;
}

/** Charges that lost their right to be set off because nobody served notice. */
export function barredCharges(
  charges: readonly ContraCharge[],
  asAt: SiteDate,
  notifyDays: number,
): ContraCharge[] {
  return charges.filter(
    (charge) =>
      compareSiteDates(charge.incurredOn, asAt) <= 0 &&
      !AGREED_IN_ORDER.includes(charge.reason) &&
      !isDeductibleAt(charge, asAt, notifyDays) &&
      compareSiteDates(addDays(charge.incurredOn, notifyDays), asAt) < 0,
  );
}

export interface ContraPosition {
  readonly subcontractId: string;
  readonly asAt: SiteDate;
  /** Everything raised against the order by the date, disputed or not. */
  readonly raised: Money;
  /** What may actually come off the next payment. */
  readonly deductible: Money;
  /** Deductible, but the subcontractor is arguing about it. */
  readonly disputed: Money;
  readonly barred: Money;
  readonly charges: readonly ContraCharge[];
}

export function contraPosition(
  charges: readonly ContraCharge[],
  subcontractId: string,
  asAt: SiteDate,
  notifyDays: number,
  currency: Money['currency'],
): ContraPosition {
  const mine = charges
    .filter(
      (charge) =>
        charge.subcontractId === subcontractId &&
        compareSiteDates(charge.incurredOn, asAt) <= 0,
    )
    .sort((a, b) => compareSiteDates(a.incurredOn, b.incurredOn) || a.id.localeCompare(b.id));

  const deductible = mine.filter((charge) => isDeductibleAt(charge, asAt, notifyDays));

  return {
    subcontractId,
    asAt,
    raised: sumMoney(
      mine.map((c) => c.amount),
      currency,
    ),
    deductible: sumMoney(
      deductible.map((c) => c.amount),
      currency,
    ),
    disputed: sumMoney(
      deductible.filter((c) => c.disputed).map((c) => c.amount),
      currency,
    ),
    barred: sumMoney(
      barredCharges(mine, asAt, notifyDays).map((c) => c.amount),
      currency,
    ),
    charges: mine,
  };
}

/**
 * Take the deductible charges off a payment, never below nothing.
 *
 * A set off larger than the payment does not become a debt the subcontractor
 * owes on this certificate. It waits for the next one, which is what the
 * contract says and also the only way the arithmetic stays honest when the
 * account is finally settled.
 */
export function applyContra(payable: Money, position: ContraPosition): Money {
  const after = subtractMoney(payable, position.deductible);
  return after.minor < 0 ? zeroMoney(payable.currency) : after;
}

/** What could not be taken this time and rolls into the next payment. */
export function carriedForward(payable: Money, position: ContraPosition): Money {
  const after = subtractMoney(payable, position.deductible);
  return after.minor < 0
    ? addMoney(zeroMoney(payable.currency), { minor: -after.minor, currency: after.currency })
    : zeroMoney(payable.currency);
}

export function describeContra(position: ContraPosition): string {
  return (
    `${position.subcontractId} at ${formatSiteDate(position.asAt)}, ` +
    `${String(position.charges.length)} charges, ` +
    `${String(position.deductible.minor / 100)} deductible`
  );
}
