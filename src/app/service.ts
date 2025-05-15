import { BadInputError, ConflictError } from '../errors.js';
import { buildBill } from '../domain/bill/bill.js';
import type { BillItem } from '../domain/bill/billItem.js';
import { firstRevision } from '../domain/bill/revision.js';
import type { Ncr } from '../domain/itp/ncr.js';
import type { Standdown } from '../domain/time/calendar.js';
import type { ResourceReturn } from '../domain/resource/return.js';
import type { SignOff } from '../domain/itp/signOff.js';
import type {
  PayLessNotice,
  PaymentApplication,
  PaymentNotice,
} from '../domain/subcontract/payment.js';
import type { SurveyRecord } from '../domain/survey/record.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { siteDateParts } from '../domain/time/siteDate.js';
import type { Config } from '../config/config.js';
import { importBillItems } from '../ingest/bill.js';
import { importHolidays, importStanddowns } from '../ingest/diary.js';
import { importNcrs, importSignOffs } from '../ingest/inspections.js';
import { importApplications, importNotices, importPayLessNotices } from '../ingest/payments.js';
import { importSurveyRecords } from '../ingest/records.js';
import { importResourceReturns } from '../ingest/resources.js';
import type { ImportReport } from '../ingest/report.js';
import { hasErrors } from '../ingest/report.js';
import type { ProgressContext } from '../progress/context.js';
import type { DefectsReport } from '../reporting/defectsReport.js';
import { buildDefectsReport } from '../reporting/defectsReport.js';
import type { DelayReport } from '../reporting/delayReport.js';
import { buildDelayReport } from '../reporting/delayReport.js';
import type { OutputReport } from '../reporting/outputReport.js';
import { buildOutputReport } from '../reporting/outputReport.js';
import type { ProgressReport } from '../reporting/progressReport.js';
import { buildProgressReport } from '../reporting/progressReport.js';
import type { SubcontractReport } from '../reporting/subcontractReport.js';
import { buildSubcontractReport } from '../reporting/subcontractReport.js';
import type { Baseline, CurvePoint } from '../reporting/sCurve.js';
import { buildSCurve, sCurveBaseline } from '../reporting/sCurve.js';
import type { StripMap } from '../reporting/stripMap.js';
import { buildStripMap } from '../reporting/stripMap.js';
import type { Job } from '../store/job.js';
import { jobCalendar, jobContext, jobSchedule } from '../store/job.js';
import { putAll } from '../store/repository.js';
import type { CashPoint } from '../valuation/cashFlow.js';
import { buildCashFlow } from '../valuation/cashFlow.js';
import type { Certificate } from '../valuation/certificate.js';
import { certifySeries } from '../valuation/certificate.js';
import type { PeriodSchedule, ValuationPeriod } from '../valuation/period.js';
import { periodByNumber } from '../valuation/period.js';

/**
 * What the HTTP layer and the CLI both call.
 *
 * Neither of them is allowed to reach into the store directly. Everything that
 * changes a job goes through here, so there is exactly one place that decides
 * whether an import with errors is applied and exactly one place that knows the
 * valuation series has to be run from period one.
 */
export interface Service {
  readonly job: Job;
  readonly config: Config;
}

export function createService(job: Job, config: Config): Service {
  return { job, config };
}

export function contextOf(service: Service): ProgressContext {
  return jobContext(service.job);
}

/** How many periods a schedule is built out to when nobody says. */
export const DEFAULT_PERIODS = 36;

/**
 * The valuation schedule, anchored on the month the first bill took effect.
 *
 * Anchored there rather than on today because the schedule has to be the same
 * every time it is asked for. A schedule that starts from the current month
 * renumbers every certificate the moment the calendar rolls over.
 */
export function scheduleOf(service: Service, count = DEFAULT_PERIODS): PeriodSchedule {
  const context = contextOf(service);
  const { year, month } = siteDateParts(firstRevision(context.billHistory).effectiveFrom);
  return jobSchedule(service.job, year, month, count);
}

export function periodOf(service: Service, number: number): ValuationPeriod {
  if (!Number.isInteger(number) || number < 1) {
    throw new BadInputError('a valuation number counts from one', { number });
  }
  return periodByNumber(scheduleOf(service, Math.max(number, DEFAULT_PERIODS)), number);
}

export function progressFor(service: Service, number: number): ProgressReport {
  const report = buildProgressReport(contextOf(service), periodOf(service, number));
  if (service.config.strictReconciliation && report.reconciliation.length > 0) {
    throw new ConflictError('the packages do not reconcile against the bill', {
      problems: report.reconciliation.length,
    });
  }
  return report;
}

/**
 * Certificates are cumulative, so period five cannot be worked out without
 * running one to four first. Doing that here rather than asking the caller for
 * a previous figure is what stops two callers disagreeing about what was
 * certified last month.
 */
/**
 * Every certificate from period one to the one asked for.
 *
 * Always run from the start, never from the period asked for. Each certificate
 * takes the previous one's net figure off itself, so a series that began in
 * the middle would pay the whole of the works again from that month.
 */
export function certificateSeries(service: Service, count: number): Certificate[] {
  const schedule = scheduleOf(service, Math.max(count, DEFAULT_PERIODS));
  const settings = service.job.settings;
  return certifySeries(schedule.periods.slice(0, count), {
    context: contextOf(service),
    contractSum: settings.contractSum,
    retention: settings.retention,
    materials: service.job.materials.all(),
    ...(settings.materialsAllowance === null
      ? {}
      : { materialsAllowance: settings.materialsAllowance }),
    dayworkSchedule: settings.daywork,
    dayworkSheets: service.job.dayworkSheets.all(),
    substantialCompletionOn: settings.substantialCompletionOn,
    defectsCertificateOn: settings.defectsCertificateOn,
  });
}

export function certificateFor(service: Service, number: number): Certificate {
  const certificate = certificateSeries(service, number)[number - 1];
  if (certificate === undefined) {
    throw new BadInputError('the schedule does not reach that valuation', { number });
  }
  return certificate;
}

/**
 * The cash forecast, run off the same certificates the job would issue.
 *
 * Built from the real series rather than from the baseline curve, because the
 * question this answers is what will actually arrive, and the baseline is by
 * definition what was hoped for before anything was measured.
 */
export function cashFlowFor(service: Service, count: number, latePeriods = 0): CashPoint[] {
  const series = certificateSeries(service, count);
  return buildCashFlow({
    periods: series.map((certificate) => certificate.period),
    certified: series.map((certificate) => certificate.netValuation),
    currency: service.job.settings.currency,
    latePeriods,
  });
}

/**
 * The extension of time position at a valuation cut-off.
 *
 * Null when the contract fixes no completion date, which is the honest answer:
 * a job with no completion date cannot be late.
 */
export function delayFor(service: Service, number: number): DelayReport | null {
  const settings = service.job.settings;
  return buildDelayReport(
    settings.completion,
    service.job.delayEvents.all(),
    jobCalendar(service.job),
    settings.contractSum,
    periodOf(service, number).cutOff,
  );
}

/**
 * The defects position at a valuation cut-off, and what is stopping the
 * defects certificate. Null when the contract has not fixed a defects period.
 */
export function defectsFor(service: Service, number: number): DefectsReport | null {
  const ends = service.job.settings.defectsPeriodEnds;
  if (ends === null) return null;
  return buildDefectsReport(service.job.defects.all(), ends, periodOf(service, number).cutOff);
}

/**
 * The subcontract payment position at a valuation cut-off.
 *
 * Run at the same cut-off as the certificate on purpose. The two sides of the
 * job move on different clocks otherwise, and a main contract valuation that
 * has been certified while three subcontract notice windows quietly closed is
 * how a job ends up paying twice for the same argument.
 */
export function paymentsFor(service: Service, number: number): SubcontractReport {
  const job = service.job;
  return buildSubcontractReport(
    {
      subcontracts: job.subcontracts.all(),
      applications: job.paymentApplications.all(),
      notices: job.paymentNotices.all(),
      payLess: job.payLessNotices.all(),
    },
    periodOf(service, number).cutOff,
  );
}

/**
 * What the job produced for the hours it booked, at a valuation cut-off.
 *
 * Never null. A job with no returns loaded has produced nothing for no hours,
 * which is a true and useful answer, and it is different from a contract that
 * fixes no such term at all.
 */
export function outputFor(service: Service, number: number): OutputReport {
  const job = service.job;
  return buildOutputReport(
    contextOf(service),
    job.packages.all(),
    job.resourceReturns.all(),
    job.settings.outputAllowances,
    periodOf(service, number).cutOff,
  );
}

export function curveFor(service: Service, count: number, baseline?: Baseline): CurvePoint[] {
  const schedule = scheduleOf(service, count);
  return buildSCurve(
    contextOf(service),
    { periods: schedule.periods.slice(0, count) },
    baseline ?? sCurveBaseline(service.job.settings.contractSum, count),
  );
}

export function stripFor(service: Service, packageId: string, number: number): StripMap {
  const pkg = service.job.packages.require(packageId);
  return buildStripMap(contextOf(service), pkg, periodOf(service, number).cutOff);
}

export interface LoadOptions {
  /** Apply the rows that parsed even when some did not. Off by default. */
  readonly partial?: boolean;
}

function applyIfClean<T>(
  report: ImportReport<T>,
  options: LoadOptions,
  apply: (items: readonly T[]) => void,
): ImportReport<T> {
  if (hasErrors(report) && options.partial !== true) return report;
  apply(report.imported);
  return report;
}

export function loadRecords(
  service: Service,
  text: string,
  options: LoadOptions = {},
): ImportReport<SurveyRecord> {
  return applyIfClean(importSurveyRecords(text), options, (items) => {
    putAll(service.job.records, items);
  });
}

export function loadSignOffs(
  service: Service,
  text: string,
  options: LoadOptions = {},
): ImportReport<SignOff> {
  return applyIfClean(importSignOffs(text), options, (items) => {
    putAll(service.job.signOffs, items);
  });
}

export function loadNcrs(
  service: Service,
  text: string,
  options: LoadOptions = {},
): ImportReport<Ncr> {
  return applyIfClean(importNcrs(text), options, (items) => {
    putAll(service.job.ncrs, items);
  });
}

export function loadDiary(
  service: Service,
  text: string,
  options: LoadOptions = {},
): ImportReport<Standdown> {
  return applyIfClean(importStanddowns(text), options, (items) => {
    service.job.standdowns = [...service.job.standdowns, ...items];
  });
}

export function loadHolidays(
  service: Service,
  text: string,
  options: LoadOptions = {},
): ImportReport<SiteDate> {
  return applyIfClean(importHolidays(text), options, (items) => {
    service.job.holidays = [...new Set([...service.job.holidays, ...items])];
  });
}

/**
 * The three payment registers load against the job currency rather than
 * carrying one of their own. A subcontract priced in a different currency from
 * the main contract is a real thing, but it is not a thing a csv column can
 * settle, and reading one here would let a typo change what is owed.
 */
export function loadApplications(
  service: Service,
  text: string,
  options: LoadOptions = {},
): ImportReport<PaymentApplication> {
  const currency = service.job.settings.currency;
  return applyIfClean(importApplications(text, { currency }), options, (items) => {
    putAll(service.job.paymentApplications, items);
  });
}

export function loadNotices(
  service: Service,
  text: string,
  options: LoadOptions = {},
): ImportReport<PaymentNotice> {
  const currency = service.job.settings.currency;
  return applyIfClean(importNotices(text, { currency }), options, (items) => {
    putAll(service.job.paymentNotices, items);
  });
}

export function loadPayLessNotices(
  service: Service,
  text: string,
  options: LoadOptions = {},
): ImportReport<PayLessNotice> {
  const currency = service.job.settings.currency;
  return applyIfClean(importPayLessNotices(text, { currency }), options, (items) => {
    putAll(service.job.payLessNotices, items);
  });
}

export function loadResourceReturns(
  service: Service,
  text: string,
  options: LoadOptions = {},
): ImportReport<ResourceReturn> {
  return applyIfClean(importResourceReturns(text), options, (items) => {
    putAll(service.job.resourceReturns, items);
  });
}

export interface LoadBillOptions extends LoadOptions {
  readonly reference: string;
  readonly effectiveFrom: SiteDate;
  readonly note?: string;
}

/**
 * A bill is loaded as a new revision, never over the top of the last one. That
 * is the whole reason the history exists, and an import that replaced a
 * revision in place would silently reprice every certificate before it.
 */
export function loadBill(
  service: Service,
  text: string,
  options: LoadBillOptions,
): ImportReport<BillItem> {
  const report = importBillItems(text, { currency: service.job.settings.currency });
  if (hasErrors(report) && options.partial !== true) return report;
  service.job.billRevisions.add({
    reference: options.reference,
    effectiveFrom: options.effectiveFrom,
    note: options.note ?? '',
    bill: buildBill({
      id: options.reference,
      title: `bill of quantities, ${options.reference}`,
      currency: service.job.settings.currency,
      items: report.imported,
    }),
  });
  return report;
}
