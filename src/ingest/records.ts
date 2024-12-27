import type { MeasurementMethod, SurveyRecord } from '../domain/survey/record.js';
import { buildSurveyRecord } from '../domain/survey/record.js';
import type { RowReader } from '../io/csv.js';
import { parseCsv, readerFor } from '../io/csv.js';
import { need, needCode, needDate, needExtent, needQuantity, optional } from './fields.js';
import type { ImportIssue, ImportReport } from './report.js';
import { checkColumns, issue, issueFrom } from './report.js';

export const RECORD_COLUMNS = [
  'record',
  'package',
  'code',
  'step',
  'from',
  'to',
  'quantity',
  'unit',
  'measured on',
  'booked by',
] as const;

const METHODS: readonly MeasurementMethod[] = [
  'tape',
  'total_station',
  'gps',
  'weighbridge',
  'count',
  'drawing',
];

/**
 * Read a survey book export.
 *
 * Every row is read on its own and a bad one is left out with an issue rather
 * than stopping the run. A site sends three hundred rows at a time and two of
 * them are always wrong; failing the whole file means the other two hundred and
 * ninety eight wait a day.
 */
export function importSurveyRecords(text: string): ImportReport<SurveyRecord> {
  const table = parseCsv(text);
  const columnProblem = checkColumns<SurveyRecord>(table, RECORD_COLUMNS);
  if (columnProblem !== null) return columnProblem;

  const imported: SurveyRecord[] = [];
  const issues: ImportIssue[] = [];
  const seen = new Set<string>();

  for (const row of table.rows) {
    const reader = readerFor(table, row);
    let record: SurveyRecord;
    try {
      record = readRow(reader, issues);
    } catch (error) {
      issues.push(issueFrom(error, reader.line));
      continue;
    }
    if (seen.has(record.id)) {
      issues.push(issue(`record ${record.id} appears twice`, row.line, 'record'));
      continue;
    }
    seen.add(record.id);
    imported.push(record);
  }
  return { imported, issues, rowsRead: table.rows.length };
}

function readRow(reader: RowReader, issues: ImportIssue[]): SurveyRecord {
  const supersedes = optional(reader, 'supersedes');
  const note = optional(reader, 'note');
  return buildSurveyRecord({
    id: need(reader, 'record'),
    packageId: need(reader, 'package'),
    code: needCode(reader, 'code'),
    stepId: need(reader, 'step'),
    extent: needExtent(reader),
    quantity: needQuantity(reader, 'quantity', 'unit'),
    measuredOn: needDate(reader, 'measured on'),
    bookedBy: need(reader, 'booked by'),
    method: readMethod(reader, issues),
    ...(supersedes === null ? {} : { supersedes }),
    ...(note === null ? {} : { note }),
  });
}

/**
 * An unrecognised method is a warning, not a rejection. The quantity is still
 * the quantity, and the method only matters when somebody audits the row.
 */
function readMethod(reader: RowReader, issues: ImportIssue[]): MeasurementMethod {
  const text = optional(reader, 'method');
  if (text === null) return 'tape';
  const cleaned = text
    .trim()
    .toLowerCase()
    .replace(/[\s-]+/g, '_');
  const found = METHODS.find((m) => m === cleaned);
  if (found === undefined) {
    issues.push(
      issue(
        `method ${text} was not recognised, taking a tape`,
        reader.line,
        'method',
        'warning',
      ),
    );
    return 'tape';
  }
  return found;
}
