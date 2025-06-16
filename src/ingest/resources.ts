import type { HourKind, ResourceLine, ResourceReturn } from '../domain/resource/return.js';
import { buildResourceReturn, HOUR_KINDS } from '../domain/resource/return.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import { need, needDate, needNumber, needOneOf, optional } from './fields.js';
import type { ImportReport } from './report.js';
import { readGrouped } from './rows.js';

export const RESOURCE_COLUMNS = [
  'return',
  'package',
  'date',
  'resource',
  'kind',
  'count',
  'hours',
  'returned by',
] as const;

type Side = 'labour' | 'plant';

interface ResourceRow {
  readonly returnId: string;
  readonly packageId: string;
  readonly stepId: string | null;
  readonly date: SiteDate;
  readonly shift: 'day' | 'night';
  readonly side: Side;
  readonly line: ResourceLine;
  readonly returnedBy: string;
  readonly note: string;
}

function readRow(reader: Parameters<typeof need>[0]): ResourceRow {
  const step = optional(reader, 'step');
  const shift = optional(reader, 'shift');
  return {
    returnId: need(reader, 'return'),
    packageId: need(reader, 'package'),
    stepId: step,
    date: needDate(reader, 'date'),
    shift: shift === 'night' ? 'night' : 'day',
    side: need(reader, 'kind').trim().toLowerCase() === 'plant' ? 'plant' : 'labour',
    line: {
      resourceId: need(reader, 'resource'),
      description: optional(reader, 'description') ?? '',
      count: needNumber(reader, 'count'),
      hours: needNumber(reader, 'hours'),
      kind: readHourKind(reader),
    },
    returnedBy: need(reader, 'returned by'),
    note: optional(reader, 'note') ?? '',
  };
}

/**
 * An hours column nobody filled in means the hours were worked.
 *
 * That is the right way round for a site return. A foreman writes "standing"
 * or "idle" precisely because those hours are unusual and somebody will ask
 * about them, and leaves the column alone on an ordinary day.
 */
function readHourKind(reader: Parameters<typeof need>[0]): HourKind {
  return optional(reader, 'hour kind') === null
    ? 'working'
    : needOneOf(reader, 'hour kind', HOUR_KINDS);
}

/**
 * Read a resource return export, one resource line to a row.
 *
 * Rows are grouped back into returns by the return reference, because that is
 * the shape a return actually has: one sheet, one day, one package, and as
 * many gang and plant lines as the foreman wrote on it. Site systems export
 * the flat version, so the grouping has to happen somewhere, and doing it here
 * keeps every reader downstream working on whole returns.
 *
 * A row whose grouping fields disagree with the first row of the same return
 * is rejected on its own rather than poisoning the return, because the usual
 * cause is one line pasted under the wrong heading.
 *
 * The report counts returns rather than rows, because a return is what the
 * caller gets back and a count of rows would say four when two returns were
 * loaded. Row level problems are still reported as issues against their own
 * line, so nothing is lost by counting the larger thing.
 */
export function importResourceReturns(text: string): ImportReport<ResourceReturn> {
  return readGrouped<ResourceRow, ResourceReturn>(text, {
    columns: RESOURCE_COLUMNS,
    read: readRow,
    keyOf: (row) => row.returnId,
    keyColumn: 'return',
    agrees: (head, row) =>
      head.packageId === row.packageId && head.date === row.date
        ? null
        : `return ${row.returnId} is against ${head.packageId} on ` +
          `${formatSiteDate(head.date)} on one row and ` +
          `${row.packageId} on ${formatSiteDate(row.date)} on another`,
    build: (id, head, rows) =>
      buildResourceReturn({
        id,
        packageId: head.packageId,
        stepId: head.stepId,
        date: head.date,
        shift: head.shift,
        labour: rows.filter((row) => row.side === 'labour').map((row) => row.line),
        plant: rows.filter((row) => row.side === 'plant').map((row) => row.line),
        returnedBy: head.returnedBy,
        note: head.note,
      }),
  });
}
