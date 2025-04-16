import type { CurrencyCode } from '../domain/money/money.js';
import type {
  PayLessNotice,
  PaymentApplication,
  PaymentNotice,
} from '../domain/subcontract/payment.js';
import type { RowReader } from '../io/csv.js';
import { parseCsv, readerFor } from '../io/csv.js';
import { need, needDate, needMoney, needNumber, optional } from './fields.js';
import type { ImportIssue, ImportReport } from './report.js';
import { checkColumns, issue, issueFrom } from './report.js';

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
  const table = parseCsv(text);
  const columnProblem = checkColumns<PaymentApplication>(table, APPLICATION_COLUMNS);
  if (columnProblem !== null) return columnProblem;

  const imported: PaymentApplication[] = [];
  const issues: ImportIssue[] = [];
  const seen = new Set<string>();

  for (const row of table.rows) {
    const reader = readerFor(table, row);
    try {
      const application = readApplication(reader, options.currency);
      if (seen.has(application.id)) {
        issues.push(
          issue(`application ${application.id} appears twice`, row.line, 'application'),
        );
        continue;
      }
      seen.add(application.id);
      imported.push(application);
    } catch (error) {
      issues.push(issueFrom(error, reader.line));
    }
  }
  return { imported, issues, rowsRead: table.rows.length };
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
  const table = parseCsv(text);
  const columnProblem = checkColumns<PaymentNotice>(table, NOTICE_COLUMNS);
  if (columnProblem !== null) return columnProblem;

  const imported: PaymentNotice[] = [];
  const issues: ImportIssue[] = [];
  const seen = new Set<string>();

  for (const row of table.rows) {
    const reader = readerFor(table, row);
    try {
      const notice: PaymentNotice = {
        applicationId: need(reader, 'application'),
        servedOn: needDate(reader, 'served on'),
        valued: needMoney(reader, 'valued', options.currency),
      };
      if (seen.has(notice.applicationId)) {
        issues.push(
          issue(
            `application ${notice.applicationId} has two notices against it`,
            row.line,
            'application',
          ),
        );
        continue;
      }
      seen.add(notice.applicationId);
      imported.push(notice);
    } catch (error) {
      issues.push(issueFrom(error, reader.line));
    }
  }
  return { imported, issues, rowsRead: table.rows.length };
}

/** Read the pay less notices, which carry the reason they have to give. */
export function importPayLessNotices(
  text: string,
  options: PaymentImportOptions,
): ImportReport<PayLessNotice> {
  const table = parseCsv(text);
  const columnProblem = checkColumns<PayLessNotice>(table, PAY_LESS_COLUMNS);
  if (columnProblem !== null) return columnProblem;

  const imported: PayLessNotice[] = [];
  const issues: ImportIssue[] = [];
  const seen = new Set<string>();

  for (const row of table.rows) {
    const reader = readerFor(table, row);
    try {
      const notice: PayLessNotice = {
        applicationId: need(reader, 'application'),
        servedOn: needDate(reader, 'served on'),
        amount: needMoney(reader, 'amount', options.currency),
        reason: need(reader, 'reason'),
      };
      if (seen.has(notice.applicationId)) {
        issues.push(
          issue(
            `application ${notice.applicationId} has two pay less notices against it`,
            row.line,
            'application',
          ),
        );
        continue;
      }
      seen.add(notice.applicationId);
      imported.push(notice);
    } catch (error) {
      issues.push(issueFrom(error, reader.line));
    }
  }
  return { imported, issues, rowsRead: table.rows.length };
}
