/**
 * The public surface of the library.
 *
 * Deliberately a curated list rather than a barrel over every module. Anything
 * exported here is something callers may depend on, and everything not here is
 * free to change: the split between the two is the only thing that makes a
 * version number mean anything.
 *
 * Grouped the way the domain is layered, and in the order work actually flows.
 * A caller reading this file top to bottom meets the units first, then the
 * bill, then what was built, then what it is worth.
 */

export type { ErrorCode, ErrorDetails } from './errors.js';
export {
  asCwError,
  BadInputError,
  ConflictError,
  CwError,
  isCwError,
  NotFoundError,
  UnsupportedError,
} from './errors.js';
export type { Result } from './result.js';
export { collectResults, err, ok, partitionResults } from './result.js';

// Units and scalars. Everything downstream is built out of these four.
export type { CurrencyCode, Money } from './domain/money/money.js';
export {
  formatMoney,
  money,
  moneyFromMajor,
  moneyMajor,
  parseMoney,
} from './domain/money/money.js';
export type { Percent } from './domain/money/percent.js';
export { formatPercent, percent, percentValue } from './domain/money/percent.js';
export type { Rate } from './domain/money/rate.js';
export { extend, formatRate, parseRate, rate } from './domain/money/rate.js';
export type { Quantity } from './domain/quantity/quantity.js';
export { formatQuantity, parseQuantity, quantity } from './domain/quantity/quantity.js';
export type { UnitCode } from './domain/quantity/unit.js';
export { isUnitCode, parseUnit, unitName } from './domain/quantity/unit.js';

// Where on the ground, and when.
export type { Station } from './domain/chainage/station.js';
export { asStation, formatStation, parseStation } from './domain/chainage/station.js';
export type { ChainageRange } from './domain/chainage/range.js';
export {
  chainageRange,
  coveredLength,
  formatChainageRange,
  intersectRangeLists,
  subtractRange,
  unionRanges,
} from './domain/chainage/range.js';
export type { Alignment, ChainageEquation } from './domain/chainage/alignment.js';
export {
  buildAlignment,
  locateStation,
  stationAtDistance,
} from './domain/chainage/alignment.js';
export type { SiteDate } from './domain/time/siteDate.js';
export {
  addDays,
  compareSiteDates,
  formatSiteDate,
  parseSiteDate,
  siteDate,
} from './domain/time/siteDate.js';
export type { SiteCalendar, Standdown } from './domain/time/calendar.js';
export { buildCalendar, addWorkingDays, workingDaysIn } from './domain/time/calendar.js';

// The bill, and the packages measured against it.
export type { BillItem, BillItemKind } from './domain/bill/billItem.js';
export { buildBillItem, billedValue } from './domain/bill/billItem.js';
export type { ItemCode } from './domain/bill/itemCode.js';
export { formatItemCode, itemCode, parseItemCode } from './domain/bill/itemCode.js';
export type { Bill } from './domain/bill/bill.js';
export { buildBill } from './domain/bill/bill.js';
export type { BillHistory, BillRevision } from './domain/bill/revision.js';
export { billAsAt, buildBillHistory, rateAsAt } from './domain/bill/revision.js';
export type { WorkPackage } from './domain/packages/workPackage.js';
export { buildWorkPackage } from './domain/packages/workPackage.js';
export type { CreditProfile } from './domain/credit/profile.js';
export { buildCreditProfile } from './domain/credit/profile.js';
export { STANDARD_PROFILES } from './domain/credit/catalogue.js';

// What was built, and what may be credited for it.
export type { SurveyRecord } from './domain/survey/record.js';
export { buildSurveyRecord, effectiveRecords } from './domain/survey/record.js';
export type { InspectionPlan } from './domain/itp/plan.js';
export { buildInspectionPlan } from './domain/itp/plan.js';
export type { SignOff } from './domain/itp/signOff.js';
export { buildSignOff } from './domain/itp/signOff.js';
export type { Ncr } from './domain/itp/ncr.js';
export { buildNcr } from './domain/itp/ncr.js';
export type { ProgressContext } from './progress/context.js';
export { buildProgressContext } from './progress/context.js';
export type { PackageEarning } from './progress/earn.js';
export { earnAll, earnPackage } from './progress/earn.js';
export type { Lookahead } from './progress/lookahead.js';
export { buildLookahead, readyNow } from './progress/lookahead.js';
export type { OutputAllowance, PackageOutput } from './progress/productivity.js';
export { buildAllowances, packageOutput } from './progress/productivity.js';

// What it is worth.
export type { ValuationPeriod, PeriodSchedule } from './valuation/period.js';
export { buildSchedule, periodByNumber } from './valuation/period.js';
export type { Certificate } from './valuation/certificate.js';
export {
  buildCertificate,
  certificateLines,
  certificateNotes,
  certifySeries,
} from './valuation/certificate.js';
export type { RetentionTerms } from './valuation/retention.js';
export { DEFAULT_RETENTION, computeRetention } from './valuation/retention.js';
export type { RetentionBond } from './valuation/bond.js';
export { bondPosition, buildRetentionBond } from './valuation/bond.js';
export type { CashPoint } from './valuation/cashFlow.js';
export { buildCashFlow, peakExposure } from './valuation/cashFlow.js';
export type { Outturn } from './valuation/outturn.js';
export { buildOutturn, outturnParts } from './valuation/outturn.js';

// The application, for anybody embedding the whole thing rather than a piece.
export type { Job, JobSettings, JobSettingsInput } from './store/job.js';
export { createJob, jobBlockers, jobContext } from './store/job.js';
export { jobFromJson, jobToJson } from './store/codec/job.js';
export type { Config } from './config/config.js';
export { configFromEnv, DEFAULT_CONFIG } from './config/config.js';
export type { Service } from './app/service.js';
export {
  certificateFor,
  createService,
  outturnFor,
  paymentsFor,
  progressFor,
  risksFor,
  variationsFor,
} from './app/service.js';
export type { Diagnosis, Finding } from './app/doctor.js';
export { diagnose, isHealthy } from './app/doctor.js';
export { buildServer } from './http/server.js';
export { buildProgram, runCli } from './cli/program.js';
export type { CliDeps } from './cli/program.js';
