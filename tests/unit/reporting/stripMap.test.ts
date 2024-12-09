import { describe, expect, it } from 'vitest';
import { formatChainageRange } from '../../../src/domain/chainage/range.js';
import { formatStation, MM_PER_METRE } from '../../../src/domain/chainage/station.js';
import { requirePackage } from '../../../src/domain/packages/register.js';
import {
  buildStripMap,
  cellGlyph,
  cellsInState,
  DEFAULT_CELL_LENGTH,
  describeCell,
  renderStrip,
  stationAtCell,
} from '../../../src/reporting/stripMap.js';
import { d, range, sampleContext } from '../../support/contextFixtures.js';

const context = sampleContext();
const wp01 = requirePackage(context.register, 'WP-01');
const cutOff = d('2025-03-31');
const map = buildStripMap(context, wp01, cutOff, 100 * MM_PER_METRE);

const row = (stepId: string) => map.rows.find((r) => r.stepId === stepId)!;
const glyphs = (stepId: string) =>
  row(stepId)
    .cells.map((c) => cellGlyph(c.state))
    .join('');

describe('buildStripMap', () => {
  it('cuts the package into cells of the length asked for', () => {
    expect(map.cellLength).toBe(100_000);
    expect(row('strip').cells).toHaveLength(8);
    expect(formatChainageRange(row('strip').cells[0]!.extent)).toBe('12+000.000 to 12+100.000');
  });

  it('defaults to fifty metre cells', () => {
    const fine = buildStripMap(context, wp01, cutOff);
    expect(fine.cellLength).toBe(DEFAULT_CELL_LENGTH);
    expect(fine.rows[0]?.cells).toHaveLength(16);
  });

  it('gives one row per step in the rule of credit', () => {
    expect(map.rows.map((r) => r.stepId)).toEqual(['strip', 'excavate', 'place', 'trim']);
  });

  it('refuses a cell length that is not a positive whole number', () => {
    expect(() => buildStripMap(context, wp01, cutOff, 0)).toThrow(/positive whole number/);
    expect(() => buildStripMap(context, wp01, cutOff, 1.5)).toThrow(/positive whole number/);
  });

  it('leaves a short last cell rather than running past the package', () => {
    const odd = buildStripMap(context, wp01, cutOff, 300 * MM_PER_METRE);
    const cells = odd.rows[0]!.cells;
    expect(cells).toHaveLength(3);
    expect(formatStation(cells[2]!.extent.to)).toBe('12+800.000');
  });
});

describe('the states on the strip', () => {
  it('credits the whole of a step nothing is gating', () => {
    expect(glyphs('strip')).toBe('########');
  });

  it('shows the excavation credited to the sign off and held after it', () => {
    expect(glyphs('excavate')).toBe('####oo..');
  });

  it('shows the placing with the non conformance punched out of it', () => {
    expect(glyphs('place')).toBe('#xoo....');
  });

  it('shows a step nobody has started as untouched', () => {
    expect(glyphs('trim')).toBe('........');
  });

  it('collects the cells in a state for a chase list', () => {
    const withheld = cellsInState(map, 'withheld');
    expect(withheld.map(describeCell)).toEqual(['12+100.000 to 12+200.000 withheld']);
  });

  it('turns everything green once the gates open and the report closes', () => {
    const released = sampleContext({
      signOffs: sampleContext().signOffs.map((s) => ({
        ...s,
        extent: range('12+000', '12+800'),
      })),
      ncrs: [],
    });
    const open = buildStripMap(released, wp01, cutOff, 100 * MM_PER_METRE);
    expect(open.rows[1]!.cells.map((c) => cellGlyph(c.state)).join('')).toBe('######..');
  });
});

describe('renderStrip', () => {
  it('draws one line per step with the name padded', () => {
    const text = renderStrip(map, { scale: false });
    expect(text.split('\n')).toEqual([
      'strip    ########',
      'excavate ####oo..',
      'place    #xoo....',
      'trim     ........',
    ]);
  });

  it('adds a chainage scale underneath by default', () => {
    const lines = renderStrip(map).split('\n');
    expect(lines).toHaveLength(5);
    expect(lines[4]).toContain('12+000');
    expect(lines[4]).toContain('12+800');
  });

  it('honours a wider label column', () => {
    const text = renderStrip(map, { labelWidth: 12, scale: false });
    expect(text.startsWith('strip        #')).toBe(true);
  });
});

describe('stationAtCell', () => {
  it('gives the chainage a cell starts at', () => {
    expect(formatStation(stationAtCell(map, 0))).toBe('12+000.000');
    expect(formatStation(stationAtCell(map, 4))).toBe('12+400.000');
  });

  it('refuses an index off the end', () => {
    expect(() => stationAtCell(map, 99)).toThrow(/no cell at that index/);
  });
});
