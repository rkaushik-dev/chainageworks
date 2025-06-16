import type { IndexReading, IndexSeries } from '../domain/fluctuation/index.js';
import { buildIndexSeries } from '../domain/fluctuation/index.js';
import { need, needFlag, needNumber, optional } from './fields.js';
import type { ImportReport } from './report.js';
import { readGrouped } from './rows.js';

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
  return readGrouped<IndexRow, IndexSeries>(text, {
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
    keyOf: (row) => row.seriesId,
    keyColumn: 'index',
    build: (id, head, rows) =>
      buildIndexSeries({
        id,
        ...(head.name === '' ? {} : { name: head.name }),
        readings: rows.map((row) => row.reading),
      }),
  });
}
