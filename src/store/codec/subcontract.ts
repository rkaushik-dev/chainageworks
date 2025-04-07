import type {
  PayLessNotice,
  PaymentApplication,
  PaymentNotice,
  PaymentTerms,
} from '../../domain/subcontract/payment.js';
import { buildPaymentTerms } from '../../domain/subcontract/payment.js';
import type { Subcontract } from '../../domain/subcontract/subcontract.js';
import { buildSubcontract } from '../../domain/subcontract/subcontract.js';
import {
  arrayFromJson,
  codeFromJson,
  codeToJson,
  dateFromJson,
  dateToJson,
  moneyFromJson,
  moneyToJson,
  needString,
  objectFromJson,
  optionalString,
  percentFromJson,
  percentToJson,
} from './values.js';

function termsToJson(terms: PaymentTerms): Record<string, unknown> {
  return {
    dueDays: terms.dueDays,
    noticeDays: terms.noticeDays,
    payLessDays: terms.payLessDays,
    finalDays: terms.finalDays,
    retention: percentToJson(terms.retention),
  };
}

/**
 * Read the payment terms back.
 *
 * Every window is optional on the way in and falls back to the statutory
 * default, because a job file written before a term existed has to keep
 * loading. The alternative is a file that refuses to open on the day the
 * schema grows a field, which is the worst possible time to find out.
 */
function termsFromJson(value: unknown): PaymentTerms {
  if (value === null || value === undefined) return buildPaymentTerms();
  const raw = objectFromJson(value, 'payment terms');
  const days = (key: string, fallback: number): number => {
    const found = raw[key];
    return found === null || found === undefined ? fallback : Number(found);
  };
  const retention = raw['retention'];
  const base = buildPaymentTerms();
  return buildPaymentTerms({
    dueDays: days('dueDays', base.dueDays),
    noticeDays: days('noticeDays', base.noticeDays),
    payLessDays: days('payLessDays', base.payLessDays),
    finalDays: days('finalDays', base.finalDays),
    retention:
      retention === null || retention === undefined
        ? base.retention
        : percentFromJson(retention),
  });
}

export function subcontractToJson(subcontract: Subcontract): Record<string, unknown> {
  return {
    id: subcontract.id,
    name: subcontract.name,
    contractor: subcontract.contractor,
    orderValue: moneyToJson(subcontract.orderValue),
    packageIds: [...subcontract.packageIds],
    codes: subcontract.codes.map(codeToJson),
    terms: termsToJson(subcontract.terms),
    placedOn: dateToJson(subcontract.placedOn),
  };
}

export function subcontractFromJson(value: unknown): Subcontract {
  const raw = objectFromJson(value, 'subcontract');
  return buildSubcontract({
    id: needString(raw['id'], 'subcontract id'),
    name: optionalString(raw['name']),
    contractor: needString(raw['contractor'], 'contractor'),
    orderValue: moneyFromJson(raw['orderValue']),
    packageIds: arrayFromJson(raw['packageIds'] ?? [], 'package ids').map((id) =>
      needString(id, 'package id'),
    ),
    codes: arrayFromJson(raw['codes'] ?? [], 'bill items').map(codeFromJson),
    terms: termsFromJson(raw['terms']),
    placedOn: dateFromJson(raw['placedOn']),
  });
}

export function applicationToJson(application: PaymentApplication): Record<string, unknown> {
  return {
    id: application.id,
    subcontractId: application.subcontractId,
    periodNumber: application.periodNumber,
    appliedOn: dateToJson(application.appliedOn),
    gross: moneyToJson(application.gross),
    previouslyPaid: moneyToJson(application.previouslyPaid),
  };
}

export function applicationFromJson(value: unknown): PaymentApplication {
  const raw = objectFromJson(value, 'payment application');
  return {
    id: needString(raw['id'], 'application id'),
    subcontractId: needString(raw['subcontractId'], 'subcontract id'),
    periodNumber: Number(raw['periodNumber']),
    appliedOn: dateFromJson(raw['appliedOn']),
    gross: moneyFromJson(raw['gross']),
    previouslyPaid: moneyFromJson(raw['previouslyPaid']),
  };
}

export function noticeToJson(notice: PaymentNotice): Record<string, unknown> {
  return {
    applicationId: notice.applicationId,
    servedOn: dateToJson(notice.servedOn),
    valued: moneyToJson(notice.valued),
  };
}

export function noticeFromJson(value: unknown): PaymentNotice {
  const raw = objectFromJson(value, 'payment notice');
  return {
    applicationId: needString(raw['applicationId'], 'application id'),
    servedOn: dateFromJson(raw['servedOn']),
    valued: moneyFromJson(raw['valued']),
  };
}

export function payLessToJson(notice: PayLessNotice): Record<string, unknown> {
  return {
    applicationId: notice.applicationId,
    servedOn: dateToJson(notice.servedOn),
    amount: moneyToJson(notice.amount),
    reason: notice.reason,
  };
}

export function payLessFromJson(value: unknown): PayLessNotice {
  const raw = objectFromJson(value, 'pay less notice');
  return {
    applicationId: needString(raw['applicationId'], 'application id'),
    servedOn: dateFromJson(raw['servedOn']),
    amount: moneyFromJson(raw['amount']),
    reason: needString(raw['reason'], 'pay less reason'),
  };
}
