import { billAsAt, revisionAsAt } from '../domain/bill/revision.js';
import type { ChainageRange } from '../domain/chainage/range.js';
import { earnedFraction } from '../domain/credit/profile.js';
import type { Money } from '../domain/money/money.js';
import { subtractMoney, sumMoney } from '../domain/money/money.js';
import type { Percent } from '../domain/money/percent.js';
import { applyPercent } from '../domain/money/percent.js';
import type { ReconciliationLine } from '../domain/packages/register.js';
import { reconcileWithBill } from '../domain/packages/register.js';
import type { WorkPackage } from '../domain/packages/workPackage.js';
import type { OverMeasure } from '../domain/survey/tolerance.js';
import { findOverMeasures } from '../domain/survey/tolerance.js';
import { productiveDaysIn, standdownsIn, workingDaysIn } from '../domain/time/calendar.js';
import type { Standdown } from '../domain/time/calendar.js';
import type { ProgressContext } from '../progress/context.js';
import { profileForPackage } from '../progress/context.js';
import type { PackageEarning } from '../progress/earn.js';
import { earnPackage } from '../progress/earn.js';
import type { StepProgressDetail } from '../progress/stepProgress.js';
import { heldBackExtent, toBuiltProgress } from '../progress/stepProgress.js';
import type { ValuationPeriod } from '../valuation/period.js';

export interface StepLine {
  readonly stepId: string;
  readonly builtFraction: number;
  readonly creditedFraction: number;
  readonly gate: string;
  readonly heldPoints: readonly string[];
  readonly heldBack: readonly ChainageRange[];
}

export interface PackageLine {
  readonly packageId: string;
  readonly name: string;
  readonly section: string;
  readonly extent: ChainageRange;
  readonly budget: Money;
  readonly earned: Money;
  readonly fraction: Percent;
  /** What the same work would have earned with every gate open. */
  readonly earnedIfReleased: Money;
  /** The difference: value sitting behind a hold point or an open report. */
  readonly heldValue: Money;
  readonly steps: readonly StepLine[];
}

export interface ProgressReport {
  readonly period: ValuationPeriod;
  readonly billRevision: string;
  readonly lines: readonly PackageLine[];
  readonly budget: Money;
  readonly earned: Money;
  readonly heldValue: Money;
  readonly workingDays: number;
  readonly productiveDays: number;
  readonly standdowns: readonly Standdown[];
  readonly reconciliation: readonly ReconciliationLine[];
  readonly overMeasures: readonly OverMeasure[];
}

/**
 * The monthly progress report.
 *
 * Written around one number that a valuation on its own never shows: the value
 * of work that is in the ground and cannot be paid for. Somebody has to chase
 * those sign-offs, and until the report puts a figure on them nobody does.
 */
export function buildProgressReport(
  context: ProgressContext,
  period: ValuationPeriod,
): ProgressReport {
  const asAt = period.cutOff;
  const bill = billAsAt(context.billHistory, asAt);
  const lines = context.register.packages.map((pkg) =>
    toLine(context, pkg, earnPackage(context, pkg, asAt)),
  );

  return {
    period,
    billRevision: revisionAsAt(context.billHistory, asAt).reference,
    lines,
    budget: sumMoney(
      lines.map((l) => l.budget),
      bill.currency,
    ),
    earned: sumMoney(
      lines.map((l) => l.earned),
      bill.currency,
    ),
    heldValue: sumMoney(
      lines.map((l) => l.heldValue),
      bill.currency,
    ),
    workingDays: workingDaysIn(context.calendar, period.range),
    productiveDays: productiveDaysIn(context.calendar, period.range),
    standdowns: standdownsIn(context.calendar, period.range),
    reconciliation: reconcileWithBill(context.register, bill),
    overMeasures: findOverMeasures(context.records, context.register, asAt, context.tolerances),
  };
}

function toLine(
  context: ProgressContext,
  pkg: WorkPackage,
  earning: PackageEarning,
): PackageLine {
  const profile = profileForPackage(context, pkg);
  const released = earnedFraction(profile, toBuiltProgress(earning.steps));
  const earnedIfReleased = applyPercent(earning.budget, released);
  return {
    packageId: earning.packageId,
    name: pkg.name,
    section: pkg.section,
    extent: pkg.extent,
    budget: earning.budget,
    earned: earning.earnedValue,
    fraction: earning.fraction,
    earnedIfReleased,
    heldValue: subtractMoney(earnedIfReleased, earning.earnedValue),
    steps: earning.steps.map(toStepLine),
  };
}

function toStepLine(step: StepProgressDetail): StepLine {
  return {
    stepId: step.stepId,
    builtFraction: step.builtFraction,
    creditedFraction: step.fraction,
    gate: step.gate,
    heldPoints: step.heldPoints,
    heldBack: heldBackExtent(step),
  };
}

/** Packages with value stuck behind a gate, worst first. */
export function heldLines(report: ProgressReport): PackageLine[] {
  return report.lines
    .filter((l) => l.heldValue.minor > 0)
    .sort((a, b) => b.heldValue.minor - a.heldValue.minor);
}

/** Every hold point named anywhere in the report, deduplicated. */
export function outstandingHoldPoints(report: ProgressReport): string[] {
  const out = new Set<string>();
  for (const line of report.lines) {
    for (const step of line.steps) {
      for (const point of step.heldPoints) out.add(point);
    }
  }
  return [...out].sort();
}

export function hasProblems(report: ProgressReport): boolean {
  return report.reconciliation.length > 0 || report.overMeasures.length > 0;
}
