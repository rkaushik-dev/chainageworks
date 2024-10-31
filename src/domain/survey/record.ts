import { BadInputError, ConflictError } from '../../errors.js';
import type { ItemCode } from '../bill/itemCode.js';
import { itemCodeKey } from '../bill/itemCode.js';
import type { ChainageRange } from '../chainage/range.js';
import type { Quantity } from '../quantity/quantity.js';
import { sumQuantities } from '../quantity/quantity.js';
import type { SiteDate } from '../time/siteDate.js';
import { compareSiteDates } from '../time/siteDate.js';

/** How the quantity was arrived at, which is what an audit asks first. */
export type MeasurementMethod =
  | 'tape'
  | 'total_station'
  | 'gps'
  | 'weighbridge'
  | 'count'
  | 'drawing';

/**
 * One measurement booked on site.
 *
 * Records are never edited. A remeasure is a new record that supersedes the old
 * one, and that is what makes a valuation reproducible: last month's figure can
 * still be read back exactly as it was certified, because the record that
 * replaced it is dated and can be left out.
 */
export interface SurveyRecord {
  readonly id: string;
  readonly packageId: string;
  readonly code: ItemCode;
  readonly stepId: string;
  readonly extent: ChainageRange;
  readonly quantity: Quantity;
  readonly measuredOn: SiteDate;
  readonly bookedBy: string;
  readonly method: MeasurementMethod;
  /** The record this one replaces, if it is a remeasure. */
  readonly supersedes: string | null;
  readonly note: string;
}

export interface SurveyRecordInput {
  readonly id: string;
  readonly packageId: string;
  readonly code: ItemCode;
  readonly stepId: string;
  readonly extent: ChainageRange;
  readonly quantity: Quantity;
  readonly measuredOn: SiteDate;
  readonly bookedBy: string;
  readonly method?: MeasurementMethod;
  readonly supersedes?: string | null;
  readonly note?: string;
}

export function buildSurveyRecord(input: SurveyRecordInput): SurveyRecord {
  if (input.id.trim() === '') {
    throw new BadInputError('a survey record needs an id');
  }
  if (input.bookedBy.trim() === '') {
    throw new BadInputError('a survey record has to name who booked it', {
      record: input.id,
    });
  }
  if (input.supersedes === input.id) {
    throw new BadInputError('a survey record cannot supersede itself', { record: input.id });
  }
  return {
    id: input.id,
    packageId: input.packageId,
    code: input.code,
    stepId: input.stepId,
    extent: input.extent,
    quantity: input.quantity,
    measuredOn: input.measuredOn,
    bookedBy: input.bookedBy.trim(),
    method: input.method ?? 'tape',
    supersedes: input.supersedes ?? null,
    note: input.note ?? '',
  };
}

export function indexRecords(
  records: readonly SurveyRecord[],
): ReadonlyMap<string, SurveyRecord> {
  const map = new Map<string, SurveyRecord>();
  for (const record of records) {
    if (map.has(record.id)) {
      throw new BadInputError('two survey records share an id', { record: record.id });
    }
    map.set(record.id, record);
  }
  return map;
}

/**
 * Walk back through the remeasures to the first booking. Throws on a loop,
 * because a loop makes every quantity on that chain unanswerable rather than
 * merely wrong.
 */
export function supersessionChain(
  records: readonly SurveyRecord[],
  id: string,
): SurveyRecord[] {
  const byId = indexRecords(records);
  const chain: SurveyRecord[] = [];
  const seen = new Set<string>();
  let cursor: string | null = id;
  while (cursor !== null) {
    if (seen.has(cursor)) {
      throw new ConflictError('the supersession chain loops', { record: cursor });
    }
    seen.add(cursor);
    const record: SurveyRecord | undefined = byId.get(cursor);
    if (record === undefined) break;
    chain.push(record);
    cursor = record.supersedes;
  }
  return chain;
}

/** More than one remeasure claiming to replace the same record. */
export function findDoubleSupersessions(records: readonly SurveyRecord[]): string[] {
  const counts = new Map<string, number>();
  for (const record of records) {
    if (record.supersedes === null) continue;
    counts.set(record.supersedes, (counts.get(record.supersedes) ?? 0) + 1);
  }
  return [...counts.entries()].filter(([, n]) => n > 1).map(([id]) => id);
}

/**
 * The records that count on a date.
 *
 * A record measured after the date is not there yet, and, more subtly, a record
 * superseded by a remeasure that is itself after the date is still the live one.
 * That second rule is what stops a remeasure in May from changing what March
 * certified.
 */
export function effectiveRecords(
  records: readonly SurveyRecord[],
  asAt: SiteDate,
): SurveyRecord[] {
  const inDate = records.filter((r) => compareSiteDates(r.measuredOn, asAt) <= 0);
  const supersededIds = new Set(
    inDate.map((r) => r.supersedes).filter((id): id is string => id !== null),
  );
  return inDate.filter((r) => !supersededIds.has(r.id));
}

export interface RecordQuery {
  readonly packageId?: string;
  readonly code?: ItemCode;
  readonly stepId?: string;
}

export function matchesQuery(record: SurveyRecord, query: RecordQuery): boolean {
  if (query.packageId !== undefined && record.packageId !== query.packageId) return false;
  if (query.stepId !== undefined && record.stepId !== query.stepId) return false;
  if (query.code !== undefined && itemCodeKey(record.code) !== itemCodeKey(query.code)) {
    return false;
  }
  return true;
}

export function selectRecords(
  records: readonly SurveyRecord[],
  asAt: SiteDate,
  query: RecordQuery = {},
): SurveyRecord[] {
  return effectiveRecords(records, asAt)
    .filter((r) => matchesQuery(r, query))
    .sort((a, b) => compareSiteDates(a.measuredOn, b.measuredOn) || (a.id < b.id ? -1 : 1));
}

/** Everything booked against a query, added up. Null when nothing matched. */
export function measuredQuantity(
  records: readonly SurveyRecord[],
  asAt: SiteDate,
  query: RecordQuery = {},
): Quantity | null {
  const selected = selectRecords(records, asAt, query);
  const first = selected[0];
  if (first === undefined) return null;
  return sumQuantities(
    selected.map((r) => r.quantity),
    first.quantity.unit,
  );
}

export function isRemeasure(record: SurveyRecord): boolean {
  return record.supersedes !== null;
}
