import type { CurrencyCode } from '../domain/money/money.js';
import type {
  PayLessNotice,
  PaymentApplication,
  PaymentNotice,
} from '../domain/subcontract/payment.js';
import type { RowReader } from '../io/csv.js';
import { need, needDate, needMoney, needNumber, optional } from './fields.js';
import type { ImportReport } from './report.js';
import { readTable } from './rows.js';

export const APPLICATION_COLUMNS = [
  'application',
  'subcontract',
  'valuation',
  'applied on',
  'gross',
] as const;

export const NOTICE_COLUMNS = ['application', 'served on', 'valued'] as const;

export const PAY_LESS_COLUMNS = ['application', 'served on', 'amount', 'reason'] as const;

export interface PaymentImportOptions {
  readonly currency: CurrencyCode;
}

/**
 * Read a subcontract payment application schedule.
 *
 * The previously paid column is optional and falls back to nothing, because
 * the first application against an order has no such figure and a commercial
 * clerk exporting one row should not have to type a zero to say so.
 */
export function importApplications(
  text: string,
  options: PaymentImportOptions,
): ImportReport<PaymentApplication> {
  return readTable(text, {
    columns: APPLICATION_COLUMNS,
    read: (reader) => readApplication(reader, options.currency),
    keyOf: (application) => application.id,
    duplicate: (id) => `application ${id} appears twice`,
    keyColumn: 'application',
  });
}

function readApplication(reader: RowReader, currency: CurrencyCode): PaymentApplication {
  const previously = optional(reader, 'previously paid');
  return {
    id: need(reader, 'application'),
    subcontractId: need(reader, 'subcontract'),
    periodNumber: needNumber(reader, 'valuation'),
    appliedOn: needDate(reader, 'applied on'),
    gross: needMoney(reader, 'gross', currency),
    previouslyPaid:
      previously === null
        ? { minor: 0, currency }
        : needMoney(reader, 'previously paid', currency),
  };
}

/**
 * Read the payment notices the payer has served.
 *
 * Keyed on the application rather than carrying an id of their own. A notice
 * that answers no application is not a notice, and giving these rows their own
 * identity would let one be filed against nothing at all.
 */
export function importNotices(
  text: string,
  options: PaymentImportOptions,
): ImportReport<PaymentNotice> {
  return readTable(text, {
    columns: NOTICE_COLUMNS,
    read: (reader): PaymentNotice => ({
      applicationId: need(reader, 'application'),
      servedOn: needDate(reader, 'served on'),
      valued: needMoney(reader, 'valued', options.currency),
    }),
    keyOf: (notice) => notice.applicationId,
    duplicate: (id) => `application ${id} has two notices against it`,
    keyColumn: 'application',
  });
}

/** Read the pay less notices, which carry the reason they have to give. */
export function importPayLessNotices(
  text: string,
  options: PaymentImportOptions,
): ImportReport<PayLessNotice> {
  return readTable(text, {
    columns: PAY_LESS_COLUMNS,
    read: (reader): PayLessNotice => ({
      applicationId: need(reader, 'application'),
      servedOn: needDate(reader, 'served on'),
      amount: needMoney(reader, 'amount', options.currency),
      reason: need(reader, 'reason'),
    }),
    keyOf: (notice) => notice.applicationId,
    duplicate: (id) => `application ${id} has two pay less notices against it`,
    keyColumn: 'application',
  });
}
