import { BadInputError } from '../errors.js';
import type { Result } from '../result.js';
import { err, ok } from '../result.js';

/**
 * A cell, with where it came from.
 *
 * Site paperwork arrives as a spreadsheet export and it is always a bit wrong.
 * Carrying the line and column through the parser is what lets an import report
 * say "line 47, column 'quantity'" instead of "invalid input", which is the
 * difference between a surveyor fixing it and a surveyor emailing about it.
 */
export interface CsvRow {
  /** One based, counting the header as line 1. */
  readonly line: number;
  readonly values: readonly string[];
}

export interface CsvTable {
  readonly headings: readonly string[];
  readonly rows: readonly CsvRow[];
}

export interface ParseCsvOptions {
  readonly delimiter?: string;
  /** Treat the first line as headings. On by default. */
  readonly header?: boolean;
  /** Drop rows that are entirely empty. On by default. */
  readonly skipBlank?: boolean;
}

/**
 * A small RFC 4180 reader.
 *
 * Written rather than taken off the shelf because the job needs the line
 * numbers and needs to keep going after a bad row, and because the quoting
 * rules are the only genuinely tricky part: a quoted field may hold the
 * delimiter, a newline, and a doubled quote standing for one.
 */
export function parseCsv(text: string, options: ParseCsvOptions = {}): CsvTable {
  const delimiter = options.delimiter ?? ',';
  if (delimiter.length !== 1) {
    throw new BadInputError('a csv delimiter is one character', { delimiter });
  }
  const withHeader = options.header ?? true;
  const skipBlank = options.skipBlank ?? true;

  const lines: string[][] = [];
  let field = '';
  let row: string[] = [];
  let quoted = false;
  let i = 0;
  const body = text.replace(/^\uFEFF/, '');

  while (i < body.length) {
    const ch = body[i] ?? '';
    if (quoted) {
      if (ch === '"') {
        if (body[i + 1] === '"') {
          field += '"';
          i += 2;
          continue;
        }
        quoted = false;
        i += 1;
        continue;
      }
      field += ch;
      i += 1;
      continue;
    }
    if (ch === '"' && field === '') {
      quoted = true;
      i += 1;
      continue;
    }
    if (ch === delimiter) {
      row.push(field);
      field = '';
      i += 1;
      continue;
    }
    if (ch === '\r') {
      i += 1;
      continue;
    }
    if (ch === '\n') {
      row.push(field);
      lines.push(row);
      field = '';
      row = [];
      i += 1;
      continue;
    }
    field += ch;
    i += 1;
  }
  if (quoted) {
    throw new BadInputError('the file ends inside a quoted field');
  }
  if (field !== '' || row.length > 0) {
    row.push(field);
    lines.push(row);
  }

  const trimmed = lines.map((cells) => cells.map((cell) => cell.trim()));
  const headings = withHeader ? (trimmed.shift() ?? []) : [];
  const offset = withHeader ? 2 : 1;
  const rows: CsvRow[] = [];
  for (const [index, values] of trimmed.entries()) {
    if (skipBlank && values.every((v) => v === '')) continue;
    rows.push({ line: index + offset, values });
  }
  return { headings, rows };
}

export interface RowReader {
  readonly line: number;
  /** The value under a heading, or null when the column is missing or blank. */
  get(column: string): string | null;
  /** The value under a heading, failing when it is missing or blank. */
  require(column: string): Result<string>;
  readonly raw: readonly string[];
}

/** Bind a row to its headings so fields are read by name rather than position. */
export function readerFor(table: CsvTable, row: CsvRow): RowReader {
  const index = new Map<string, number>();
  for (const [i, heading] of table.headings.entries()) {
    index.set(heading.toLowerCase(), i);
  }
  const get = (column: string): string | null => {
    const at = index.get(column.toLowerCase());
    if (at === undefined) return null;
    const value = row.values[at];
    return value === undefined || value === '' ? null : value;
  };
  return {
    line: row.line,
    raw: row.values,
    get,
    require(column: string): Result<string> {
      const value = get(column);
      if (value === null) {
        return err(
          new BadInputError(`line ${String(row.line)}: ${column} is missing`, {
            line: row.line,
            column,
          }),
        );
      }
      return ok(value);
    },
  };
}

/** Headings the table needs and does not have. */
export function missingColumns(table: CsvTable, required: readonly string[]): string[] {
  const have = new Set(table.headings.map((h) => h.toLowerCase()));
  return required.filter((column) => !have.has(column.toLowerCase()));
}

export interface WriteCsvOptions {
  readonly delimiter?: string;
  readonly eol?: string;
}

export function writeCsv(
  headings: readonly string[],
  rows: readonly (readonly string[])[],
  options: WriteCsvOptions = {},
): string {
  const delimiter = options.delimiter ?? ',';
  const eol = options.eol ?? '\n';
  const lines = [headings, ...rows].map((row) =>
    row.map((cell) => quoteCell(cell, delimiter)).join(delimiter),
  );
  return lines.join(eol) + eol;
}

function quoteCell(cell: string, delimiter: string): string {
  if (
    cell.includes(delimiter) ||
    cell.includes('"') ||
    cell.includes('\n') ||
    cell.includes('\r')
  ) {
    return `"${cell.replace(/"/g, '""')}"`;
  }
  return cell;
}
