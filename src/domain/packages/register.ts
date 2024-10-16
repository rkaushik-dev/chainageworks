import { BadInputError, NotFoundError } from '../../errors.js';
import type { Bill } from '../bill/bill.js';
import { findItem } from '../bill/bill.js';
import { isEarnable } from '../bill/billItem.js';
import type { ItemCode } from '../bill/itemCode.js';
import { compareItemCodes, formatItemCode, itemCodeKey } from '../bill/itemCode.js';
import type { ChainageRange } from '../chainage/range.js';
import { coveredLength, rangeContains, subtractRange, unionRanges } from '../chainage/range.js';
import type { Station } from '../chainage/station.js';
import type { Quantity } from '../quantity/quantity.js';
import { compareQuantities, formatQuantity, sumQuantities } from '../quantity/quantity.js';
import type { WorkPackage } from './workPackage.js';
import { packagesClash } from './workPackage.js';

export interface PackageRegister {
  readonly packages: readonly WorkPackage[];
}

export function buildRegister(packages: readonly WorkPackage[]): PackageRegister {
  const seen = new Set<string>();
  for (const pkg of packages) {
    if (seen.has(pkg.id)) {
      throw new BadInputError('two work packages share an id', { package: pkg.id });
    }
    seen.add(pkg.id);
  }
  const sorted = [...packages].sort(
    (a, b) => a.extent.from - b.extent.from || (a.id < b.id ? -1 : 1),
  );
  return { packages: sorted };
}

export function findPackage(register: PackageRegister, id: string): WorkPackage | null {
  return register.packages.find((p) => p.id === id) ?? null;
}

export function requirePackage(register: PackageRegister, id: string): WorkPackage {
  const pkg = findPackage(register, id);
  if (pkg === null) {
    throw new NotFoundError('work package', id);
  }
  return pkg;
}

export function packagesInSection(register: PackageRegister, section: string): WorkPackage[] {
  return register.packages.filter((p) => p.section === section);
}

export function sections(register: PackageRegister): string[] {
  return [...new Set(register.packages.map((p) => p.section))].sort();
}

export function packagesAt(register: PackageRegister, station: Station): WorkPackage[] {
  return register.packages.filter((p) => rangeContains(p.extent, station));
}

export function packagesFor(register: PackageRegister, code: ItemCode): WorkPackage[] {
  const key = itemCodeKey(code);
  return register.packages.filter((p) =>
    p.allocations.some((a) => itemCodeKey(a.code) === key),
  );
}

/** Everything the packages together allocate against one bill item. */
export function allocatedQuantity(register: PackageRegister, code: ItemCode): Quantity | null {
  const parts = register.packages
    .flatMap((p) => p.allocations)
    .filter((a) => itemCodeKey(a.code) === itemCodeKey(code));
  const first = parts[0];
  if (first === undefined) return null;
  return sumQuantities(
    parts.map((a) => a.quantity),
    first.quantity.unit,
  );
}

export type ReconciliationProblem =
  | 'unknown_item'
  | 'over_allocated'
  | 'under_allocated'
  | 'not_allocated';

export interface ReconciliationLine {
  readonly code: ItemCode;
  readonly problem: ReconciliationProblem;
  readonly billed: Quantity | null;
  readonly allocated: Quantity | null;
  readonly message: string;
}

/**
 * Check the package register against the bill.
 *
 * Three things go wrong in practice and all three are silent until a valuation
 * comes out short. Packages can allocate more than the bill carries, they can
 * allocate less and leave work with nowhere to book it, and they can allocate
 * against an item that no longer exists after a revision.
 */
export function reconcileWithBill(register: PackageRegister, bill: Bill): ReconciliationLine[] {
  const lines: ReconciliationLine[] = [];
  const allocatedKeys = new Set<string>();

  for (const pkg of register.packages) {
    for (const allocation of pkg.allocations) {
      allocatedKeys.add(itemCodeKey(allocation.code));
    }
  }

  for (const key of allocatedKeys) {
    const pkgWithCode = register.packages
      .flatMap((p) => p.allocations)
      .find((a) => itemCodeKey(a.code) === key);
    if (pkgWithCode === undefined) continue;
    const code = pkgWithCode.code;
    const item = findItem(bill, code);
    const allocated = allocatedQuantity(register, code);
    if (item === null) {
      lines.push({
        code,
        problem: 'unknown_item',
        billed: null,
        allocated,
        message: `${formatItemCode(code)} is allocated but the bill has no such item`,
      });
      continue;
    }
    if (allocated === null) continue;
    const difference = compareQuantities(allocated, item.quantity);
    if (difference > 0) {
      lines.push({
        code,
        problem: 'over_allocated',
        billed: item.quantity,
        allocated,
        message:
          `${formatItemCode(code)} is allocated ${formatQuantity(allocated)} ` +
          `against a billed ${formatQuantity(item.quantity)}`,
      });
    } else if (difference < 0) {
      lines.push({
        code,
        problem: 'under_allocated',
        billed: item.quantity,
        allocated,
        message:
          `${formatItemCode(code)} is allocated ${formatQuantity(allocated)} ` +
          `of a billed ${formatQuantity(item.quantity)}`,
      });
    }
  }

  for (const item of bill.items) {
    if (!isEarnable(item)) continue;
    if (allocatedKeys.has(itemCodeKey(item.code))) continue;
    lines.push({
      code: item.code,
      problem: 'not_allocated',
      billed: item.quantity,
      allocated: null,
      message: `${formatItemCode(item.code)} is in the bill but no package earns against it`,
    });
  }

  return lines.sort((a, b) => compareItemCodes(a.code, b.code));
}

/** Stretches of the alignment that no package covers, which is usually a gap. */
export function uncoveredStretches(
  register: PackageRegister,
  extent: ChainageRange,
  alignmentId: string,
): ChainageRange[] {
  const covered = unionRanges(
    register.packages.filter((p) => p.alignmentId === alignmentId).map((p) => p.extent),
  );
  let remaining: ChainageRange[] = [extent];
  for (const range of covered) {
    remaining = remaining.flatMap((r) => subtractRange(r, range));
  }
  return remaining;
}

export function coveredExtent(register: PackageRegister, alignmentId: string): number {
  return coveredLength(
    register.packages.filter((p) => p.alignmentId === alignmentId).map((p) => p.extent),
  );
}

/** Pairs of packages that overlap and share a bill item. */
export function clashes(register: PackageRegister): [WorkPackage, WorkPackage][] {
  const out: [WorkPackage, WorkPackage][] = [];
  for (let i = 0; i < register.packages.length; i += 1) {
    for (let j = i + 1; j < register.packages.length; j += 1) {
      const left = register.packages[i];
      const right = register.packages[j];
      if (left === undefined || right === undefined) continue;
      if (packagesClash(left, right)) out.push([left, right]);
    }
  }
  return out;
}
