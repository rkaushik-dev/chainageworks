import { BadInputError } from '../errors.js';
import type { ItemCode } from '../domain/bill/itemCode.js';
import { tryParseItemCode } from '../domain/bill/itemCode.js';
import { chainageRange } from '../domain/chainage/range.js';
import type { Station } from '../domain/chainage/station.js';
import { tryParseStation } from '../domain/chainage/station.js';
import { quantity } from '../domain/quantity/quantity.js';
import { parseUnit } from '../domain/quantity/unit.js';
import type { MeasurementMethod, SurveyRecord } from '../domain/survey/record.js';
import { buildSurveyRecord } from '../domain/survey/record.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { tryParseSiteDate } from '../domain/time/siteDate.js';
import type { RowReader } from '../io/csv.js';
import { parseCsv, readerFor } from '../io/csv.js';
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

function need(reader: RowReader, column: string): string {
  const value = reader.require(column);
  if (!value.ok) throw value.error;
  return value.value;
}

function needCode(reader: RowReader, column: string): ItemCode {
  const parsed = tryParseItemCode(need(reader, column));
  if (!parsed.ok) {
    throw new BadInputError(parsed.error.message, { line: reader.line, column });
  }
  return parsed.value;
}

function needStation(reader: RowReader, column: string): Station {
  const parsed = tryParseStation(need(reader, column));
  if (!parsed.ok) {
    throw new BadInputError(parsed.error.message, { line: reader.line, column });
  }
  return parsed.value;
}

function needDate(reader: RowReader, column: string): SiteDate {
  const parsed = tryParseSiteDate(need(reader, column));
  if (!parsed.ok) {
    throw new BadInputError(parsed.error.message, { line: reader.line, column });
  }
  return parsed.value;
}

function needNumber(reader: RowReader, column: string): number {
  const value = Number(need(reader, column).replace(/,/g, ''));
  if (!Number.isFinite(value)) {
    throw new BadInputError(`the ${column} is not a number`, { line: reader.line, column });
  }
  return value;
}

function readRow(reader: RowReader, issues: ImportIssue[]): SurveyRecord {
  const supersedes = reader.get('supersedes');
  const note = reader.get('note');
  return buildSurveyRecord({
    id: need(reader, 'record'),
    packageId: need(reader, 'package'),
    code: needCode(reader, 'code'),
    stepId: need(reader, 'step'),
    extent: chainageRange(needStation(reader, 'from'), needStation(reader, 'to')),
    quantity: quantity(needNumber(reader, 'quantity'), parseUnit(need(reader, 'unit'))),
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
  const text = reader.get('method');
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
