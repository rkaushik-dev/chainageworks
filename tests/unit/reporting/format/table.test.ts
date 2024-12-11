import { describe, expect, it } from 'vitest';
import {
  indent,
  renderHeading,
  renderPairs,
  renderTable,
} from '../../../../src/reporting/format/table.js';

const columns = [
  { heading: 'package' },
  { heading: 'earned', align: 'right' as const },
  { heading: 'percent', align: 'right' as const },
];

const rows = [
  ['WP-01', '£16,152.50', '32.5%'],
  ['WP-02', '£0.00', '0%'],
];

describe('renderTable', () => {
  it('sizes every column to its widest cell', () => {
    expect(renderTable(columns, rows).split('\n')).toEqual([
      'package      earned  percent',
      '-------  ----------  -------',
      'WP-01    £16,152.50    32.5%',
      'WP-02         £0.00       0%',
    ]);
  });

  it('can leave the rule off', () => {
    const lines = renderTable(columns, rows, { rule: false }).split('\n');
    expect(lines).toHaveLength(3);
    expect(lines[1]).toContain('WP-01');
  });

  it('honours a wider gap', () => {
    const wide = renderTable(columns, rows, { gap: 4, rule: false });
    expect(wide.split('\n')[0]).toBe('package        earned    percent');
  });

  it('left aligns by default and right aligns on request', () => {
    const single = renderTable([{ heading: 'a' }], [['xyz'], ['z']], { rule: false });
    expect(single.split('\n')[2]).toBe('z');
    const right = renderTable([{ heading: 'a', align: 'right' }], [['xyz'], ['z']], {
      rule: false,
    });
    expect(right.split('\n')[2]).toBe('  z');
  });

  it('trims the trailing space off every line', () => {
    for (const line of renderTable(columns, rows).split('\n')) {
      expect(line).toBe(line.trimEnd());
    }
  });

  it('clips a cell that is wider than the column allows', () => {
    const clipped = renderTable(
      [{ heading: 'name', width: 6 }],
      [['a very long package name']],
      { rule: false },
    );
    expect(clipped.split('\n')[1]).toBe('a ver…');
  });

  it('clips the heading too', () => {
    const clipped = renderTable([{ heading: 'a very long heading', width: 4 }], [['x']], {
      rule: false,
    });
    expect(clipped.split('\n')[0]).toBe('a v…');
  });

  it('renders headings with no rows at all', () => {
    expect(renderTable(columns, [], { rule: false })).toBe('package  earned  percent');
  });

  it('refuses a table with no columns', () => {
    expect(() => renderTable([], [])).toThrow(/at least one column/);
  });

  it('refuses a row with the wrong number of cells', () => {
    expect(() => renderTable(columns, [['a', 'b']])).toThrow(/wrong number of cells/);
  });
});

describe('renderHeading', () => {
  it('underlines to the width of the text', () => {
    expect(renderHeading('progress report')).toBe('progress report\n===============');
  });

  it('takes a different rule character', () => {
    expect(renderHeading('held work', '-')).toBe('held work\n---------');
  });
});

describe('renderPairs', () => {
  it('aligns the values', () => {
    expect(
      renderPairs([
        ['valuation', '3'],
        ['cut off', '2025-03-25'],
      ]),
    ).toBe('valuation  3\ncut off    2025-03-25');
  });

  it('handles an empty list', () => {
    expect(renderPairs([])).toBe('');
  });
});

describe('indent', () => {
  it('shifts every line and leaves blank lines alone', () => {
    expect(indent('a\n\nb')).toBe('  a\n\n  b');
  });

  it('takes a different width', () => {
    expect(indent('a', 4)).toBe('    a');
  });
});
