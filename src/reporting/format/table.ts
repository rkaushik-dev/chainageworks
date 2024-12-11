import { BadInputError } from '../../errors.js';

export type Align = 'left' | 'right';

export interface Column {
  readonly heading: string;
  readonly align?: Align;
  /** Cut anything longer than this, with an ellipsis. */
  readonly width?: number;
}

export interface TableOptions {
  /** Draw a rule under the headings. On by default. */
  readonly rule?: boolean;
  /** Spaces between columns. Two by default. */
  readonly gap?: number;
}

/**
 * A fixed width table for a terminal and for a plain text report.
 *
 * Money and quantities are right aligned because that is the only way a column
 * of figures can be read down, and the widths come from the content rather than
 * being declared, so a long package name does not silently lose its tail.
 */
export function renderTable(
  columns: readonly Column[],
  rows: readonly (readonly string[])[],
  options: TableOptions = {},
): string {
  if (columns.length === 0) {
    throw new BadInputError('a table needs at least one column');
  }
  for (const [index, row] of rows.entries()) {
    if (row.length !== columns.length) {
      throw new BadInputError('a table row has the wrong number of cells', {
        row: index,
        cells: row.length,
        columns: columns.length,
      });
    }
  }
  const gap = options.gap ?? 2;
  const cells = rows.map((row) => row.map((cell, i) => clip(cell, columns[i]?.width)));
  const headings = columns.map((c) => clip(c.heading, c.width));
  const widths = columns.map((_, i) =>
    Math.max(headings[i]?.length ?? 0, ...cells.map((row) => row[i]?.length ?? 0)),
  );

  const lines: string[] = [pad(headings, widths, columns, gap)];
  if (options.rule !== false) {
    lines.push(widths.map((w) => '-'.repeat(w)).join(' '.repeat(gap)));
  }
  for (const row of cells) {
    lines.push(pad(row, widths, columns, gap));
  }
  return lines.join('\n');
}

function pad(
  row: readonly string[],
  widths: readonly number[],
  columns: readonly Column[],
  gap: number,
): string {
  return row
    .map((cell, i) => {
      const width = widths[i] ?? cell.length;
      return columns[i]?.align === 'right' ? cell.padStart(width) : cell.padEnd(width);
    })
    .join(' '.repeat(gap))
    .trimEnd();
}

function clip(text: string, width: number | undefined): string {
  if (width === undefined || text.length <= width) return text;
  if (width <= 1) return text.slice(0, width);
  return `${text.slice(0, width - 1)}…`;
}

/** A heading with a rule under it, for the top of a report section. */
export function renderHeading(text: string, underline = '='): string {
  return `${text}\n${underline.repeat(text.length)}`;
}

/** Label and value pairs, aligned on the colon. */
export function renderPairs(pairs: readonly (readonly [string, string])[]): string {
  const width = Math.max(0, ...pairs.map(([label]) => label.length));
  return pairs.map(([label, value]) => `${label.padEnd(width)}  ${value}`).join('\n');
}

export function indent(text: string, spaces = 2): string {
  const prefix = ' '.repeat(spaces);
  return text
    .split('\n')
    .map((line) => (line === '' ? line : prefix + line))
    .join('\n');
}
