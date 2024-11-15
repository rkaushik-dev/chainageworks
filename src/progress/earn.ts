import { NotFoundError } from '../errors.js';
import { findItem } from '../domain/bill/bill.js';
import type { BillItem } from '../domain/bill/billItem.js';
import { isEarnable } from '../domain/bill/billItem.js';
import type { ItemCode } from '../domain/bill/itemCode.js';
import { formatItemCode } from '../domain/bill/itemCode.js';
import { billAsAt } from '../domain/bill/revision.js';
import { earnedFraction } from '../domain/credit/profile.js';
import type { Money } from '../domain/money/money.js';
import { sumMoney, zeroMoney } from '../domain/money/money.js';
import type { Percent } from '../domain/money/percent.js';
import { applyPercentToQuantity } from '../domain/money/percent.js';
import type { Rate } from '../domain/money/rate.js';
import { extend } from '../domain/money/rate.js';
import type { WorkPackage } from '../domain/packages/workPackage.js';
import type { Quantity } from '../domain/quantity/quantity.js';
import {
  minQuantity,
  roundToBillPrecision,
  zeroQuantity,
} from '../domain/quantity/quantity.js';
import { isMeasurable } from '../domain/quantity/unit.js';
import { measuredQuantity } from '../domain/survey/record.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import type { ProgressContext } from './context.js';
import { profileForPackage } from './context.js';
import type { StepProgressDetail } from './stepProgress.js';
import { packageStepProgress, toStepProgress } from './stepProgress.js';

export interface AllocationEarning {
  readonly code: ItemCode;
  readonly allocated: Quantity;
  readonly measured: Quantity;
  readonly earnedQuantity: Quantity;
  readonly rate: Rate;
  readonly earnedValue: Money;
  /** True when the survey book, not the rule of credit, decided the figure. */
  readonly cappedByMeasurement: boolean;
  /** True when the item is provisional and earns nothing whatever was built. */
  readonly notEarnable: boolean;
}

export interface PackageEarning {
  readonly packageId: string;
  readonly fraction: Percent;
  readonly steps: readonly StepProgressDetail[];
  readonly allocations: readonly AllocationEarning[];
  readonly budget: Money;
  readonly earnedValue: Money;
}

/**
 * What a package has earned at a cut-off date.
 *
 * Five separate rules land on the same number and they are applied in this
 * order, which is the order the contract applies them in:
 *
 * 1. the survey book says what was built,
 * 2. the hold points and the open reports say how much of that may count,
 * 3. the rule of credit turns the step fractions into one earned fraction,
 * 4. the bill revision in force at the cut-off prices it,
 * 5. the measurement caps it, because nothing may be earned that was not built.
 *
 * Step five is the one that keeps being left out of a spreadsheet version, and
 * it is the one that stops a generous rule of credit paying for work that only
 * exists on the programme.
 */
export function earnPackage(
  context: ProgressContext,
  pkg: WorkPackage,
  asAt: SiteDate,
): PackageEarning {
  const bill = billAsAt(context.billHistory, asAt);
  const profile = profileForPackage(context, pkg);
  const steps = packageStepProgress(context, pkg, asAt);
  const fraction = earnedFraction(profile, toStepProgress(steps));

  const allocations = pkg.allocations.map((allocation): AllocationEarning => {
    const item = findItem(bill, allocation.code);
    if (item === null) {
      throw new NotFoundError('bill item', formatItemCode(allocation.code));
    }
    return earnAllocation(context, pkg, item, allocation.quantity, fraction, asAt);
  });

  return {
    packageId: pkg.id,
    fraction,
    steps,
    allocations,
    budget: sumMoney(
      allocations.map((a) => extend(a.rate, a.allocated)),
      bill.currency,
    ),
    earnedValue: sumMoney(
      allocations.map((a) => a.earnedValue),
      bill.currency,
    ),
  };
}

function earnAllocation(
  context: ProgressContext,
  pkg: WorkPackage,
  item: BillItem,
  allocated: Quantity,
  fraction: Percent,
  asAt: SiteDate,
): AllocationEarning {
  const measured =
    measuredQuantity(context.records, asAt, { packageId: pkg.id, code: item.code }) ??
    zeroQuantity(allocated.unit);

  if (!isEarnable(item)) {
    return {
      code: item.code,
      allocated,
      measured,
      earnedQuantity: zeroQuantity(allocated.unit),
      rate: item.rate,
      earnedValue: zeroMoney(item.rate.currency),
      cappedByMeasurement: false,
      notEarnable: true,
    };
  }

  // A lump sum has nothing to measure, so the rule of credit is the only thing
  // that can decide it: it is neither rounded to the bill precision, which would
  // round a part earned sum to nothing, nor capped by a survey book that will
  // never carry a record for it.
  const scaled = applyPercentToQuantity(allocated, fraction);
  const measurable = isMeasurable(allocated.unit);
  const target = measurable ? roundToBillPrecision(scaled) : scaled;
  const capped = measurable ? minQuantity(target, measured) : target;

  return {
    code: item.code,
    allocated,
    measured,
    earnedQuantity: capped,
    rate: item.rate,
    earnedValue: extend(item.rate, capped),
    cappedByMeasurement: capped.milli < target.milli,
    notEarnable: false,
  };
}

export function earnAll(context: ProgressContext, asAt: SiteDate): PackageEarning[] {
  return context.register.packages.map((pkg) => earnPackage(context, pkg, asAt));
}

export function totalEarned(
  earnings: readonly PackageEarning[],
  context: ProgressContext,
  asAt: SiteDate,
): Money {
  const currency = billAsAt(context.billHistory, asAt).currency;
  return sumMoney(
    earnings.map((e) => e.earnedValue),
    currency,
  );
}

export function findEarning(
  earnings: readonly PackageEarning[],
  packageId: string,
): PackageEarning | null {
  return earnings.find((e) => e.packageId === packageId) ?? null;
}

export function cappedAllocations(earning: PackageEarning): AllocationEarning[] {
  return earning.allocations.filter((a) => a.cappedByMeasurement);
}
