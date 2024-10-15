import { BadInputError, NotFoundError } from '../../errors.js';
import type { Bill } from '../bill/bill.js';
import { findItem } from '../bill/bill.js';
import type { ItemCode } from '../bill/itemCode.js';
import { formatItemCode, itemCodeKey, itemCodesEqual } from '../bill/itemCode.js';
import type { ChainageRange } from '../chainage/range.js';
import {
  formatChainageRange,
  rangeContains,
  rangeLength,
  rangesOverlap,
} from '../chainage/range.js';
import type { Station } from '../chainage/station.js';
import type { Money } from '../money/money.js';
import { sumMoney } from '../money/money.js';
import { extend } from '../money/rate.js';
import type { Quantity } from '../quantity/quantity.js';
import { assertSameUnit } from '../quantity/unit.js';

/**
 * How much of a bill item a package is responsible for.
 *
 * The bill is written for the whole job and the works are built in stretches,
 * so the two have to be reconciled somewhere. This is that somewhere: a package
 * takes a share of a bill item, and the shares across every package are what
 * the bill quantity is checked against.
 */
export interface PackageAllocation {
  readonly code: ItemCode;
  readonly quantity: Quantity;
}

export interface WorkPackage {
  readonly id: string;
  readonly name: string;
  readonly alignmentId: string;
  readonly extent: ChainageRange;
  /** Free text grouping, usually the contract section. */
  readonly section: string;
  /** Which rule of credit decides how it earns. */
  readonly profileId: string;
  readonly allocations: readonly PackageAllocation[];
}

export interface WorkPackageInput {
  readonly id: string;
  readonly name: string;
  readonly alignmentId: string;
  readonly extent: ChainageRange;
  readonly section?: string;
  readonly profileId: string;
  readonly allocations: readonly PackageAllocation[];
}

export function buildWorkPackage(input: WorkPackageInput): WorkPackage {
  if (input.id.trim() === '') {
    throw new BadInputError('a work package needs an id');
  }
  if (input.allocations.length === 0) {
    throw new BadInputError('a work package with no allocation can never earn', {
      package: input.id,
    });
  }
  const seen = new Set<string>();
  for (const allocation of input.allocations) {
    const key = itemCodeKey(allocation.code);
    if (seen.has(key)) {
      throw new BadInputError('the same bill item is allocated twice to one package', {
        package: input.id,
        code: formatItemCode(allocation.code),
      });
    }
    seen.add(key);
    if (allocation.quantity.milli < 0) {
      throw new BadInputError('an allocated quantity cannot be negative', {
        package: input.id,
        code: formatItemCode(allocation.code),
      });
    }
  }
  return {
    id: input.id,
    name: input.name,
    alignmentId: input.alignmentId,
    extent: input.extent,
    section: input.section ?? '',
    profileId: input.profileId,
    allocations: [...input.allocations],
  };
}

export function findAllocation(pkg: WorkPackage, code: ItemCode): PackageAllocation | null {
  return pkg.allocations.find((a) => itemCodesEqual(a.code, code)) ?? null;
}

export function requireAllocation(pkg: WorkPackage, code: ItemCode): PackageAllocation {
  const allocation = findAllocation(pkg, code);
  if (allocation === null) {
    throw new NotFoundError('package allocation', `${pkg.id}/${formatItemCode(code)}`);
  }
  return allocation;
}

export function allocatedCodes(pkg: WorkPackage): ItemCode[] {
  return pkg.allocations.map((a) => a.code);
}

export function packageLength(pkg: WorkPackage): number {
  return rangeLength(pkg.extent);
}

export function packageCovers(pkg: WorkPackage, station: Station): boolean {
  return rangeContains(pkg.extent, station);
}

/**
 * Two packages clash when they are on the same alignment and share length. It
 * is not always wrong, since drainage runs under a pavement, but it is always
 * worth reporting, because it is also how the same work gets claimed twice.
 */
export function packagesClash(a: WorkPackage, b: WorkPackage): boolean {
  if (a.id === b.id) return false;
  if (a.alignmentId !== b.alignmentId) return false;
  if (!rangesOverlap(a.extent, b.extent)) return false;
  return a.allocations.some((left) =>
    b.allocations.some((right) => itemCodesEqual(left.code, right.code)),
  );
}

/** What the package is worth at the bill rates, before anything is built. */
export function packageBudget(pkg: WorkPackage, bill: Bill): Money {
  const values = pkg.allocations.map((allocation) => {
    const item = findItem(bill, allocation.code);
    if (item === null) {
      throw new NotFoundError('bill item', formatItemCode(allocation.code));
    }
    assertSameUnit(item.rate.unit, allocation.quantity.unit);
    return extend(item.rate, allocation.quantity);
  });
  return sumMoney(values, bill.currency);
}

export function describeWorkPackage(pkg: WorkPackage): string {
  return `${pkg.id} ${pkg.name} [${formatChainageRange(pkg.extent)}]`;
}
