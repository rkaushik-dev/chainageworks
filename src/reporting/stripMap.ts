import { BadInputError } from '../errors.js';
import type { ChainageRange } from '../domain/chainage/range.js';
import { formatChainageRange, intersectRangeLists } from '../domain/chainage/range.js';
import type { Station } from '../domain/chainage/station.js';
import { asStation, formatStation, MM_PER_METRE } from '../domain/chainage/station.js';
import type { WorkPackage } from '../domain/packages/workPackage.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import type { ProgressContext } from '../progress/context.js';
import { packageStepProgress } from '../progress/stepProgress.js';

/**
 * What a stretch of one step looks like on the ground.
 *
 * `held` and `withheld` are separated because the two need different people.
 * Held work is waiting on an inspection, which is a phone call. Withheld work
 * is waiting on a non conformance being put right, which is a gang.
 */
export type CellState = 'not_started' | 'credited' | 'held' | 'withheld';

export interface StripCell {
  readonly extent: ChainageRange;
  readonly state: CellState;
}

export interface StripRow {
  readonly stepId: string;
  readonly cells: readonly StripCell[];
}

export interface StripMap {
  readonly packageId: string;
  readonly extent: ChainageRange;
  readonly cellLength: number;
  readonly rows: readonly StripRow[];
}

/** Fifty metres a cell, which puts a kilometre across twenty characters. */
export const DEFAULT_CELL_LENGTH = 50 * MM_PER_METRE;

function cellsOf(extent: ChainageRange, cellLength: number): ChainageRange[] {
  const out: ChainageRange[] = [];
  for (let from: number = extent.from; from < extent.to; from += cellLength) {
    const to = Math.min(from + cellLength, extent.to);
    out.push({ from: asStation(from), to: asStation(to) });
  }
  return out;
}

function overlaps(cell: ChainageRange, ranges: readonly ChainageRange[]): boolean {
  return intersectRangeLists([cell], ranges).length > 0;
}

/**
 * A strip of the alignment, one row per step, showing where each step has got
 * to and what is stopping it.
 */
export function buildStripMap(
  context: ProgressContext,
  pkg: WorkPackage,
  asAt: SiteDate,
  cellLength: number = DEFAULT_CELL_LENGTH,
): StripMap {
  if (!Number.isInteger(cellLength) || cellLength <= 0) {
    throw new BadInputError('a strip cell has to be a positive whole number of millimetres', {
      cellLength,
    });
  }
  const cells = cellsOf(pkg.extent, cellLength);
  const rows = packageStepProgress(context, pkg, asAt).map(
    (step): StripRow => ({
      stepId: step.stepId,
      cells: cells.map((cell): StripCell => {
        if (overlaps(cell, step.withheldExtent) && overlaps(cell, step.builtExtent)) {
          return { extent: cell, state: 'withheld' };
        }
        if (overlaps(cell, step.creditedExtent)) return { extent: cell, state: 'credited' };
        if (overlaps(cell, step.builtExtent)) return { extent: cell, state: 'held' };
        return { extent: cell, state: 'not_started' };
      }),
    }),
  );
  return { packageId: pkg.id, extent: pkg.extent, cellLength, rows };
}

const GLYPHS: Readonly<Record<CellState, string>> = {
  not_started: '.',
  credited: '#',
  held: 'o',
  withheld: 'x',
};

export function cellGlyph(state: CellState): string {
  return GLYPHS[state];
}

export interface RenderOptions {
  /** Width of the step name column. */
  readonly labelWidth?: number;
  /** Print the chainage of both ends underneath. */
  readonly scale?: boolean;
}

/** A fixed width picture of the strip, for the terminal and the report. */
export function renderStrip(map: StripMap, options: RenderOptions = {}): string {
  const labelWidth = options.labelWidth ?? Math.max(...map.rows.map((r) => r.stepId.length), 4);
  const lines = map.rows.map(
    (row) =>
      `${row.stepId.padEnd(labelWidth)} ${row.cells.map((c) => cellGlyph(c.state)).join('')}`,
  );
  if (options.scale === false) return lines.join('\n');
  const width = map.rows[0]?.cells.length ?? 0;
  const left = formatStation(map.extent.from, { decimals: 0 });
  const right = formatStation(map.extent.to, { decimals: 0 });
  const gap = Math.max(1, width - left.length - right.length);
  lines.push(`${' '.repeat(labelWidth)} ${left}${' '.repeat(gap)}${right}`);
  return lines.join('\n');
}

/** Cells in a state, with where they are, for a chase list. */
export function cellsInState(map: StripMap, state: CellState): StripCell[] {
  return map.rows.flatMap((row) => row.cells.filter((c) => c.state === state));
}

export function describeCell(cell: StripCell): string {
  return `${formatChainageRange(cell.extent)} ${cell.state}`;
}

export function stationAtCell(map: StripMap, index: number): Station {
  const cell = map.rows[0]?.cells[index];
  if (cell === undefined) {
    throw new BadInputError('no cell at that index', { index });
  }
  return cell.extent.from;
}
