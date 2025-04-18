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
