import { BadInputError } from '../errors.js';
import type { Alignment } from '../domain/chainage/alignment.js';
import type { BillRevision } from '../domain/bill/revision.js';
import { buildBillHistory } from '../domain/bill/revision.js';
import type { CreditProfile } from '../domain/credit/profile.js';
import type { DayworkSchedule } from '../domain/daywork/schedule.js';
import type { DayworkSheet } from '../domain/daywork/sheet.js';
import type { CompletionTerms } from '../domain/delay/completion.js';
import type { DelayEvent } from '../domain/delay/event.js';
import type { Defect } from '../domain/defects/defect.js';
import { STANDARD_PROFILES, buildProfileRegistry } from '../domain/credit/catalogue.js';
import type { InspectionPlan } from '../domain/itp/plan.js';
import type { Ncr } from '../domain/itp/ncr.js';
import type { SignOff } from '../domain/itp/signOff.js';
import type { CurrencyCode, Money } from '../domain/money/money.js';
import type { Percent } from '../domain/money/percent.js';
import { buildRegister } from '../domain/packages/register.js';
import type { WorkPackage } from '../domain/packages/workPackage.js';
import type {
  PayLessNotice,
  PaymentApplication,
  PaymentNotice,
} from '../domain/subcontract/payment.js';
import type { ContraCharge } from '../domain/subcontract/contraCharge.js';
import type { Subcontract } from '../domain/subcontract/subcontract.js';
import { DEFAULT_CONTRA_NOTIFY_DAYS } from '../domain/subcontract/subcontract.js';
import type { AdjustmentTerms } from '../domain/fluctuation/adjustment.js';
import type { IndexSeries } from '../domain/fluctuation/index.js';
import type { ResourceReturn } from '../domain/resource/return.js';
import type { SurveyRecord } from '../domain/survey/record.js';
import type { ToleranceSet } from '../domain/survey/tolerance.js';
import { DEFAULT_TOLERANCES } from '../domain/survey/tolerance.js';
import type { SiteCalendar, Standdown } from '../domain/time/calendar.js';
import { buildCalendar, DEFAULT_WORKING_DAYS } from '../domain/time/calendar.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import type { Variation } from '../domain/variation/variation.js';
import type { OutputAllowance } from '../progress/productivity.js';
import { buildAllowances } from '../progress/productivity.js';
import type { ProgressContext } from '../progress/context.js';
import { buildProgressContext } from '../progress/context.js';
import type { MaterialsOnSite } from '../valuation/materials.js';
import type { PeriodSchedule } from '../valuation/period.js';
import { buildSchedule } from '../valuation/period.js';
import type { RetentionTerms } from '../valuation/retention.js';
import { DEFAULT_RETENTION } from '../valuation/retention.js';
import type { Repository } from './repository.js';
import { inMemoryRepository } from './repository.js';

/** Everything about the contract that is not a record of what happened. */
export interface JobSettings {
  readonly id: string;
  readonly name: string;
  readonly currency: CurrencyCode;
  readonly contractSum: Money;
  readonly retention: RetentionTerms;
  readonly materialsAllowance: Percent | null;
  /** The daywork schedule, when the contract has one. */
  readonly daywork: DayworkSchedule | null;
  /** Completion date and liquidated damages, when the contract fixes them. */
  readonly completion: CompletionTerms | null;
  /** When the defects liability period ends, if it has been fixed. */
  readonly defectsPeriodEnds: SiteDate | null;
  /** What each step of a rule of credit was expected to produce in an hour. */
  readonly outputAllowances: readonly OutputAllowance[];
  /** The fluctuation clause, when the contract carries one. */
  readonly adjustment: AdjustmentTerms | null;
  /** Days to notify a subcontractor of a charge the order does not cover. */
  readonly contraNotifyDays: number;
  readonly tolerances: ToleranceSet;
  readonly workingDays: readonly number[];
  readonly cutOffDay: number;
  readonly issueDays: number;
  readonly paymentDays: number;
  readonly substantialCompletionOn: SiteDate | null;
  readonly defectsCertificateOn: SiteDate | null;
}

export interface JobSettingsInput {
  readonly id: string;
  readonly name: string;
  readonly currency?: CurrencyCode;
  readonly contractSum: Money;
  readonly retention?: RetentionTerms;
  readonly materialsAllowance?: Percent | null;
  readonly daywork?: DayworkSchedule | null;
  readonly completion?: CompletionTerms | null;
  readonly defectsPeriodEnds?: SiteDate | null;
  readonly outputAllowances?: readonly OutputAllowance[];
  readonly adjustment?: AdjustmentTerms | null;
  readonly contraNotifyDays?: number;
  readonly tolerances?: ToleranceSet;
  readonly workingDays?: readonly number[];
  readonly cutOffDay?: number;
  readonly issueDays?: number;
  readonly paymentDays?: number;
  readonly substantialCompletionOn?: SiteDate | null;
  readonly defectsCertificateOn?: SiteDate | null;
}

/**
 * One contract's worth of state.
 *
 * The repositories hold what happened and the settings hold what the contract
 * says. Keeping them apart matters because the settings are the only part
 * anybody edits by hand, and a bad edit there quietly changes every valuation
 * ever produced rather than one record.
 */
export interface Job {
  settings: JobSettings;
  readonly alignments: Repository<Alignment>;
  readonly billRevisions: Repository<BillRevision>;
  readonly packages: Repository<WorkPackage>;
  readonly profiles: Repository<CreditProfile>;
  readonly plans: Repository<InspectionPlan>;
  readonly signOffs: Repository<SignOff>;
  readonly ncrs: Repository<Ncr>;
  readonly records: Repository<SurveyRecord>;
  readonly materials: Repository<MaterialsOnSite>;
  readonly variations: Repository<Variation>;
  readonly dayworkSheets: Repository<DayworkSheet>;
  readonly delayEvents: Repository<DelayEvent>;
  readonly defects: Repository<Defect>;
  readonly subcontracts: Repository<Subcontract>;
  readonly paymentApplications: Repository<PaymentApplication>;
  readonly paymentNotices: Repository<PaymentNotice>;
  readonly payLessNotices: Repository<PayLessNotice>;
  readonly resourceReturns: Repository<ResourceReturn>;
  readonly indices: Repository<IndexSeries>;
  readonly contraCharges: Repository<ContraCharge>;
  holidays: readonly SiteDate[];
  standdowns: readonly Standdown[];
}

export function jobSettings(input: JobSettingsInput): JobSettings {
  const cutOffDay = input.cutOffDay ?? 25;
  if (!Number.isInteger(cutOffDay) || cutOffDay < 1 || cutOffDay > 31) {
    throw new BadInputError('the cut off day has to be a day of the month', { cutOffDay });
  }
  if (input.contractSum.minor < 0) {
    throw new BadInputError('the contract sum cannot be negative', { job: input.id });
  }
  const currency = input.currency ?? input.contractSum.currency;
  if (currency !== input.contractSum.currency) {
    throw new BadInputError('the contract sum is in a different currency from the job', {
      job: currency,
      contractSum: input.contractSum.currency,
    });
  }
  return {
    id: input.id,
    name: input.name,
    currency,
    contractSum: input.contractSum,
    retention: input.retention ?? DEFAULT_RETENTION,
    materialsAllowance: input.materialsAllowance ?? null,
    daywork: input.daywork ?? null,
    completion: input.completion ?? null,
    defectsPeriodEnds: input.defectsPeriodEnds ?? null,
    outputAllowances: buildAllowances(input.outputAllowances ?? []),
    adjustment: input.adjustment ?? null,
    contraNotifyDays: input.contraNotifyDays ?? DEFAULT_CONTRA_NOTIFY_DAYS,
    tolerances: input.tolerances ?? DEFAULT_TOLERANCES,
    workingDays: input.workingDays ?? DEFAULT_WORKING_DAYS,
    cutOffDay,
    issueDays: input.issueDays ?? 7,
    paymentDays: input.paymentDays ?? 14,
    substantialCompletionOn: input.substantialCompletionOn ?? null,
    defectsCertificateOn: input.defectsCertificateOn ?? null,
  };
}

export function createJob(input: JobSettingsInput): Job {
  return {
    settings: jobSettings(input),
    alignments: inMemoryRepository<Alignment>('alignment', (a) => a.id),
    billRevisions: inMemoryRepository<BillRevision>('bill revision', (r) => r.reference),
    packages: inMemoryRepository<WorkPackage>('work package', (p) => p.id),
    profiles: inMemoryRepository<CreditProfile>(
      'rule of credit',
      (p) => p.id,
      STANDARD_PROFILES,
    ),
    plans: inMemoryRepository<InspectionPlan>('inspection plan', (p) => p.id),
    signOffs: inMemoryRepository<SignOff>('sign off', (s) => s.id),
    ncrs: inMemoryRepository<Ncr>('non conformance', (n) => n.id),
    records: inMemoryRepository<SurveyRecord>('survey record', (r) => r.id),
    materials: inMemoryRepository<MaterialsOnSite>('materials on site', (m) => m.id),
    variations: inMemoryRepository<Variation>('variation', (v) => v.id),
    dayworkSheets: inMemoryRepository<DayworkSheet>('daywork sheet', (s) => s.id),
    delayEvents: inMemoryRepository<DelayEvent>('delay event', (e) => e.id),
    defects: inMemoryRepository<Defect>('defect', (x) => x.id),
    subcontracts: inMemoryRepository<Subcontract>('subcontract', (s) => s.id),
    paymentApplications: inMemoryRepository<PaymentApplication>(
      'payment application',
      (a) => a.id,
    ),
    paymentNotices: inMemoryRepository<PaymentNotice>('payment notice', (n) => n.applicationId),
    payLessNotices: inMemoryRepository<PayLessNotice>(
      'pay less notice',
      (n) => n.applicationId,
    ),
    resourceReturns: inMemoryRepository<ResourceReturn>('resource return', (r) => r.id),
    indices: inMemoryRepository<IndexSeries>('index series', (i) => i.id),
    contraCharges: inMemoryRepository<ContraCharge>('contra charge', (c) => c.id),
    holidays: [],
    standdowns: [],
  };
}

export function jobCalendar(job: Job): SiteCalendar {
  return buildCalendar({
    id: `${job.settings.id}-calendar`,
    name: `${job.settings.name} calendar`,
    workingDays: job.settings.workingDays,
    holidays: job.holidays,
    standdowns: job.standdowns,
  });
}

/**
 * Assemble the read model the progress engine wants.
 *
 * Built fresh every time rather than cached. The engine reads eleven registers
 * and any one of them can have moved since the last call, and a stale context
 * is a valuation that is quietly a week out of date.
 */
export function jobContext(job: Job): ProgressContext {
  if (job.billRevisions.count() === 0) {
    throw new BadInputError('the job has no bill yet', { job: job.settings.id });
  }
  return buildProgressContext({
    billHistory: buildBillHistory(job.billRevisions.all()),
    register: buildRegister(job.packages.all()),
    profiles: buildProfileRegistry(job.profiles.all()),
    plans: job.plans.all(),
    signOffs: job.signOffs.all(),
    ncrs: job.ncrs.all(),
    records: job.records.all(),
    calendar: jobCalendar(job),
    tolerances: job.settings.tolerances,
  });
}

export function jobSchedule(
  job: Job,
  firstYear: number,
  firstMonth: number,
  count: number,
): PeriodSchedule {
  return buildSchedule({
    firstYear,
    firstMonth,
    count,
    cutOffDay: job.settings.cutOffDay,
    issueDays: job.settings.issueDays,
    paymentDays: job.settings.paymentDays,
    calendar: jobCalendar(job),
  });
}

export function isJobReady(job: Job): boolean {
  return job.billRevisions.count() > 0 && job.packages.count() > 0;
}

/** What the job is missing before it can value anything. */
export function jobBlockers(job: Job): string[] {
  const blockers: string[] = [];
  if (job.billRevisions.count() === 0) blockers.push('no bill has been loaded');
  if (job.packages.count() === 0) blockers.push('no work packages have been set up');
  if (job.alignments.count() === 0) blockers.push('no alignment has been loaded');
  return blockers;
}
