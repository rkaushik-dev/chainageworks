import { BadInputError } from '../errors.js';
import type { ItemCode } from '../domain/bill/itemCode.js';
import type { CurrencyCode, Money } from '../domain/money/money.js';
import { sumMoney, zeroMoney } from '../domain/money/money.js';
import type { Percent } from '../domain/money/percent.js';
import { applyPercent, percent } from '../domain/money/percent.js';
import type { Quantity } from '../domain/quantity/quantity.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { compareSiteDates, formatSiteDate } from '../domain/time/siteDate.js';

/**
 * Unfixed materials standing on site.
 *
 * Four conditions have to hold before any of it can be certified, and the
 * fourth is the one that gets missed: the material has to still be unfixed. The
 * moment it is built in, the measured work carries its value, and leaving it in
 * this list as well pays for it twice.
 */
export interface MaterialsOnSite {
  readonly id: string;
  readonly packageId: string;
  readonly code: ItemCode;
  readonly description: string;
  readonly quantity: Quantity;
  /** The invoice value, before any allowance is applied. */
  readonly value: Money;
  readonly deliveredOn: SiteDate;
  /** When title passed to the employer. Null means it has not. */
  readonly vestedOn: SiteDate | null;
  /** When it stopped being unfixed material and became part of the works. */
  readonly incorporatedOn: SiteDate | null;
  readonly protectedOnSite: boolean;
}

export interface MaterialsInput {
  readonly id: string;
  readonly packageId: string;
  readonly code: ItemCode;
  readonly description: string;
  readonly quantity: Quantity;
  readonly value: Money;
  readonly deliveredOn: SiteDate;
  readonly vestedOn?: SiteDate | null;
  readonly incorporatedOn?: SiteDate | null;
  readonly protectedOnSite?: boolean;
}

/** What a contract usually allows against the invoice value. */
export const DEFAULT_MATERIALS_ALLOWANCE: Percent = percent(90);

export function buildMaterialsOnSite(input: MaterialsInput): MaterialsOnSite {
  if (input.value.minor < 0) {
    throw new BadInputError('materials on site cannot carry a negative value', {
      materials: input.id,
    });
  }
  if (input.description.trim() === '') {
    throw new BadInputError('materials on site need a description', {
      materials: input.id,
    });
  }
  const vestedOn = input.vestedOn ?? null;
  const incorporatedOn = input.incorporatedOn ?? null;
  if (vestedOn !== null && compareSiteDates(vestedOn, input.deliveredOn) < 0) {
    throw new BadInputError('materials cannot vest before they were delivered', {
      materials: input.id,
      delivered: formatSiteDate(input.deliveredOn),
      vested: formatSiteDate(vestedOn),
    });
  }
  if (incorporatedOn !== null && compareSiteDates(incorporatedOn, input.deliveredOn) < 0) {
    throw new BadInputError('materials cannot be built in before they were delivered', {
      materials: input.id,
      delivered: formatSiteDate(input.deliveredOn),
      incorporated: formatSiteDate(incorporatedOn),
    });
  }
  return {
    id: input.id,
    packageId: input.packageId,
    code: input.code,
    description: input.description.trim(),
    quantity: input.quantity,
    value: input.value,
    deliveredOn: input.deliveredOn,
    vestedOn,
    incorporatedOn,
    protectedOnSite: input.protectedOnSite ?? true,
  };
}

export type NotClaimableReason =
  | 'not_delivered'
  | 'not_vested'
  | 'not_protected'
  | 'incorporated';

/**
 * Why this lot cannot be certified at a cut-off, or null when it can. The
 * checks are in the order a quantity surveyor walks them, and the first one
 * that fails is the one reported.
 */
export function reasonNotClaimable(
  materials: MaterialsOnSite,
  asAt: SiteDate,
): NotClaimableReason | null {
  if (compareSiteDates(materials.deliveredOn, asAt) > 0) return 'not_delivered';
  if (materials.vestedOn === null || compareSiteDates(materials.vestedOn, asAt) > 0) {
    return 'not_vested';
  }
  if (!materials.protectedOnSite) return 'not_protected';
  if (
    materials.incorporatedOn !== null &&
    compareSiteDates(materials.incorporatedOn, asAt) <= 0
  ) {
    return 'incorporated';
  }
  return null;
}

export function isClaimableAt(materials: MaterialsOnSite, asAt: SiteDate): boolean {
  return reasonNotClaimable(materials, asAt) === null;
}

export function claimableMaterials(
  materials: readonly MaterialsOnSite[],
  asAt: SiteDate,
): MaterialsOnSite[] {
  return materials.filter((m) => isClaimableAt(m, asAt));
}

/** The certified value of unfixed materials, after the contract allowance. */
export function materialsValue(
  materials: readonly MaterialsOnSite[],
  asAt: SiteDate,
  currency: CurrencyCode,
  allowance: Percent = DEFAULT_MATERIALS_ALLOWANCE,
): Money {
  const claimable = claimableMaterials(materials, asAt);
  if (claimable.length === 0) return zeroMoney(currency);
  return sumMoney(
    claimable.map((m) => applyPercent(m.value, allowance)),
    currency,
  );
}

/** What dropped out of the list between two cut-offs, and why. */
export function materialsMovement(
  materials: readonly MaterialsOnSite[],
  from: SiteDate,
  to: SiteDate,
): { added: MaterialsOnSite[]; removed: MaterialsOnSite[] } {
  const before = new Set(claimableMaterials(materials, from).map((m) => m.id));
  const after = new Set(claimableMaterials(materials, to).map((m) => m.id));
  return {
    added: materials.filter((m) => after.has(m.id) && !before.has(m.id)),
    removed: materials.filter((m) => before.has(m.id) && !after.has(m.id)),
  };
}

export function describeMaterials(materials: MaterialsOnSite): string {
  return `${materials.id} ${materials.description}, delivered ${formatSiteDate(
    materials.deliveredOn,
  )}`;
}
