import { BadInputError } from '../../errors.js';
import type { Result } from '../../result.js';
import { err, ok } from '../../result.js';
import type { Station } from './station.js';
import {
  compareStations,
  formatStation,
  maxStation,
  minStation,
  tryParseStation,
} from './station.js';

/**
 * A stretch of the alignment, held half open as `[from, to)`.
 *
 * Half open is the only choice that makes the bill add up. Two abutting pipe
 * runs written `12+000 to 12+100` and `12+100 to 12+200` are 200 m of pipe, not
 * 200 m plus one shared metre, and a closed interval would have to special case
 * the join every time two claims are summed.
 */
export interface ChainageRange {
  readonly from: Station;
  readonly to: Station;
}

const RANGE_TEXT = /^(.+?)\s*(?:\bto\b|--|–|→)\s*(.+)$/i;

export function chainageRange(from: Station, to: Station): ChainageRange {
  if (compareStations(from, to) > 0) {
    throw new BadInputError('a chainage range runs forwards', {
      from: formatStation(from),
      to: formatStation(to),
    });
  }
  return { from, to };
}

/** `12+000 to 12+340`, `12+000 -- 12+340`. A single station gives a point. */
export function tryParseChainageRange(text: string): Result<ChainageRange> {
  const trimmed = text.trim();
  const m = RANGE_TEXT.exec(trimmed);
  if (m === null) {
    const single = tryParseStation(trimmed);
    if (!single.ok) return single;
    return ok({ from: single.value, to: single.value });
  }
  const from = tryParseStation(m[1] ?? '');
  if (!from.ok) return from;
  const to = tryParseStation(m[2] ?? '');
  if (!to.ok) return to;
  if (compareStations(from.value, to.value) > 0) {
    return err(
      new BadInputError('a chainage range runs forwards', {
        from: formatStation(from.value),
        to: formatStation(to.value),
      }),
    );
  }
  return ok({ from: from.value, to: to.value });
}

export function parseChainageRange(text: string): ChainageRange {
  const r = tryParseChainageRange(text);
  if (!r.ok) throw r.error;
  return r.value;
}

export function formatChainageRange(range: ChainageRange): string {
  if (isPointRange(range)) return formatStation(range.from);
  return `${formatStation(range.from)} to ${formatStation(range.to)}`;
}

/** Length in millimetres. A point measures zero. */
export function rangeLength(range: ChainageRange): number {
  return range.to - range.from;
}

export function isPointRange(range: ChainageRange): boolean {
  return range.from === range.to;
}

/** Half open, so the upper station is outside the range. */
export function rangeContains(range: ChainageRange, station: Station): boolean {
  return compareStations(station, range.from) >= 0 && compareStations(station, range.to) < 0;
}

/** Closed test, for asking whether a point sits on the end of a stretch. */
export function rangeTouches(range: ChainageRange, station: Station): boolean {
  return compareStations(station, range.from) >= 0 && compareStations(station, range.to) <= 0;
}

export function rangesOverlap(a: ChainageRange, b: ChainageRange): boolean {
  return maxStation(a.from, b.from) < minStation(a.to, b.to);
}

export function rangesEqual(a: ChainageRange, b: ChainageRange): boolean {
  return a.from === b.from && a.to === b.to;
}

export function rangeCovers(outer: ChainageRange, inner: ChainageRange): boolean {
  return (
    compareStations(outer.from, inner.from) <= 0 && compareStations(outer.to, inner.to) >= 0
  );
}

export function intersectRanges(a: ChainageRange, b: ChainageRange): ChainageRange | null {
  const from = maxStation(a.from, b.from);
  const to = minStation(a.to, b.to);
  return compareStations(from, to) < 0 ? { from, to } : null;
}

/** `a` with everything `b` covers taken out. Gives nought, one or two pieces. */
export function subtractRange(a: ChainageRange, b: ChainageRange): ChainageRange[] {
  if (!rangesOverlap(a, b)) return isPointRange(a) ? [] : [a];
  const out: ChainageRange[] = [];
  if (compareStations(a.from, b.from) < 0) out.push({ from: a.from, to: b.from });
  if (compareStations(b.to, a.to) < 0) out.push({ from: b.to, to: a.to });
  return out;
}

export function compareRanges(a: ChainageRange, b: ChainageRange): number {
  const byFrom = compareStations(a.from, b.from);
  return byFrom !== 0 ? byFrom : compareStations(a.to, b.to);
}

/**
 * Sort and merge, so overlapping or abutting stretches collapse into one. A
 * point that falls on or inside an existing stretch disappears into it, which
 * is right: it is already covered. A point on its own survives as a point.
 */
export function unionRanges(ranges: readonly ChainageRange[]): ChainageRange[] {
  const sorted = [...ranges].sort(compareRanges);
  const out: ChainageRange[] = [];
  for (const range of sorted) {
    const last = out[out.length - 1];
    if (last !== undefined && compareStations(range.from, last.to) <= 0) {
      out[out.length - 1] = { from: last.from, to: maxStation(last.to, range.to) };
    } else {
      out.push(range);
    }
  }
  return out;
}

/** Total covered length, counting any overlap once. */
export function coveredLength(ranges: readonly ChainageRange[]): number {
  return unionRanges(ranges).reduce((total, r) => total + rangeLength(r), 0);
}

/** Cut at a station. A cut outside the range leaves it whole. */
export function splitRangeAt(range: ChainageRange, station: Station): ChainageRange[] {
  if (!rangeContains(range, station) || station === range.from) return [range];
  return [
    { from: range.from, to: station },
    { from: station, to: range.to },
  ];
}

/**
 * The stretches two lists of ranges have in common.
 *
 * Both sides are normalised first, so a caller can pass overlapping ranges
 * without the intersection counting the shared length twice.
 */
export function intersectRangeLists(
  left: readonly ChainageRange[],
  right: readonly ChainageRange[],
): ChainageRange[] {
  const a = unionRanges(left);
  const b = unionRanges(right);
  const out: ChainageRange[] = [];
  for (const one of a) {
    for (const other of b) {
      const shared = intersectRanges(one, other);
      if (shared !== null) out.push(shared);
    }
  }
  return unionRanges(out);
}
