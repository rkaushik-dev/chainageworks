import type { IndexReading, IndexSeries } from '../domain/fluctuation/index.js';
import { buildIndexSeries } from '../domain/fluctuation/index.js';
import { need, needFlag, needNumber, optional } from './fields.js';
import type { ImportIssue, ImportReport } from './report.js';
import { issue } from './report.js';
import { readTable } from './rows.js';

export const INDEX_COLUMNS = ['index', 'year', 'month', 'value'] as const;

interface IndexRow {
  readonly seriesId: string;
  readonly name: string;
  readonly reading: IndexReading;
}

/**
 * Read a published index bulletin, one month to a row.
 *
 * The provisional column defaults to firm, which is the safe way round. A
 * figure wrongly held provisional only causes a certificate to be revisited
 * unnecessarily, while a provisional figure wrongly taken as firm closes an
 * account on a number that is still going to move.
 */
export function importIndexSeries(text: string): ImportReport<IndexSeries> {
  const rows = readTable<IndexRow>(text, {
    columns: INDEX_COLUMNS,
    read: (reader) => ({
      seriesId: need(reader, 'index'),
      name: optional(reader, 'name') ?? '',
      reading: {
        year: needNumber(reader, 'year'),
        month: needNumber(reader, 'month'),
        value: needNumber(reader, 'value'),
        provisional: needFlag(reader, 'provisional', false),
      },
    }),
  });

  const issues: ImportIssue[] = [...rows.issues];
  const order: string[] = [];
  const names = new Map<string, string>();
  const readings = new Map<string, IndexReading[]>();

  for (const row of rows.imported) {
    if (!readings.has(row.seriesId)) {
      order.push(row.seriesId);
      readings.set(row.seriesId, []);
      names.set(row.seriesId, row.name);
    }
    readings.get(row.seriesId)?.push(row.reading);
  }

  const imported: IndexSeries[] = [];
  for (const id of order) {
    try {
      const name = names.get(id) ?? '';
      imported.push(
        buildIndexSeries({
          id,
          ...(name === '' ? {} : { name }),
          readings: readings.get(id) ?? [],
        }),
      );
    } catch (error) {
      issues.push(issue(error instanceof Error ? error.message : String(error), null, 'index'));
    }
  }
  return { imported, issues, rowsRead: order.length };
}
