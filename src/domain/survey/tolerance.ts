import { BadInputError } from '../../errors.js';
import type { ItemCode } from '../bill/itemCode.js';
import { formatItemCode } from '../bill/itemCode.js';
import type { Percent } from '../money/percent.js';
import { applyPercentToQuantity, formatPercent, percent } from '../money/percent.js';
import type { PackageRegister } from '../packages/register.js';
import type { WorkPackage } from '../packages/workPackage.js';
import { findAllocation } from '../packages/workPackage.js';
import type { Quantity } from '../quantity/quantity.js';
import {
  addQuantities,
  formatQuantity,
  subtractQuantities,
  zeroQuantity,
} from '../quantity/quantity.js';
import type { SiteDate } from '../time/siteDate.js';
import type { SurveyRecord } from './record.js';
import { measuredQuantity } from './record.js';

/**
 * How much more than the allocation a class of work may measure before it is
 * reported.
 *
 * Earthworks routinely overruns because the ground is not what the borehole
 * said, and a count of manhole covers does not. One allowance for the whole job
 * therefore either buries the earthworks or floods the report with drainage
 * noise, so the allowance is set per work class.
 */
export interface ToleranceRule {
  /** The work class letter, or null for the rule everything else falls back to. */
  readonly workClass: string | null;
  readonly allowance: Percent;
}

export interface ToleranceSet {
  readonly rules: readonly ToleranceRule[];
}

export const DEFAULT_TOLERANCES: ToleranceSet = {
  rules: [
    { workClass: null, allowance: percent(2.5) },
    { workClass: 'E', allowance: percent(10) },
    { workClass: 'D', allowance: percent(15) },
    { workClass: 'K', allowance: percent(0) },
    { workClass: 'I', allowance: percent(5) },
  ],
};

export function buildToleranceSet(rules: readonly ToleranceRule[]): ToleranceSet {
  const seen = new Set<string>();
  for (const rule of rules) {
    const key = rule.workClass ?? '*';
    if (seen.has(key)) {
      throw new BadInputError('two tolerance rules cover the same work class', {
        workClass: key,
      });
    }
    seen.add(key);
    if (rule.allowance.basisPoints < 0) {
      throw new BadInputError('a tolerance allowance cannot be negative', {
        workClass: key,
      });
    }
  }
  if (!seen.has('*')) {
    throw new BadInputError('a tolerance set needs a fallback rule');
  }
  return { rules: [...rules] };
}

export function allowanceFor(set: ToleranceSet, code: ItemCode): Percent {
  const specific = set.rules.find((r) => r.workClass === code.workClass);
  if (specific !== undefined) return specific.allowance;
  const fallback = set.rules.find((r) => r.workClass === null);
  if (fallback === undefined) {
    throw new BadInputError('a tolerance set needs a fallback rule');
  }
  return fallback.allowance;
}

export interface OverMeasure {
  readonly packageId: string;
  readonly code: ItemCode;
  readonly allocated: Quantity;
  readonly measured: Quantity;
  readonly allowed: Quantity;
  readonly excess: Quantity;
  readonly message: string;
}

/** What the package may measure before anybody has to explain themselves. */
export function allowedQuantity(
  pkg: WorkPackage,
  code: ItemCode,
  tolerances: ToleranceSet,
): Quantity | null {
  const allocation = findAllocation(pkg, code);
  if (allocation === null) return null;
  const allowance = allowanceFor(tolerances, code);
  return addQuantities(
    allocation.quantity,
    applyPercentToQuantity(allocation.quantity, allowance),
  );
}

/**
 * Measurements that have run past what the package was allocated, plus the
 * allowance for that class of work.
 *
 * This is not a hard stop. Overrun is normal on a measured contract and the
 * point of the report is that somebody prices it before the certificate goes
 * out, not that the survey is refused.
 */
export function findOverMeasures(
  records: readonly SurveyRecord[],
  register: PackageRegister,
  asAt: SiteDate,
  tolerances: ToleranceSet = DEFAULT_TOLERANCES,
): OverMeasure[] {
  const out: OverMeasure[] = [];
  for (const pkg of register.packages) {
    for (const allocation of pkg.allocations) {
      const measured =
        measuredQuantity(records, asAt, { packageId: pkg.id, code: allocation.code }) ??
        zeroQuantity(allocation.quantity.unit);
      const allowed = allowedQuantity(pkg, allocation.code, tolerances);
      if (allowed === null) continue;
      if (measured.milli <= allowed.milli) continue;
      const excess = subtractQuantities(measured, allowed);
      out.push({
        packageId: pkg.id,
        code: allocation.code,
        allocated: allocation.quantity,
        measured,
        allowed,
        excess,
        message:
          `${pkg.id} ${formatItemCode(allocation.code)} measured ` +
          `${formatQuantity(measured)} against an allowance of ${formatQuantity(allowed)} ` +
          `(${formatQuantity(allocation.quantity)} plus ` +
          `${formatPercent(allowanceFor(tolerances, allocation.code))})`,
      });
    }
  }
  return out;
}

/** Records booked outside the stretch their package covers. */
export function findRecordsOutsideExtent(
  records: readonly SurveyRecord[],
  register: PackageRegister,
): SurveyRecord[] {
  return records.filter((record) => {
    const pkg = register.packages.find((p) => p.id === record.packageId);
    if (pkg === undefined) return false;
    return record.extent.from < pkg.extent.from || record.extent.to > pkg.extent.to;
  });
}

/** Records whose package is not in the register at all. */
export function findOrphanRecords(
  records: readonly SurveyRecord[],
  register: PackageRegister,
): SurveyRecord[] {
  const ids = new Set(register.packages.map((p) => p.id));
  return records.filter((r) => !ids.has(r.packageId));
}
