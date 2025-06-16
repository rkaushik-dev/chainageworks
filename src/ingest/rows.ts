import type { RowReader } from '../io/csv.js';
import { parseCsv, readerFor } from '../io/csv.js';
import type { ImportIssue, ImportReport } from './report.js';
import { checkColumns, issue, issueFrom } from './report.js';

export interface RowImport<T> {
  /** The headings the file has to carry before any row is read. */
  readonly columns: readonly string[];
  readonly read: (reader: RowReader, issues: ImportIssue[]) => T;
  /** How a row names itself, when two rows carrying the same name is a mistake. */
  readonly keyOf?: (item: T) => string;
  /** What to say about the second row carrying a name already used. */
  readonly duplicate?: (key: string) => string;
  /** The column the duplicate complaint is filed against. */
  readonly keyColumn?: string;
}

/**
 * Read a csv into records, one row at a time.
 *
 * Every importer in this directory had the same loop written out by hand, and
 * they had already drifted: two of them stopped at the first bad row and the
 * rest carried on, which meant the same site file behaved differently
 * depending on which register it was going into.
 *
 * The rules the loop keeps are the ones a site file needs. A row that cannot be
 * read is left out with an issue against its line and the run continues, because
 * a survey book arrives three hundred rows at a time and two of them are always
 * wrong. A row that repeats a name already taken is left out rather than
 * overwriting, because an import that quietly replaced a record would change a
 * certificate that has already been issued.
 */
export function readTable<T>(text: string, spec: RowImport<T>): ImportReport<T> {
  const table = parseCsv(text);
  const columnProblem = checkColumns<T>(table, spec.columns);
  if (columnProblem !== null) return columnProblem;

  const imported: T[] = [];
  const issues: ImportIssue[] = [];
  const seen = new Set<string>();

  for (const row of table.rows) {
    const reader = readerFor(table, row);
    let item: T;
    try {
      item = spec.read(reader, issues);
    } catch (error) {
      issues.push(issueFrom(error, reader.line));
      continue;
    }
    if (spec.keyOf !== undefined) {
      const key = spec.keyOf(item);
      if (seen.has(key)) {
        const message =
          spec.duplicate === undefined ? `${key} appears twice` : spec.duplicate(key);
        issues.push(issue(message, row.line, spec.keyColumn ?? spec.columns[0] ?? 'row'));
        continue;
      }
      seen.add(key);
    }
    imported.push(item);
  }
  return { imported, issues, rowsRead: table.rows.length };
}

export interface GroupedImport<Row, T> {
  readonly columns: readonly string[];
  readonly read: (reader: RowReader, issues: ImportIssue[]) => Row;
  /** Which record a row belongs to. Rows sharing a key become one record. */
  readonly keyOf: (row: Row) => string;
  /**
   * Whether a later row agrees with the first row of its group. Return the
   * complaint when it does not, or null when it does.
   */
  readonly agrees?: (head: Row, row: Row) => string | null;
  readonly build: (key: string, head: Row, rows: readonly Row[]) => T;
  /** The column a grouping complaint is filed against. */
  readonly keyColumn?: string;
}

/**
 * Read a csv where several rows make one record.
 *
 * A resource return is one sheet with many gang and plant lines on it, and a
 * cost index is one series with many months in it. Site systems export both
 * flat, so the grouping has to happen somewhere, and doing it once here keeps
 * the two importers from drifting apart the way the row loops did.
 *
 * Groups come back in the order their first row appeared, not sorted, because
 * that order is the order the file was written in and it is what a person
 * reading the issues alongside the file expects.
 *
 * The report counts records rather than rows. A count of rows would say four
 * when two returns were loaded, and row level problems are still reported as
 * issues against their own line, so nothing is lost by counting the larger
 * thing.
 */
export function readGrouped<Row, T>(
  text: string,
  spec: GroupedImport<Row, T>,
): ImportReport<T> {
  const rows = readTable<Row>(text, { columns: spec.columns, read: spec.read });
  if (rows.imported.length === 0) {
    return { imported: [], issues: rows.issues, rowsRead: 0 };
  }

  const issues: ImportIssue[] = [...rows.issues];
  const order: string[] = [];
  const heads = new Map<string, Row>();
  const grouped = new Map<string, Row[]>();

  for (const row of rows.imported) {
    const key = spec.keyOf(row);
    const head = heads.get(key);
    if (head === undefined) {
      order.push(key);
      heads.set(key, row);
      grouped.set(key, []);
    } else {
      const complaint = spec.agrees?.(head, row) ?? null;
      if (complaint !== null) {
        issues.push(issue(complaint, null, spec.keyColumn ?? spec.columns[0] ?? 'row'));
        continue;
      }
    }
    grouped.get(key)?.push(row);
  }

  const imported: T[] = [];
  for (const key of order) {
    const head = heads.get(key);
    if (head === undefined) continue;
    try {
      imported.push(spec.build(key, head, grouped.get(key) ?? []));
    } catch (error) {
      issues.push(
        issue(
          error instanceof Error ? error.message : String(error),
          null,
          spec.keyColumn ?? spec.columns[0] ?? 'row',
        ),
      );
    }
  }
  return { imported, issues, rowsRead: order.length };
}
