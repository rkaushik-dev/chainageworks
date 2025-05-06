import type { HourKind, ResourceLine, ResourceReturn } from '../domain/resource/return.js';
import { buildResourceReturn, HOUR_KINDS } from '../domain/resource/return.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import { need, needDate, needNumber, needOneOf, optional } from './fields.js';
import type { ImportIssue, ImportReport } from './report.js';
import { issue } from './report.js';
import { readTable } from './rows.js';

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
  const rows = readTable<ResourceRow>(text, {
    columns: RESOURCE_COLUMNS,
    read: readRow,
  });
  if (rows.rowsRead === 0 || rows.imported.length === 0) {
    return { imported: [], issues: rows.issues, rowsRead: rows.rowsRead };
  }

  const issues: ImportIssue[] = [...rows.issues];
  const order: string[] = [];
  const heads = new Map<string, ResourceRow>();
  const labour = new Map<string, ResourceLine[]>();
  const plant = new Map<string, ResourceLine[]>();

  for (const row of rows.imported) {
    const head = heads.get(row.returnId);
    if (head === undefined) {
      order.push(row.returnId);
      heads.set(row.returnId, row);
      labour.set(row.returnId, []);
      plant.set(row.returnId, []);
    } else if (head.packageId !== row.packageId || head.date !== row.date) {
      issues.push(
        issue(
          `return ${row.returnId} is against ${head.packageId} on ` +
            `${formatSiteDate(head.date)} on one row and ` +
            `${row.packageId} on ${formatSiteDate(row.date)} on another`,
          null,
          'return',
        ),
      );
      continue;
    }
    (row.side === 'plant' ? plant : labour).get(row.returnId)?.push(row.line);
  }

  const imported: ResourceReturn[] = [];
  for (const id of order) {
    const head = heads.get(id);
    if (head === undefined) continue;
    try {
      imported.push(
        buildResourceReturn({
          id,
          packageId: head.packageId,
          stepId: head.stepId,
          date: head.date,
          shift: head.shift,
          labour: labour.get(id) ?? [],
          plant: plant.get(id) ?? [],
          returnedBy: head.returnedBy,
          note: head.note,
        }),
      );
    } catch (error) {
      issues.push(
        issue(error instanceof Error ? error.message : String(error), null, 'return'),
      );
    }
  }
  return { imported, issues, rowsRead: order.length };
}
