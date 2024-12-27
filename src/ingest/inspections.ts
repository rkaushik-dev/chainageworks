import type { Ncr, NcrStatus } from '../domain/itp/ncr.js';
import { buildNcr } from '../domain/itp/ncr.js';
import type { SignOff, SignOffOutcome } from '../domain/itp/signOff.js';
import { buildSignOff } from '../domain/itp/signOff.js';
import type { RowReader } from '../io/csv.js';
import { parseCsv, readerFor } from '../io/csv.js';
import {
  need,
  needDate,
  needExtent,
  needFlag,
  needOneOf,
  optional,
  optionalDate,
} from './fields.js';
import type { ImportIssue, ImportReport } from './report.js';
import { checkColumns, issue, issueFrom } from './report.js';

export const SIGN_OFF_COLUMNS = [
  'sign off',
  'point',
  'package',
  'from',
  'to',
  'signed on',
  'by',
] as const;

export const NCR_COLUMNS = [
  'ncr',
  'package',
  'from',
  'to',
  'raised on',
  'description',
] as const;

const OUTCOMES: readonly SignOffOutcome[] = ['passed', 'passed_with_comment', 'failed'];
const STATUSES: readonly NcrStatus[] = ['open', 'closed', 'accepted'];

/** Read an inspection register export. */
export function importSignOffs(text: string): ImportReport<SignOff> {
  const table = parseCsv(text);
  const columnProblem = checkColumns<SignOff>(table, SIGN_OFF_COLUMNS);
  if (columnProblem !== null) return columnProblem;

  const imported: SignOff[] = [];
  const issues: ImportIssue[] = [];
  const seen = new Set<string>();

  for (const row of table.rows) {
    const reader = readerFor(table, row);
    try {
      const signOff = readSignOff(reader);
      if (seen.has(signOff.id)) {
        issues.push(issue(`sign off ${signOff.id} appears twice`, row.line, 'sign off'));
        continue;
      }
      seen.add(signOff.id);
      imported.push(signOff);
    } catch (error) {
      issues.push(issueFrom(error, reader.line));
    }
  }
  return { imported, issues, rowsRead: table.rows.length };
}

function readSignOff(reader: RowReader): SignOff {
  const note = optional(reader, 'note');
  const outcome = optional(reader, 'outcome');
  return buildSignOff({
    id: need(reader, 'sign off'),
    pointId: need(reader, 'point'),
    packageId: need(reader, 'package'),
    extent: needExtent(reader),
    signedOn: needDate(reader, 'signed on'),
    by: need(reader, 'by'),
    ...(outcome === null ? {} : { outcome: needOneOf(reader, 'outcome', OUTCOMES) }),
    ...(note === null ? {} : { note }),
  });
}

/** Read a non conformance register export. */
export function importNcrs(text: string): ImportReport<Ncr> {
  const table = parseCsv(text);
  const columnProblem = checkColumns<Ncr>(table, NCR_COLUMNS);
  if (columnProblem !== null) return columnProblem;

  const imported: Ncr[] = [];
  const issues: ImportIssue[] = [];
  const seen = new Set<string>();

  for (const row of table.rows) {
    const reader = readerFor(table, row);
    try {
      const ncr = readNcr(reader);
      if (seen.has(ncr.id)) {
        issues.push(issue(`report ${ncr.id} appears twice`, row.line, 'ncr'));
        continue;
      }
      seen.add(ncr.id);
      imported.push(ncr);
    } catch (error) {
      issues.push(issueFrom(error, reader.line));
    }
  }
  return { imported, issues, rowsRead: table.rows.length };
}

function readNcr(reader: RowReader): Ncr {
  const status = optional(reader, 'status');
  const stepId = optional(reader, 'step');
  return buildNcr({
    id: need(reader, 'ncr'),
    packageId: need(reader, 'package'),
    extent: needExtent(reader),
    raisedOn: needDate(reader, 'raised on'),
    settledOn: optionalDate(reader, 'settled on'),
    description: need(reader, 'description'),
    withholdsCredit: needFlag(reader, 'withholds credit', true),
    ...(status === null ? {} : { status: needOneOf(reader, 'status', STATUSES) }),
    ...(stepId === null ? {} : { stepId }),
  });
}
