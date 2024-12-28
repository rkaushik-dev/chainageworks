import type { Standdown, StanddownReason } from '../domain/time/calendar.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import type { RowReader } from '../io/csv.js';
import { parseCsv, readerFor } from '../io/csv.js';
import { need, needDate, needOneOf, optional } from './fields.js';
import type { ImportIssue, ImportReport } from './report.js';
import { checkColumns, issue, issueFrom } from './report.js';

export const DIARY_COLUMNS = ['date', 'reason'] as const;
export const HOLIDAY_COLUMNS = ['date'] as const;

const REASONS: readonly StanddownReason[] = [
  'weather',
  'ground_conditions',
  'access',
  'industrial',
  'other',
];

/** Site diary spellings that mean one of the reasons the calendar knows. */
const REASON_ALIASES: Readonly<Record<string, StanddownReason>> = {
  rain: 'weather',
  wet: 'weather',
  frost: 'weather',
  snow: 'weather',
  wind: 'weather',
  ground: 'ground_conditions',
  mud: 'ground_conditions',
  waterlogged: 'ground_conditions',
  no_access: 'access',
  blocked: 'access',
  strike: 'industrial',
  dispute: 'industrial',
};

/**
 * Read the standdown lines out of a site diary export.
 *
 * The reason column is free text on site, so the common spellings are mapped
 * before the strict check runs. Anything still unrecognised becomes `other`
 * with a warning rather than being dropped: the day was lost whatever the
 * clerk called it, and losing the day is what the delay analysis needs.
 */
export function importStanddowns(text: string): ImportReport<Standdown> {
  const table = parseCsv(text);
  const columnProblem = checkColumns<Standdown>(table, DIARY_COLUMNS);
  if (columnProblem !== null) return columnProblem;

  const imported: Standdown[] = [];
  const issues: ImportIssue[] = [];
  const seen = new Set<number>();

  for (const row of table.rows) {
    const reader = readerFor(table, row);
    try {
      const date = needDate(reader, 'date');
      if (seen.has(date)) {
        issues.push(
          issue(`${formatSiteDate(date)} is stood down twice`, row.line, 'date', 'warning'),
        );
      }
      seen.add(date);
      const note = optional(reader, 'note');
      imported.push({
        date,
        reason: readReason(reader, issues),
        ...(note === null ? {} : { note }),
      });
    } catch (error) {
      issues.push(issueFrom(error, reader.line));
    }
  }
  return { imported, issues, rowsRead: table.rows.length };
}

function readReason(reader: RowReader, issues: ImportIssue[]): StanddownReason {
  const text = need(reader, 'reason');
  const cleaned = text
    .trim()
    .toLowerCase()
    .replace(/[\s-]+/g, '_');
  const alias = REASON_ALIASES[cleaned];
  if (alias !== undefined) return alias;
  try {
    return needOneOf(reader, 'reason', REASONS);
  } catch {
    issues.push(
      issue(
        `reason ${text} was not recognised, filing it as other`,
        reader.line,
        'reason',
        'warning',
      ),
    );
    return 'other';
  }
}

/** Read the contract holiday list. One date a row, with an optional name. */
export function importHolidays(text: string): ImportReport<SiteDate> {
  const table = parseCsv(text);
  const columnProblem = checkColumns<SiteDate>(table, HOLIDAY_COLUMNS);
  if (columnProblem !== null) return columnProblem;

  const imported: SiteDate[] = [];
  const issues: ImportIssue[] = [];
  const seen = new Set<number>();

  for (const row of table.rows) {
    const reader = readerFor(table, row);
    try {
      const date = needDate(reader, 'date');
      if (seen.has(date)) {
        issues.push(
          issue(`${formatSiteDate(date)} is listed twice`, row.line, 'date', 'warning'),
        );
        continue;
      }
      seen.add(date);
      imported.push(date);
    } catch (error) {
      issues.push(issueFrom(error, reader.line));
    }
  }
  return { imported, issues, rowsRead: table.rows.length };
}
