import type { Bill } from '../domain/bill/bill.js';
import { buildBill } from '../domain/bill/bill.js';
import type { BillItem, BillItemKind } from '../domain/bill/billItem.js';
import { buildBillItem } from '../domain/bill/billItem.js';
import { formatItemCode } from '../domain/bill/itemCode.js';
import { formatStation } from '../domain/chainage/station.js';
import type { CurrencyCode } from '../domain/money/money.js';
import { rateFromMoney, rateMajor } from '../domain/money/rate.js';
import { quantityAmount } from '../domain/quantity/quantity.js';
import type { RowReader } from '../io/csv.js';
import { parseCsv, readerFor, writeCsv } from '../io/csv.js';
import { need, needCode, needMoney, needOneOf, needQuantity, optional } from './fields.js';
import { needStation } from './fields.js';
import type { ImportIssue, ImportReport } from './report.js';
import { checkColumns, issue, issueFrom } from './report.js';

export const BILL_COLUMNS = ['code', 'description', 'unit', 'rate', 'quantity'] as const;

const KINDS: readonly BillItemKind[] = [
  'measured',
  'provisional',
  'prime_cost',
  'lump',
  'star',
];

export interface ImportBillOptions {
  readonly id?: string;
  readonly title?: string;
  readonly currency?: CurrencyCode;
}

/**
 * Read a priced bill export.
 *
 * The kind column decides whether the money is earnable, so a sheet that omits
 * it gets `measured` for everything except a sum, which gets `lump`. That
 * default is the safe one: it never turns provisional money into claimable
 * money by accident, because a bill that carries provisional items and does not
 * say so is a bill that has already lost the distinction.
 */
export function importBillItems(
  text: string,
  options: ImportBillOptions = {},
): ImportReport<BillItem> {
  const table = parseCsv(text);
  const columnProblem = checkColumns<BillItem>(table, BILL_COLUMNS);
  if (columnProblem !== null) return columnProblem;

  const currency = options.currency ?? 'GBP';
  const imported: BillItem[] = [];
  const issues: ImportIssue[] = [];
  const seen = new Set<string>();

  for (const row of table.rows) {
    const reader = readerFor(table, row);
    try {
      const item = readItem(reader, currency);
      const key = formatItemCode(item.code);
      if (seen.has(key)) {
        issues.push(issue(`item ${key} appears twice`, row.line, 'code'));
        continue;
      }
      seen.add(key);
      imported.push(item);
    } catch (error) {
      issues.push(issueFrom(error, reader.line));
    }
  }
  return { imported, issues, rowsRead: table.rows.length };
}

function readItem(reader: RowReader, currency: CurrencyCode): BillItem {
  const quantity = needQuantity(reader, 'quantity', 'unit');
  const kindText = optional(reader, 'kind');
  const kind =
    kindText === null
      ? quantity.unit === 'sum'
        ? 'lump'
        : 'measured'
      : needOneOf(reader, 'kind', KINDS);
  const from = optional(reader, 'from');
  const to = optional(reader, 'to');
  const chainage =
    from === null || to === null
      ? null
      : { from: needStation(reader, 'from'), to: needStation(reader, 'to') };
  return buildBillItem({
    code: needCode(reader, 'code'),
    description: need(reader, 'description'),
    rate: rateFromMoney(needMoney(reader, 'rate', currency), quantity.unit),
    quantity,
    kind,
    chainage,
  });
}

/** Read a whole bill in one go, when the file is expected to be clean. */
export function importBill(text: string, options: ImportBillOptions = {}): Bill {
  const report = importBillItems(text, options);
  const first = report.issues.find((i) => i.severity === 'error');
  if (first !== undefined) {
    throw new Error(
      first.line === null ? first.message : `line ${String(first.line)}: ${first.message}`,
    );
  }
  return buildBill({
    id: options.id ?? 'B1',
    title: options.title ?? 'bill of quantities',
    currency: options.currency ?? 'GBP',
    items: report.imported,
  });
}

export const BILL_EXPORT_COLUMNS = [
  'code',
  'description',
  'unit',
  'rate',
  'quantity',
  'kind',
  'from',
  'to',
] as const;

/** Write the bill back out in the shape the importer reads. */
export function exportBill(bill: Bill): string {
  return writeCsv(
    BILL_EXPORT_COLUMNS,
    bill.items.map((item) => [
      formatItemCode(item.code),
      item.description,
      item.unit,
      rateMajor(item.rate).toFixed(4),
      String(quantityAmount(item.quantity)),
      item.kind,
      item.chainage === null ? '' : formatStation(item.chainage.from),
      item.chainage === null ? '' : formatStation(item.chainage.to),
    ]),
  );
}
