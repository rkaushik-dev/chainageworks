import { BadInputError, NotFoundError } from '../../errors.js';
import type { ItemCode } from '../bill/itemCode.js';
import { formatItemCode, itemCodeKey } from '../bill/itemCode.js';
import type { Money } from '../money/money.js';
import { subtractMoney, sumMoney, zeroMoney } from '../money/money.js';
import type { SiteDate } from '../time/siteDate.js';
import { compareSiteDates } from '../time/siteDate.js';
import type { PaymentApplication, PaymentTerms } from './payment.js';
import { buildPaymentTerms, paymentPosition } from './payment.js';
import type { PayLessNotice, PaymentNotice } from './payment.js';

/**
 * A subcontract, and which of the main contract's work it covers.
 *
 * The packages and the bill items are both recorded because they answer
 * different questions. The packages say where on the ground the subcontractor
 * is working, which is what a site manager needs. The items say what they are
 * paid against, which is what the account needs, and the two are not the same
 * list as soon as one package is split between two trades.
 */
export interface Subcontract {
  readonly id: string;
  readonly name: string;
  readonly contractor: string;
  readonly orderValue: Money;
  readonly packageIds: readonly string[];
  readonly codes: readonly ItemCode[];
  readonly terms: PaymentTerms;
  readonly placedOn: SiteDate;
}

export interface SubcontractInput {
  readonly id: string;
  readonly name: string;
  readonly contractor: string;
  readonly orderValue: Money;
  readonly packageIds?: readonly string[];
  readonly codes?: readonly ItemCode[];
  readonly terms?: PaymentTerms;
  readonly placedOn: SiteDate;
}

export function buildSubcontract(input: SubcontractInput): Subcontract {
  if (input.contractor.trim() === '') {
    throw new BadInputError('a subcontract has to name the subcontractor', {
      subcontract: input.id,
    });
  }
  if (input.orderValue.minor < 0) {
    throw new BadInputError('an order value cannot be negative', {
      subcontract: input.id,
    });
  }
  const seen = new Set<string>();
  for (const code of input.codes ?? []) {
    const key = itemCodeKey(code);
    if (seen.has(key)) {
      throw new BadInputError('the same bill item is on one subcontract twice', {
        subcontract: input.id,
        code: formatItemCode(code),
      });
    }
    seen.add(key);
  }
  return {
    id: input.id,
    name: input.name,
    contractor: input.contractor.trim(),
    orderValue: input.orderValue,
    packageIds: [...(input.packageIds ?? [])],
    codes: [...(input.codes ?? [])],
    terms: input.terms ?? buildPaymentTerms(),
    placedOn: input.placedOn,
  };
}

export function coversPackage(subcontract: Subcontract, packageId: string): boolean {
  return subcontract.packageIds.includes(packageId);
}

export function coversCode(subcontract: Subcontract, code: ItemCode): boolean {
  const key = itemCodeKey(code);
  return subcontract.codes.some((c) => itemCodeKey(c) === key);
}

export interface SubcontractPosition {
  readonly subcontract: Subcontract;
  readonly applications: number;
  readonly certifiedToDate: Money;
  readonly paidToDate: Money;
  /** Order value less what has been certified. */
  readonly remaining: Money;
  /** Applications that defaulted to the applied for sum. */
  readonly defaulted: readonly string[];
}

/**
 * Where a subcontract stands at a date.
 *
 * Certified and paid are kept apart because they are different facts: a
 * certificate that has been issued and not paid is a cash problem, and one that
 * was never issued at all is a notice problem. Reporting one figure hides which
 * of the two the job has.
 */
export function subcontractPosition(
  subcontract: Subcontract,
  applications: readonly PaymentApplication[],
  notices: readonly PaymentNotice[],
  payLess: readonly PayLessNotice[],
  asAt: SiteDate,
): SubcontractPosition {
  const currency = subcontract.orderValue.currency;
  const mine = applications
    .filter(
      (a) => a.subcontractId === subcontract.id && compareSiteDates(a.appliedOn, asAt) <= 0,
    )
    .sort((a, b) => a.periodNumber - b.periodNumber);

  const paid: Money[] = [];
  const defaulted: string[] = [];
  let certified = zeroMoney(currency);

  for (const application of mine) {
    const position = paymentPosition(
      application,
      subcontract.terms,
      notices.find((n) => n.applicationId === application.id) ?? null,
      payLess.find((n) => n.applicationId === application.id) ?? null,
    );
    certified = position.netOfRetention;
    paid.push(position.payable);
    if (position.byDefault) defaulted.push(application.id);
  }

  return {
    subcontract,
    applications: mine.length,
    certifiedToDate: certified,
    paidToDate: sumMoney(paid, currency),
    remaining: subtractMoney(subcontract.orderValue, certified),
    defaulted,
  };
}

export function findSubcontract(
  subcontracts: readonly Subcontract[],
  id: string,
): Subcontract | null {
  return subcontracts.find((s) => s.id === id) ?? null;
}

export function requireSubcontract(
  subcontracts: readonly Subcontract[],
  id: string,
): Subcontract {
  const found = findSubcontract(subcontracts, id);
  if (found === null) throw new NotFoundError('subcontract', id);
  return found;
}

/** Subcontracts whose certified total has run past the order value. */
export function overOrder(positions: readonly SubcontractPosition[]): SubcontractPosition[] {
  return positions.filter((position) => position.remaining.minor < 0);
}

export function describeSubcontract(position: SubcontractPosition): string {
  const state = position.remaining.minor < 0 ? 'over the order' : 'inside the order';
  return (
    `${position.subcontract.id} ${position.subcontract.contractor}, ` +
    `${String(position.applications)} applications, ${state}`
  );
}
