import { BadInputError } from '../errors.js';
import type { Money } from '../domain/money/money.js';
import { maxMoney, minMoney, subtractMoney, zeroMoney } from '../domain/money/money.js';
import type { Percent } from '../domain/money/percent.js';
import { applyPercent, FULL_PERCENT, percent } from '../domain/money/percent.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { compareSiteDates, formatSiteDate } from '../domain/time/siteDate.js';

/**
 * A retention bond, which stands in place of cash retention.
 *
 * The trade is straightforward: the contractor pays a bank a percentage a year
 * for a guarantee, and in exchange the employer stops holding cash off every
 * certificate. Which is cheaper depends entirely on how long the money would
 * otherwise sit there, which is why the two have to be comparable rather than
 * a setting somebody picks once.
 */
export interface RetentionBond {
  readonly id: string;
  readonly surety: string;
  /** The most the bond can be called for, usually the retention cap. */
  readonly maximum: Money;
  readonly issuedOn: SiteDate;
  /** When the bond lapses, whether or not the works are finished. */
  readonly expiresOn: SiteDate;
  /** What the surety charges a year, against the bonded amount. */
  readonly rate: Percent;
  /** Reduced amount once substantial completion is certified, if any. */
  readonly reducesTo: Money | null;
}

export interface RetentionBondInput {
  readonly id: string;
  readonly surety: string;
  readonly maximum: Money;
  readonly issuedOn: SiteDate;
  readonly expiresOn: SiteDate;
  readonly rate?: Percent;
  readonly reducesTo?: Money | null;
}

export const DEFAULT_BOND_RATE: Percent = percent(1);

export function buildRetentionBond(input: RetentionBondInput): RetentionBond {
  if (input.maximum.minor <= 0) {
    throw new BadInputError('a bond has to be for some amount', { bond: input.id });
  }
  if (compareSiteDates(input.expiresOn, input.issuedOn) <= 0) {
    throw new BadInputError('a bond cannot expire before it is issued', {
      bond: input.id,
      issuedOn: formatSiteDate(input.issuedOn),
      expiresOn: formatSiteDate(input.expiresOn),
    });
  }
  const rate = input.rate ?? DEFAULT_BOND_RATE;
  if (rate.basisPoints < 0 || rate.basisPoints > FULL_PERCENT.basisPoints) {
    throw new BadInputError('a bond rate runs from 0 to 100', { bond: input.id });
  }
  const reducesTo = input.reducesTo ?? null;
  if (reducesTo !== null && reducesTo.minor > input.maximum.minor) {
    throw new BadInputError('a bond cannot reduce to more than it started at', {
      bond: input.id,
    });
  }
  return {
    id: input.id,
    surety: input.surety,
    maximum: input.maximum,
    issuedOn: input.issuedOn,
    expiresOn: input.expiresOn,
    rate,
    reducesTo,
  };
}

export interface BondPosition {
  readonly bond: RetentionBond;
  readonly asAt: SiteDate;
  /** What the bond covers on this date. */
  readonly cover: Money;
  /** What the employer would otherwise be holding in cash. */
  readonly wouldHold: Money;
  /** Cash the contractor has because the bond is in place. */
  readonly released: Money;
  /** The part of the cash retention the bond does not reach. */
  readonly shortfall: Money;
  readonly expired: boolean;
  readonly daysToExpiry: number;
}

/**
 * Where a bond stands against the cash it replaces.
 *
 * The shortfall is the figure worth reporting. A bond written for the
 * retention cap covers the whole of it, but one written for a round number at
 * tender stops covering somewhere in the middle of the job, and the employer
 * is entitled to hold cash for the difference from that moment.
 */
export function bondPosition(
  bond: RetentionBond,
  wouldHold: Money,
  asAt: SiteDate,
  substantialCompletionOn: SiteDate | null = null,
): BondPosition {
  const currency = bond.maximum.currency;
  const expired = compareSiteDates(asAt, bond.expiresOn) > 0;
  const notYet = compareSiteDates(asAt, bond.issuedOn) < 0;

  const reduction = bond.reducesTo;
  const reduced =
    reduction !== null &&
    substantialCompletionOn !== null &&
    compareSiteDates(asAt, substantialCompletionOn) >= 0;

  const face = reduced ? reduction : bond.maximum;
  const cover = expired || notYet ? zeroMoney(currency) : face;
  const released = minMoney(cover, wouldHold);

  return {
    bond,
    asAt,
    cover,
    wouldHold,
    released,
    shortfall: maxMoney(subtractMoney(wouldHold, cover), zeroMoney(currency)),
    expired,
    daysToExpiry: bond.expiresOn - asAt,
  };
}

/**
 * What the bond costs for a year on the cover it carries.
 *
 * Charged on the face of the bond rather than on what is drawn against it,
 * because that is how a surety prices one. A contractor who thinks the charge
 * follows the retention it replaces gets a nasty surprise in the month the
 * works stop and the bond does not.
 */
export function annualCharge(bond: RetentionBond): Money {
  return applyPercent(bond.maximum, bond.rate);
}

/** The whole charge from issue to expiry, which is what the trade turns on. */
export function totalCharge(bond: RetentionBond): Money {
  const years = (bond.expiresOn - bond.issuedOn) / 365;
  const annual = annualCharge(bond);
  return { minor: Math.round(annual.minor * years), currency: annual.currency };
}

/** True when the bond costs less than the cash it frees up would earn. */
export function isWorthTaking(
  bond: RetentionBond,
  wouldHold: Money,
  interestRate: Percent,
): boolean {
  const years = (bond.expiresOn - bond.issuedOn) / 365;
  const covered = minMoney(bond.maximum, wouldHold);
  const interest = applyPercent(covered, interestRate);
  return totalCharge(bond).minor < Math.round(interest.minor * years);
}

/** Bonds within a window of lapsing, which is the list somebody has to chase. */
export function expiringWithin(
  bonds: readonly RetentionBond[],
  asAt: SiteDate,
  days: number,
): RetentionBond[] {
  return bonds
    .filter((bond) => {
      const left = bond.expiresOn - asAt;
      return left >= 0 && left <= days;
    })
    .sort((a, b) => a.expiresOn - b.expiresOn);
}

export function describeBond(position: BondPosition): string {
  if (position.expired) {
    return `${position.bond.id} expired on ${formatSiteDate(position.bond.expiresOn)}`;
  }
  const gap =
    position.shortfall.minor === 0
      ? 'covers the retention in full'
      : `${String(position.shortfall.minor / 100)} short of the retention`;
  return `${position.bond.id} with ${position.bond.surety}, ${gap}`;
}
