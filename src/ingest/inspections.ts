import type { Ncr, NcrStatus } from '../domain/itp/ncr.js';
import { buildNcr } from '../domain/itp/ncr.js';
import type { SignOff, SignOffOutcome } from '../domain/itp/signOff.js';
import { buildSignOff } from '../domain/itp/signOff.js';
import type { RowReader } from '../io/csv.js';
import {
  need,
  needDate,
  needExtent,
  needFlag,
  needOneOf,
  optional,
  optionalDate,
} from './fields.js';
import type { ImportReport } from './report.js';
import { readTable } from './rows.js';

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
  return readTable(text, {
    columns: SIGN_OFF_COLUMNS,
    read: readSignOff,
    keyOf: (signOff) => signOff.id,
    duplicate: (id) => `sign off ${id} appears twice`,
    keyColumn: 'sign off',
  });
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
  return readTable(text, {
    columns: NCR_COLUMNS,
    read: readNcr,
    keyOf: (ncr) => ncr.id,
    duplicate: (id) => `report ${id} appears twice`,
    keyColumn: 'ncr',
  });
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
