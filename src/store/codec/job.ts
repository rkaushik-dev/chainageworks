import { BadInputError } from '../../errors.js';
import { buildBill } from '../../domain/bill/bill.js';
import type { BillItem, BillItemKind } from '../../domain/bill/billItem.js';
import { buildBillItem } from '../../domain/bill/billItem.js';
import type { BillRevision } from '../../domain/bill/revision.js';
import type { Alignment, ChainageEquation } from '../../domain/chainage/alignment.js';
import { buildAlignment } from '../../domain/chainage/alignment.js';
import type { CreditProfile } from '../../domain/credit/profile.js';
import { buildCreditProfile } from '../../domain/credit/profile.js';
import type { InspectionPlan, PointType } from '../../domain/itp/plan.js';
import { buildInspectionPlan } from '../../domain/itp/plan.js';
import type { Ncr, NcrStatus } from '../../domain/itp/ncr.js';
import { buildNcr } from '../../domain/itp/ncr.js';
import type { SignOff, SignOffOutcome } from '../../domain/itp/signOff.js';
import { buildSignOff } from '../../domain/itp/signOff.js';
import type { WorkPackage } from '../../domain/packages/workPackage.js';
import { buildWorkPackage } from '../../domain/packages/workPackage.js';
import type { MeasurementMethod, SurveyRecord } from '../../domain/survey/record.js';
import { buildSurveyRecord } from '../../domain/survey/record.js';
import type { Standdown, StanddownReason } from '../../domain/time/calendar.js';
import type {
  Variation,
  ValuationBasis,
  VariationStatus,
} from '../../domain/variation/variation.js';
import { buildVariation } from '../../domain/variation/variation.js';
import type { MaterialsOnSite } from '../../valuation/materials.js';
import { buildMaterialsOnSite } from '../../valuation/materials.js';
import {
  dayworkScheduleFromJson,
  dayworkScheduleToJson,
  dayworkSheetFromJson,
  dayworkSheetToJson,
} from './daywork.js';
import {
  completionFromJson,
  completionToJson,
  delayEventFromJson,
  delayEventToJson,
} from './delay.js';
import { defectFromJson, defectToJson } from './defects.js';
import {
  haulLinkFromJson,
  haulLinkToJson,
  haulSiteFromJson,
  haulSiteToJson,
} from './earthworks.js';
import {
  adjustmentTermsFromJson,
  adjustmentTermsToJson,
  indexSeriesFromJson,
  indexSeriesToJson,
} from './fluctuation.js';
import {
  allowanceFromJson,
  allowanceToJson,
  resourceReturnFromJson,
  resourceReturnToJson,
} from './resource.js';
import {
  applicationFromJson,
  applicationToJson,
  contraChargeFromJson,
  contraChargeToJson,
  noticeFromJson,
  noticeToJson,
  payLessFromJson,
  payLessToJson,
  subcontractFromJson,
  subcontractToJson,
} from './subcontract.js';
import type { Job, JobSettings } from '../job.js';
import { createJob } from '../job.js';
import { putAll } from '../repository.js';
import {
  arrayFromJson,
  booleanFromJson,
  codeFromJson,
  codeToJson,
  currencyFromJson,
  dateFromJson,
  dateToJson,
  moneyFromJson,
  moneyToJson,
  needString,
  objectFromJson,
  optionalDateFromJson,
  optionalDateToJson,
  optionalString,
  percentFromJson,
  percentToJson,
  quantityFromJson,
  quantityToJson,
  rangeFromJson,
  rangeToJson,
  rateFromJson,
  rateToJson,
  stationFromJson,
  stationToJson,
} from './values.js';

/** Bumped whenever the shape on disk changes in a way a reader has to know. */
export const DOCUMENT_VERSION = 1;

export interface JobDocument {
  readonly version: number;
  readonly settings: Record<string, unknown>;
  readonly alignments: readonly Record<string, unknown>[];
  readonly billRevisions: readonly Record<string, unknown>[];
  readonly packages: readonly Record<string, unknown>[];
  readonly profiles: readonly Record<string, unknown>[];
  readonly plans: readonly Record<string, unknown>[];
  readonly signOffs: readonly Record<string, unknown>[];
  readonly ncrs: readonly Record<string, unknown>[];
  readonly records: readonly Record<string, unknown>[];
  readonly materials: readonly Record<string, unknown>[];
  readonly variations: readonly Record<string, unknown>[];
  readonly dayworkSheets: readonly Record<string, unknown>[];
  readonly delayEvents: readonly Record<string, unknown>[];
  readonly defects: readonly Record<string, unknown>[];
  readonly subcontracts: readonly Record<string, unknown>[];
  readonly paymentApplications: readonly Record<string, unknown>[];
  readonly paymentNotices: readonly Record<string, unknown>[];
  readonly payLessNotices: readonly Record<string, unknown>[];
  readonly resourceReturns: readonly Record<string, unknown>[];
  readonly indices: readonly Record<string, unknown>[];
  readonly contraCharges: readonly Record<string, unknown>[];
  readonly haulSites: readonly Record<string, unknown>[];
  readonly holidays: readonly string[];
  readonly standdowns: readonly Record<string, unknown>[];
}

function enumFrom<T extends string>(value: unknown, allowed: readonly T[], what: string): T {
  const text = needString(value, what);
  const found = allowed.find((v) => v === text);
  if (found === undefined) {
    throw new BadInputError(`${text} is not a known ${what}`, { got: text });
  }
  return found;
}

const KINDS: readonly BillItemKind[] = [
  'measured',
  'provisional',
  'prime_cost',
  'lump',
  'star',
];
const POINT_TYPES: readonly PointType[] = ['hold', 'witness', 'review'];
const OUTCOMES: readonly SignOffOutcome[] = ['passed', 'passed_with_comment', 'failed'];
const NCR_STATUSES: readonly NcrStatus[] = ['open', 'closed', 'accepted'];
const METHODS: readonly MeasurementMethod[] = [
  'tape',
  'total_station',
  'gps',
  'weighbridge',
  'count',
  'drawing',
];
const REASONS: readonly StanddownReason[] = [
  'weather',
  'ground_conditions',
  'access',
  'industrial',
  'other',
];
const VARIATION_STATUSES: readonly VariationStatus[] = [
  'proposed',
  'instructed',
  'agreed',
  'rejected',
];
const BASES: readonly ValuationBasis[] = [
  'bill_rates',
  'pro_rata',
  'star_rate',
  'daywork',
  'lump_sum',
];

function settingsToJson(settings: JobSettings): Record<string, unknown> {
  return {
    id: settings.id,
    name: settings.name,
    currency: settings.currency,
    contractSum: moneyToJson(settings.contractSum),
    retention: {
      rate: percentToJson(settings.retention.rate),
      limit: percentToJson(settings.retention.limit),
      releaseOnCompletion: percentToJson(settings.retention.releaseOnCompletion),
    },
    materialsAllowance:
      settings.materialsAllowance === null ? null : percentToJson(settings.materialsAllowance),
    daywork: settings.daywork === null ? null : dayworkScheduleToJson(settings.daywork),
    completion: settings.completion === null ? null : completionToJson(settings.completion),
    defectsPeriodEnds: optionalDateToJson(settings.defectsPeriodEnds),
    outputAllowances: settings.outputAllowances.map(allowanceToJson),
    adjustment:
      settings.adjustment === null ? null : adjustmentTermsToJson(settings.adjustment),
    contraNotifyDays: settings.contraNotifyDays,
    haulRate: moneyToJson(settings.haulRate),
    haulLinks: settings.haulLinks.map(haulLinkToJson),
    tolerances: settings.tolerances.rules.map((rule) => ({
      workClass: rule.workClass,
      allowance: percentToJson(rule.allowance),
    })),
    workingDays: [...settings.workingDays],
    cutOffDay: settings.cutOffDay,
    issueDays: settings.issueDays,
    paymentDays: settings.paymentDays,
    substantialCompletionOn: optionalDateToJson(settings.substantialCompletionOn),
    defectsCertificateOn: optionalDateToJson(settings.defectsCertificateOn),
  };
}

function alignmentToJson(alignment: Alignment): Record<string, unknown> {
  return {
    id: alignment.id,
    name: alignment.name,
    start: stationToJson(alignment.start),
    end: stationToJson(alignment.end),
    equations: alignment.equations.map((e) => ({
      back: stationToJson(e.back),
      ahead: stationToJson(e.ahead),
    })),
  };
}

function alignmentFromJson(value: unknown): Alignment {
  const raw = objectFromJson(value, 'alignment');
  const equations: ChainageEquation[] = arrayFromJson(
    raw['equations'] ?? [],
    'chainage equations',
  ).map((entry) => {
    const e = objectFromJson(entry, 'chainage equation');
    return { back: stationFromJson(e['back']), ahead: stationFromJson(e['ahead']) };
  });
  return buildAlignment({
    id: needString(raw['id'], 'alignment id'),
    name: optionalString(raw['name']),
    start: stationFromJson(raw['start']),
    end: stationFromJson(raw['end']),
    equations,
  });
}

function billItemToJson(item: BillItem): Record<string, unknown> {
  return {
    code: codeToJson(item.code),
    description: item.description,
    rate: rateToJson(item.rate),
    quantity: quantityToJson(item.quantity),
    kind: item.kind,
    chainage: item.chainage === null ? null : rangeToJson(item.chainage),
  };
}

function billItemFromJson(value: unknown): BillItem {
  const raw = objectFromJson(value, 'bill item');
  const chainage = raw['chainage'];
  return buildBillItem({
    code: codeFromJson(raw['code']),
    description: needString(raw['description'], 'description'),
    rate: rateFromJson(raw['rate']),
    quantity: quantityFromJson(raw['quantity']),
    kind: enumFrom(raw['kind'], KINDS, 'item kind'),
    chainage: chainage === null || chainage === undefined ? null : rangeFromJson(chainage),
  });
}

function revisionToJson(revision: BillRevision): Record<string, unknown> {
  return {
    reference: revision.reference,
    effectiveFrom: dateToJson(revision.effectiveFrom),
    note: revision.note,
    bill: {
      id: revision.bill.id,
      title: revision.bill.title,
      currency: revision.bill.currency,
      items: revision.bill.items.map(billItemToJson),
    },
  };
}

function revisionFromJson(value: unknown): BillRevision {
  const raw = objectFromJson(value, 'bill revision');
  const bill = objectFromJson(raw['bill'], 'bill');
  return {
    reference: needString(raw['reference'], 'revision reference'),
    effectiveFrom: dateFromJson(raw['effectiveFrom']),
    note: optionalString(raw['note']),
    bill: buildBill({
      id: needString(bill['id'], 'bill id'),
      title: optionalString(bill['title']),
      currency: currencyFromJson(bill['currency']),
      items: arrayFromJson(bill['items'], 'bill items').map(billItemFromJson),
    }),
  };
}

function packageToJson(pkg: WorkPackage): Record<string, unknown> {
  return {
    id: pkg.id,
    name: pkg.name,
    alignmentId: pkg.alignmentId,
    extent: rangeToJson(pkg.extent),
    section: pkg.section,
    profileId: pkg.profileId,
    allocations: pkg.allocations.map((a) => ({
      code: codeToJson(a.code),
      quantity: quantityToJson(a.quantity),
    })),
  };
}

function packageFromJson(value: unknown): WorkPackage {
  const raw = objectFromJson(value, 'work package');
  return buildWorkPackage({
    id: needString(raw['id'], 'package id'),
    name: optionalString(raw['name']),
    alignmentId: needString(raw['alignmentId'], 'alignment id'),
    extent: rangeFromJson(raw['extent']),
    section: optionalString(raw['section']),
    profileId: needString(raw['profileId'], 'rule of credit id'),
    allocations: arrayFromJson(raw['allocations'], 'allocations').map((entry) => {
      const a = objectFromJson(entry, 'allocation');
      return { code: codeFromJson(a['code']), quantity: quantityFromJson(a['quantity']) };
    }),
  });
}

function profileToJson(profile: CreditProfile): Record<string, unknown> {
  return {
    id: profile.id,
    name: profile.name,
    steps: profile.steps.map((s) => ({
      id: s.id,
      name: s.name,
      weight: percentToJson(s.weight),
      requiresPrevious: s.requiresPrevious,
    })),
  };
}

function profileFromJson(value: unknown): CreditProfile {
  const raw = objectFromJson(value, 'rule of credit');
  return buildCreditProfile({
    id: needString(raw['id'], 'profile id'),
    name: optionalString(raw['name']),
    steps: arrayFromJson(raw['steps'], 'steps').map((entry) => {
      const s = objectFromJson(entry, 'step');
      return {
        id: needString(s['id'], 'step id'),
        name: optionalString(s['name']),
        weight: percentFromJson(s['weight']),
        requiresPrevious: booleanFromJson(s['requiresPrevious'] ?? false, 'requiresPrevious'),
      };
    }),
  });
}

function planToJson(plan: InspectionPlan): Record<string, unknown> {
  return {
    id: plan.id,
    name: plan.name,
    profileId: plan.profileId,
    points: plan.points.map((p) => ({
      id: p.id,
      name: p.name,
      type: p.type,
      stepId: p.stepId,
      discipline: p.discipline,
      reference: p.reference,
    })),
  };
}

function planFromJson(value: unknown, profiles: readonly CreditProfile[]): InspectionPlan {
  const raw = objectFromJson(value, 'inspection plan');
  const profileId = needString(raw['profileId'], 'rule of credit id');
  const profile = profiles.find((p) => p.id === profileId);
  if (profile === undefined) {
    throw new BadInputError('the plan gates a rule of credit the job does not carry', {
      profile: profileId,
    });
  }
  return buildInspectionPlan(
    {
      id: needString(raw['id'], 'plan id'),
      name: optionalString(raw['name']),
      profileId,
      points: arrayFromJson(raw['points'], 'inspection points').map((entry) => {
        const p = objectFromJson(entry, 'inspection point');
        return {
          id: needString(p['id'], 'point id'),
          name: optionalString(p['name']),
          type: enumFrom(p['type'], POINT_TYPES, 'point type'),
          stepId: needString(p['stepId'], 'step id'),
          discipline: optionalString(p['discipline'], 'civil'),
          reference: optionalString(p['reference']),
        };
      }),
    },
    profile,
  );
}

function signOffToJson(signOff: SignOff): Record<string, unknown> {
  return {
    id: signOff.id,
    pointId: signOff.pointId,
    packageId: signOff.packageId,
    extent: rangeToJson(signOff.extent),
    signedOn: dateToJson(signOff.signedOn),
    by: signOff.by,
    outcome: signOff.outcome,
    note: signOff.note,
  };
}

function signOffFromJson(value: unknown): SignOff {
  const raw = objectFromJson(value, 'sign off');
  return buildSignOff({
    id: needString(raw['id'], 'sign off id'),
    pointId: needString(raw['pointId'], 'point id'),
    packageId: needString(raw['packageId'], 'package id'),
    extent: rangeFromJson(raw['extent']),
    signedOn: dateFromJson(raw['signedOn']),
    by: needString(raw['by'], 'signatory'),
    outcome: enumFrom(raw['outcome'] ?? 'passed', OUTCOMES, 'sign off outcome'),
    note: optionalString(raw['note']),
  });
}

function ncrToJson(ncr: Ncr): Record<string, unknown> {
  return {
    id: ncr.id,
    packageId: ncr.packageId,
    extent: rangeToJson(ncr.extent),
    stepId: ncr.stepId,
    raisedOn: dateToJson(ncr.raisedOn),
    settledOn: optionalDateToJson(ncr.settledOn),
    status: ncr.status,
    description: ncr.description,
    withholdsCredit: ncr.withholdsCredit,
  };
}

function ncrFromJson(value: unknown): Ncr {
  const raw = objectFromJson(value, 'non conformance');
  const stepId = raw['stepId'];
  return buildNcr({
    id: needString(raw['id'], 'ncr id'),
    packageId: needString(raw['packageId'], 'package id'),
    extent: rangeFromJson(raw['extent']),
    stepId: stepId === null || stepId === undefined ? null : needString(stepId, 'step id'),
    raisedOn: dateFromJson(raw['raisedOn']),
    settledOn: optionalDateFromJson(raw['settledOn']),
    status: enumFrom(raw['status'] ?? 'open', NCR_STATUSES, 'ncr status'),
    description: needString(raw['description'], 'description'),
    withholdsCredit: booleanFromJson(raw['withholdsCredit'] ?? true, 'withholdsCredit'),
  });
}

function recordToJson(record: SurveyRecord): Record<string, unknown> {
  return {
    id: record.id,
    packageId: record.packageId,
    code: codeToJson(record.code),
    stepId: record.stepId,
    extent: rangeToJson(record.extent),
    quantity: quantityToJson(record.quantity),
    measuredOn: dateToJson(record.measuredOn),
    bookedBy: record.bookedBy,
    method: record.method,
    supersedes: record.supersedes,
    note: record.note,
  };
}

function recordFromJson(value: unknown): SurveyRecord {
  const raw = objectFromJson(value, 'survey record');
  const supersedes = raw['supersedes'];
  return buildSurveyRecord({
    id: needString(raw['id'], 'record id'),
    packageId: needString(raw['packageId'], 'package id'),
    code: codeFromJson(raw['code']),
    stepId: needString(raw['stepId'], 'step id'),
    extent: rangeFromJson(raw['extent']),
    quantity: quantityFromJson(raw['quantity']),
    measuredOn: dateFromJson(raw['measuredOn']),
    bookedBy: needString(raw['bookedBy'], 'booked by'),
    method: enumFrom(raw['method'] ?? 'tape', METHODS, 'measurement method'),
    supersedes:
      supersedes === null || supersedes === undefined
        ? null
        : needString(supersedes, 'superseded record'),
    note: optionalString(raw['note']),
  });
}

function materialsToJson(materials: MaterialsOnSite): Record<string, unknown> {
  return {
    id: materials.id,
    packageId: materials.packageId,
    code: codeToJson(materials.code),
    description: materials.description,
    quantity: quantityToJson(materials.quantity),
    value: moneyToJson(materials.value),
    deliveredOn: dateToJson(materials.deliveredOn),
    vestedOn: optionalDateToJson(materials.vestedOn),
    incorporatedOn: optionalDateToJson(materials.incorporatedOn),
    protectedOnSite: materials.protectedOnSite,
  };
}

function materialsFromJson(value: unknown): MaterialsOnSite {
  const raw = objectFromJson(value, 'materials on site');
  return buildMaterialsOnSite({
    id: needString(raw['id'], 'materials id'),
    packageId: needString(raw['packageId'], 'package id'),
    code: codeFromJson(raw['code']),
    description: needString(raw['description'], 'description'),
    quantity: quantityFromJson(raw['quantity']),
    value: moneyFromJson(raw['value']),
    deliveredOn: dateFromJson(raw['deliveredOn']),
    vestedOn: optionalDateFromJson(raw['vestedOn']),
    incorporatedOn: optionalDateFromJson(raw['incorporatedOn']),
    protectedOnSite: booleanFromJson(raw['protectedOnSite'] ?? true, 'protectedOnSite'),
  });
}

function variationToJson(variation: Variation): Record<string, unknown> {
  return {
    id: variation.id,
    reference: variation.reference,
    title: variation.title,
    status: variation.status,
    instructedOn: optionalDateToJson(variation.instructedOn),
    agreedOn: optionalDateToJson(variation.agreedOn),
    extent: variation.extent === null ? null : rangeToJson(variation.extent),
    items: variation.items.map((i) => ({
      code: codeToJson(i.code),
      quantityChange: quantityToJson(i.quantityChange),
      rate: i.rate === null ? null : rateToJson(i.rate),
      basis: i.basis,
    })),
  };
}

function variationFromJson(value: unknown): Variation {
  const raw = objectFromJson(value, 'variation');
  const extent = raw['extent'];
  return buildVariation({
    id: needString(raw['id'], 'variation id'),
    reference: optionalString(raw['reference']),
    title: optionalString(raw['title']),
    status: enumFrom(raw['status'] ?? 'proposed', VARIATION_STATUSES, 'variation status'),
    instructedOn: optionalDateFromJson(raw['instructedOn']),
    agreedOn: optionalDateFromJson(raw['agreedOn']),
    extent: extent === null || extent === undefined ? null : rangeFromJson(extent),
    items: arrayFromJson(raw['items'], 'variation items').map((entry) => {
      const i = objectFromJson(entry, 'variation item');
      const rate = i['rate'];
      return {
        code: codeFromJson(i['code']),
        quantityChange: quantityFromJson(i['quantityChange']),
        rate: rate === null || rate === undefined ? null : rateFromJson(rate),
        basis: enumFrom(i['basis'], BASES, 'valuation basis'),
      };
    }),
  });
}

function standdownToJson(standdown: Standdown): Record<string, unknown> {
  return {
    date: dateToJson(standdown.date),
    reason: standdown.reason,
    note: standdown.note ?? '',
  };
}

function standdownFromJson(value: unknown): Standdown {
  const raw = objectFromJson(value, 'standdown');
  return {
    date: dateFromJson(raw['date']),
    reason: enumFrom(raw['reason'], REASONS, 'standdown reason'),
    note: optionalString(raw['note']),
  };
}

export function jobToJson(job: Job): JobDocument {
  return {
    version: DOCUMENT_VERSION,
    settings: settingsToJson(job.settings),
    alignments: job.alignments.all().map(alignmentToJson),
    billRevisions: job.billRevisions.all().map(revisionToJson),
    packages: job.packages.all().map(packageToJson),
    profiles: job.profiles.all().map(profileToJson),
    plans: job.plans.all().map(planToJson),
    signOffs: job.signOffs.all().map(signOffToJson),
    ncrs: job.ncrs.all().map(ncrToJson),
    records: job.records.all().map(recordToJson),
    materials: job.materials.all().map(materialsToJson),
    variations: job.variations.all().map(variationToJson),
    dayworkSheets: job.dayworkSheets.all().map(dayworkSheetToJson),
    delayEvents: job.delayEvents.all().map(delayEventToJson),
    defects: job.defects.all().map(defectToJson),
    subcontracts: job.subcontracts.all().map(subcontractToJson),
    paymentApplications: job.paymentApplications.all().map(applicationToJson),
    paymentNotices: job.paymentNotices.all().map(noticeToJson),
    payLessNotices: job.payLessNotices.all().map(payLessToJson),
    resourceReturns: job.resourceReturns.all().map(resourceReturnToJson),
    indices: job.indices.all().map(indexSeriesToJson),
    contraCharges: job.contraCharges.all().map(contraChargeToJson),
    haulSites: job.haulSites.all().map(haulSiteToJson),
    holidays: job.holidays.map(dateToJson),
    standdowns: job.standdowns.map(standdownToJson),
  };
}

export function jobFromJson(value: unknown): Job {
  const doc = objectFromJson(value, 'job document');
  const version = doc['version'];
  if (version !== DOCUMENT_VERSION) {
    throw new BadInputError('this file was written by a different version', {
      found: String(version),
      expected: DOCUMENT_VERSION,
    });
  }
  const settings = objectFromJson(doc['settings'], 'settings');
  const retention = objectFromJson(settings['retention'], 'retention terms');
  const allowance = settings['materialsAllowance'];

  const job = createJob({
    id: needString(settings['id'], 'job id'),
    name: optionalString(settings['name']),
    currency: currencyFromJson(settings['currency']),
    contractSum: moneyFromJson(settings['contractSum']),
    retention: {
      rate: percentFromJson(retention['rate']),
      limit: percentFromJson(retention['limit']),
      releaseOnCompletion: percentFromJson(retention['releaseOnCompletion']),
    },
    materialsAllowance:
      allowance === null || allowance === undefined ? null : percentFromJson(allowance),
    daywork: dayworkScheduleFromJson(settings['daywork']),
    completion: completionFromJson(settings['completion']),
    defectsPeriodEnds: optionalDateFromJson(settings['defectsPeriodEnds']),
    outputAllowances: arrayFromJson(
      settings['outputAllowances'] ?? [],
      'output allowances',
    ).map(allowanceFromJson),
    adjustment: adjustmentTermsFromJson(settings['adjustment']),
    contraNotifyDays: Number(settings['contraNotifyDays'] ?? 14),
    haulRate:
      settings['haulRate'] === undefined || settings['haulRate'] === null
        ? { minor: 0, currency: currencyFromJson(settings['currency']) }
        : moneyFromJson(settings['haulRate']),
    haulLinks: arrayFromJson(settings['haulLinks'] ?? [], 'haul links').map(haulLinkFromJson),
    tolerances: {
      rules: arrayFromJson(settings['tolerances'], 'tolerance rules').map((entry) => {
        const rule = objectFromJson(entry, 'tolerance rule');
        const workClass = rule['workClass'];
        return {
          workClass:
            workClass === null || workClass === undefined
              ? null
              : needString(workClass, 'work class'),
          allowance: percentFromJson(rule['allowance']),
        };
      }),
    },
    workingDays: arrayFromJson(settings['workingDays'], 'working days').map((day) =>
      Number(day),
    ),
    cutOffDay: Number(settings['cutOffDay']),
    issueDays: Number(settings['issueDays']),
    paymentDays: Number(settings['paymentDays']),
    substantialCompletionOn: optionalDateFromJson(settings['substantialCompletionOn']),
    defectsCertificateOn: optionalDateFromJson(settings['defectsCertificateOn']),
  });

  job.profiles.clear();
  const profiles = arrayFromJson(doc['profiles'], 'rules of credit').map(profileFromJson);
  putAll(job.profiles, profiles);
  putAll(job.alignments, arrayFromJson(doc['alignments'], 'alignments').map(alignmentFromJson));
  putAll(
    job.billRevisions,
    arrayFromJson(doc['billRevisions'], 'bill revisions').map(revisionFromJson),
  );
  putAll(job.packages, arrayFromJson(doc['packages'], 'work packages').map(packageFromJson));
  putAll(
    job.plans,
    arrayFromJson(doc['plans'], 'inspection plans').map((entry) =>
      planFromJson(entry, profiles),
    ),
  );
  putAll(job.signOffs, arrayFromJson(doc['signOffs'], 'sign offs').map(signOffFromJson));
  putAll(job.ncrs, arrayFromJson(doc['ncrs'], 'non conformances').map(ncrFromJson));
  putAll(job.records, arrayFromJson(doc['records'], 'survey records').map(recordFromJson));
  putAll(
    job.materials,
    arrayFromJson(doc['materials'], 'materials on site').map(materialsFromJson),
  );
  putAll(job.variations, arrayFromJson(doc['variations'], 'variations').map(variationFromJson));
  putAll(
    job.dayworkSheets,
    arrayFromJson(doc['dayworkSheets'] ?? [], 'daywork sheets').map(dayworkSheetFromJson),
  );
  putAll(
    job.delayEvents,
    arrayFromJson(doc['delayEvents'] ?? [], 'delay events').map(delayEventFromJson),
  );
  putAll(job.defects, arrayFromJson(doc['defects'] ?? [], 'defects').map(defectFromJson));
  putAll(
    job.subcontracts,
    arrayFromJson(doc['subcontracts'] ?? [], 'subcontracts').map(subcontractFromJson),
  );
  putAll(
    job.paymentApplications,
    arrayFromJson(doc['paymentApplications'] ?? [], 'payment applications').map(
      applicationFromJson,
    ),
  );
  putAll(
    job.paymentNotices,
    arrayFromJson(doc['paymentNotices'] ?? [], 'payment notices').map(noticeFromJson),
  );
  putAll(
    job.payLessNotices,
    arrayFromJson(doc['payLessNotices'] ?? [], 'pay less notices').map(payLessFromJson),
  );
  putAll(
    job.resourceReturns,
    arrayFromJson(doc['resourceReturns'] ?? [], 'resource returns').map(resourceReturnFromJson),
  );
  putAll(
    job.indices,
    arrayFromJson(doc['indices'] ?? [], 'index series').map(indexSeriesFromJson),
  );
  putAll(
    job.contraCharges,
    arrayFromJson(doc['contraCharges'] ?? [], 'contra charges').map(contraChargeFromJson),
  );
  putAll(
    job.haulSites,
    arrayFromJson(doc['haulSites'] ?? [], 'haul sites').map(haulSiteFromJson),
  );
  job.holidays = arrayFromJson(doc['holidays'], 'holidays').map(dateFromJson);
  job.standdowns = arrayFromJson(doc['standdowns'], 'standdowns').map(standdownFromJson);
  return job;
}
