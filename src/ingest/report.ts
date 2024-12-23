import { CwError } from '../errors.js';
import type { CsvTable } from '../io/csv.js';
import { missingColumns } from '../io/csv.js';

export type IssueSeverity = 'error' | 'warning';

/**
 * Something wrong with one row, or with the file as a whole.
 *
 * A warning is a row that was taken and is worth a look. An error is a row that
 * was left out. Both carry the line so the surveyor can go and fix the sheet
 * rather than guess.
 */
export interface ImportIssue {
  /** Null when the problem is with the file rather than a row. */
  readonly line: number | null;
  readonly column: string | null;
  readonly message: string;
  readonly severity: IssueSeverity;
}

export interface ImportReport<T> {
  readonly imported: readonly T[];
  readonly issues: readonly ImportIssue[];
  readonly rowsRead: number;
}

export function issue(
  message: string,
  line: number | null = null,
  column: string | null = null,
  severity: IssueSeverity = 'error',
): ImportIssue {
  return { line, column, message, severity };
}

/** Turn a thrown or returned error into an issue, keeping its line if it has one. */
export function issueFrom(error: unknown, line: number | null = null): ImportIssue {
  if (error instanceof CwError) {
    const details = error.details;
    const fromDetails = typeof details['line'] === 'number' ? details['line'] : null;
    const column = typeof details['column'] === 'string' ? details['column'] : null;
    return {
      line: fromDetails ?? line,
      column,
      message: error.message,
      severity: 'error',
    };
  }
  return issue(String(error), line);
}

export function emptyReport<T>(): ImportReport<T> {
  return { imported: [], issues: [], rowsRead: 0 };
}

export function errors<T>(report: ImportReport<T>): ImportIssue[] {
  return report.issues.filter((i) => i.severity === 'error');
}

export function warnings<T>(report: ImportReport<T>): ImportIssue[] {
  return report.issues.filter((i) => i.severity === 'warning');
}

export function hasErrors<T>(report: ImportReport<T>): boolean {
  return report.issues.some((i) => i.severity === 'error');
}

export function rowsAccepted<T>(report: ImportReport<T>): number {
  return report.imported.length;
}

export function rowsRejected<T>(report: ImportReport<T>): number {
  return report.rowsRead - report.imported.length;
}

/**
 * Check the headings before reading a row.
 *
 * A file with the wrong columns produces one issue rather than one per row,
 * which is the difference between a report somebody reads and four hundred
 * lines of the same complaint.
 */
export function checkColumns<T>(
  table: CsvTable,
  required: readonly string[],
): ImportReport<T> | null {
  const missing = missingColumns(table, required);
  if (missing.length === 0) return null;
  return {
    imported: [],
    issues: missing.map((column) => issue(`the file has no ${column} column`, null, column)),
    rowsRead: table.rows.length,
  };
}

export function mergeReports<T>(reports: readonly ImportReport<T>[]): ImportReport<T> {
  return {
    imported: reports.flatMap((r) => [...r.imported]),
    issues: reports.flatMap((r) => [...r.issues]),
    rowsRead: reports.reduce((total, r) => total + r.rowsRead, 0),
  };
}

/** A plain text summary, which is what the CLI prints and the API returns. */
export function formatImportReport<T>(report: ImportReport<T>, what = 'rows'): string {
  const lines: string[] = [
    `read ${String(report.rowsRead)} ${what}, took ${String(rowsAccepted(report))}, ` +
      `left out ${String(rowsRejected(report))}`,
  ];
  for (const problem of report.issues) {
    const where = problem.line === null ? 'file' : `line ${String(problem.line)}`;
    const column = problem.column === null ? '' : ` (${problem.column})`;
    lines.push(`  ${problem.severity}: ${where}${column}: ${problem.message}`);
  }
  return lines.join('\n');
}
