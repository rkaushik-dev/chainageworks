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
import type { DefectsReport } from '../reporting/defectsReport.js';
import type { DelayReport } from '../reporting/delayReport.js';
import type { OutputReport } from '../reporting/outputReport.js';
import { unallocatedHours } from '../reporting/outputReport.js';
import { barredLines, claimedDays, unassessedLines } from '../reporting/delayReport.js';
import type { SubcontractReport } from '../reporting/subcontractReport.js';
import { rowsAccepted, rowsRejected } from '../ingest/report.js';
import type { PackageLine, ProgressReport } from '../reporting/progressReport.js';
import type { CurvePoint } from '../reporting/sCurve.js';
import type { StripMap } from '../reporting/stripMap.js';
import { cellGlyph } from '../reporting/stripMap.js';
import type { CashPoint } from '../valuation/cashFlow.js';
import { peakExposure } from '../valuation/cashFlow.js';
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
    daywork: moneyDto(certificate.daywork),
    priceAdjustment: {
      factor: certificate.priceAdjustment.factor.factor,
      adjustable: moneyDto(certificate.priceAdjustment.adjustable),
      amount: moneyDto(certificate.priceAdjustment.amount),
      provisional: certificate.priceAdjustment.provisional,
      indices: certificate.priceAdjustment.factor.lines.map((line) => ({
        indexId: line.indexId,
        weight: percentDto(line.weight),
        movement: line.movement,
        provisional: line.provisional,
      })),
    },
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

/**
 * The delay position over the wire.
 *
 * The barred days ride alongside the awarded days rather than being folded
 * into one figure. A client that only sees the award cannot tell a job that
 * was never delayed from one that was delayed and served its notices late,
 * and those two need completely different conversations.
 */
export function delayReportDto(report: DelayReport): Record<string, unknown> {
  return {
    asAt: formatSiteDate(report.asAt),
    baseCompletion: formatSiteDate(report.position.baseCompletion),
    extendedCompletion: formatSiteDate(report.position.extendedCompletion),
    awardedDays: report.position.awardedDays,
    barredDays: report.position.barredDays,
    claimedDays: claimedDays(report),
    daysLate: report.daysLate,
    damages: moneyDto(report.damages),
    damagesCapped: report.damagesCapped,
    barred: barredLines(report).map((line) => line.id),
    unassessed: unassessedLines(report).map((line) => line.id),
    events: report.lines.map((line) => ({
      id: line.id,
      reference: line.reference,
      cause: line.cause,
      entitlement: line.entitlement,
      from: formatSiteDate(line.from),
      to: formatSiteDate(line.to),
      workingDays: line.workingDays,
      awardedDays: line.awardedDays,
      bar: line.bar,
      carriesMoney: line.carriesMoney,
    })),
  };
}

export function defectsReportDto(report: DefectsReport): Record<string, unknown> {
  return {
    asAt: formatSiteDate(report.asAt),
    periodEnds: formatSiteDate(report.periodEnds),
    block: report.block,
    summary: {
      outstanding: report.summary.outstanding,
      overdue: report.summary.overdue,
      disputed: report.summary.disputed,
      bySeverity: report.summary.bySeverity,
    },
    outstanding: report.outstanding.map((defect) => ({
      id: defect.id,
      reference: defect.reference,
      packageId: defect.packageId,
      extent: defect.extent === null ? null : formatChainageRange(defect.extent),
      description: defect.description,
      severity: defect.severity,
      attribution: defect.attribution,
      raisedOn: formatSiteDate(defect.raisedOn),
      dueBy: formatSiteDate(defect.dueBy),
    })),
    overdue: report.overdue.map((defect) => defect.id),
  };
}

export function subcontractReportDto(report: SubcontractReport): Record<string, unknown> {
  return {
    asAt: formatSiteDate(report.asAt),
    exposure: report.exposure,
    overOrder: report.overOrder.map((position) => position.subcontract.id),
    barredContra: report.barredContra,
    orders: report.positions.map((position) => ({
      id: position.subcontract.id,
      contractor: position.subcontract.contractor,
      orderValue: moneyDto(position.subcontract.orderValue),
      applications: position.applications,
      certified: moneyDto(position.certifiedToDate),
      paid: moneyDto(position.paidToDate),
      remaining: moneyDto(position.remaining),
      defaulted: [...position.defaulted],
      contra: {
        raised: moneyDto(position.contra.raised),
        deductible: moneyDto(position.contra.deductible),
        disputed: moneyDto(position.contra.disputed),
        barred: moneyDto(position.contra.barred),
        charges: position.contra.charges.map((charge) => ({
          id: charge.id,
          reference: charge.reference,
          reason: charge.reason,
          amount: moneyDto(charge.amount),
          disputed: charge.disputed,
        })),
      },
      netOfContra: moneyDto(position.netOfContra),
      carriedForward: moneyDto(position.contraCarriedForward),
    })),
    diary: report.diary.map((entry) => ({
      applicationId: entry.applicationId,
      subcontractId: entry.subcontractId,
      contractor: entry.contractor,
      appliedOn: formatSiteDate(entry.appliedOn),
      noticeBy: formatSiteDate(entry.noticeBy),
      amount: entry.amount,
      missed: entry.missed,
    })),
  };
}

export function outputReportDto(report: OutputReport): Record<string, unknown> {
  return {
    asAt: formatSiteDate(report.asAt),
    totals: report.totals,
    lostFraction: Number(report.lostFraction.toFixed(4)),
    unallocated: unallocatedHours(report),
    behind: report.behind.map((entry) => ({
      packageId: entry.packageId,
      stepId: entry.step.stepId,
      ratio: entry.step.ratio,
    })),
    barren: report.barren.map((entry) => ({
      packageId: entry.packageId,
      stepId: entry.step.stepId,
      labourHours: entry.step.labourHours,
    })),
    packages: report.packages.map((output) => ({
      packageId: output.packageId,
      labourHours: output.labourHours,
      plantHours: output.plantHours,
      unallocatedHours: output.unallocatedHours,
      steps: output.steps.map((step) => ({
        stepId: step.stepId,
        builtMetres: step.builtMetres,
        labourHours: step.labourHours,
        plantHours: step.plantHours,
        lostHours: step.lostHours,
        achieved: step.achieved,
        allowed: step.allowed,
        ratio: step.ratio,
      })),
    })),
  };
}

export function cashFlowDto(points: readonly CashPoint[]): Record<string, unknown> {
  const peak = peakExposure(points);
  return {
    peak:
      peak === null
        ? null
        : {
            period: peak.period,
            dueOn: formatSiteDate(peak.dueOn),
            outstanding: moneyDto(peak.outstanding),
          },
    points: points.map((point) => ({
      period: point.period,
      cutOff: formatSiteDate(point.cutOff),
      dueOn: formatSiteDate(point.dueOn),
      certified: moneyDto(point.certified),
      certificate: moneyDto(point.certificate),
      receipt: moneyDto(point.receipt),
      received: moneyDto(point.received),
      outstanding: moneyDto(point.outstanding),
    })),
  };
}
