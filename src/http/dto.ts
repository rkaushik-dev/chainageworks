import { formatItemCode } from '../domain/bill/itemCode.js';
import { formatChainageRange } from '../domain/chainage/range.js';
import type { Money } from '../domain/money/money.js';
import { formatMoney, moneyMajor } from '../domain/money/money.js';
import type { Percent } from '../domain/money/percent.js';
import { percentValue } from '../domain/money/percent.js';
import type { Quantity } from '../domain/quantity/quantity.js';
import { formatQuantity, quantityAmount } from '../domain/quantity/quantity.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import type { ImportIssue, ImportReport } from '../ingest/report.js';
import { rowsAccepted, rowsRejected } from '../ingest/report.js';
import type { PackageLine, ProgressReport } from '../reporting/progressReport.js';
import type { CurvePoint } from '../reporting/sCurve.js';
import type { StripMap } from '../reporting/stripMap.js';
import { cellGlyph } from '../reporting/stripMap.js';
import type { Certificate } from '../valuation/certificate.js';
import type { ValuationPeriod } from '../valuation/period.js';

/**
 * How money goes over the wire.
 *
 * Three fields rather than one, because the three readers want different
 * things. A spreadsheet wants the number, a person wants the formatted string,
 * and anything doing arithmetic wants the exact minor units. Sending only the
 * formatted string is how a client ends up parsing currency symbols.
 */
export interface MoneyDto {
  readonly minor: number;
  readonly amount: number;
  readonly formatted: string;
}

export function moneyDto(value: Money): MoneyDto {
  return { minor: value.minor, amount: moneyMajor(value), formatted: formatMoney(value) };
}

export interface QuantityDto {
  readonly milli: number;
  readonly amount: number;
  readonly unit: string;
  readonly formatted: string;
}

export function quantityDto(value: Quantity): QuantityDto {
  return {
    milli: value.milli,
    amount: quantityAmount(value),
    unit: value.unit,
    formatted: formatQuantity(value),
  };
}

export function percentDto(value: Percent): number {
  return percentValue(value);
}

export function periodDto(period: ValuationPeriod): Record<string, unknown> {
  return {
    number: period.number,
    from: formatSiteDate(period.range.from),
    to: formatSiteDate(period.range.to),
    cutOff: formatSiteDate(period.cutOff),
    issuedOn: formatSiteDate(period.issuedOn),
    dueOn: formatSiteDate(period.dueOn),
  };
}

export function packageLineDto(line: PackageLine): Record<string, unknown> {
  return {
    packageId: line.packageId,
    name: line.name,
    section: line.section,
    extent: formatChainageRange(line.extent),
    budget: moneyDto(line.budget),
    earned: moneyDto(line.earned),
    percent: percentDto(line.fraction),
    heldValue: moneyDto(line.heldValue),
    steps: line.steps.map((step) => ({
      stepId: step.stepId,
      builtPercent: Number((step.builtFraction * 100).toFixed(2)),
      creditedPercent: Number((step.creditedFraction * 100).toFixed(2)),
      gate: step.gate,
      heldPoints: [...step.heldPoints],
      heldBack: step.heldBack.map(formatChainageRange),
    })),
  };
}

export function progressReportDto(report: ProgressReport): Record<string, unknown> {
  return {
    period: periodDto(report.period),
    billRevision: report.billRevision,
    budget: moneyDto(report.budget),
    earned: moneyDto(report.earned),
    heldValue: moneyDto(report.heldValue),
    workingDays: report.workingDays,
    productiveDays: report.productiveDays,
    standdowns: report.standdowns.map((s) => ({
      date: formatSiteDate(s.date),
      reason: s.reason,
      note: s.note ?? '',
    })),
    packages: report.lines.map(packageLineDto),
    reconciliation: report.reconciliation.map((line) => ({
      code: formatItemCode(line.code),
      problem: line.problem,
      billed: line.billed === null ? null : quantityDto(line.billed),
      allocated: line.allocated === null ? null : quantityDto(line.allocated),
      message: line.message,
    })),
    overMeasures: report.overMeasures.map((over) => ({
      packageId: over.packageId,
      code: formatItemCode(over.code),
      measured: quantityDto(over.measured),
      allowed: quantityDto(over.allowed),
      excess: quantityDto(over.excess),
      message: over.message,
    })),
  };
}

export function certificateDto(certificate: Certificate): Record<string, unknown> {
  return {
    period: periodDto(certificate.period),
    billRevision: certificate.billRevision,
    measuredWork: moneyDto(certificate.measuredWork),
    materialsOnSite: moneyDto(certificate.materialsOnSite),
    gross: moneyDto(certificate.gross),
    retention: {
      accrued: moneyDto(certificate.retention.accrued),
      held: moneyDto(certificate.retention.held),
      releasedOnCompletion: moneyDto(certificate.retention.releasedOnCompletion),
      releasedOnDefects: moneyDto(certificate.retention.releasedOnDefects),
      capReached: certificate.retention.capReached,
    },
    netValuation: moneyDto(certificate.netValuation),
    previouslyCertified: moneyDto(certificate.previouslyCertified),
    amountDue: moneyDto(certificate.amountDue),
    packages: certificate.earnings.map((earning) => ({
      packageId: earning.packageId,
      percent: percentDto(earning.fraction),
      budget: moneyDto(earning.budget),
      earned: moneyDto(earning.earnedValue),
      allocations: earning.allocations.map((a) => ({
        code: formatItemCode(a.code),
        allocated: quantityDto(a.allocated),
        measured: quantityDto(a.measured),
        earned: quantityDto(a.earnedQuantity),
        value: moneyDto(a.earnedValue),
        cappedByMeasurement: a.cappedByMeasurement,
        notEarnable: a.notEarnable,
      })),
    })),
  };
}

export function curveDto(points: readonly CurvePoint[]): Record<string, unknown>[] {
  return points.map((point) => ({
    period: point.period,
    cutOff: formatSiteDate(point.cutOff),
    planned: moneyDto(point.planned),
    earned: moneyDto(point.earned),
    variance: moneyDto(point.variance),
    plannedPercent: percentDto(point.plannedShare),
    earnedPercent: percentDto(point.earnedShare),
  }));
}

export function stripDto(map: StripMap): Record<string, unknown> {
  return {
    packageId: map.packageId,
    extent: formatChainageRange(map.extent),
    cellLength: map.cellLength,
    rows: map.rows.map((row) => ({
      stepId: row.stepId,
      glyphs: row.cells.map((c) => cellGlyph(c.state)).join(''),
      cells: row.cells.map((c) => ({
        extent: formatChainageRange(c.extent),
        state: c.state,
      })),
    })),
  };
}

function issueDto(issue: ImportIssue): Record<string, unknown> {
  return {
    line: issue.line,
    column: issue.column,
    severity: issue.severity,
    message: issue.message,
  };
}

export function importReportDto<T>(report: ImportReport<T>): Record<string, unknown> {
  return {
    rowsRead: report.rowsRead,
    accepted: rowsAccepted(report),
    rejected: rowsRejected(report),
    issues: report.issues.map(issueDto),
  };
}
