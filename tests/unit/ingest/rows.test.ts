import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../src/errors.js';
import { need, needNumber } from '../../../src/ingest/fields.js';
import { errors, rowsAccepted, rowsRejected, warnings } from '../../../src/ingest/report.js';
import { issue } from '../../../src/ingest/report.js';
import { readTable } from '../../../src/ingest/rows.js';

interface Gang {
  readonly id: string;
  readonly size: number;
}

const spec = {
  columns: ['gang', 'size'] as const,
  read: (reader: Parameters<typeof need>[0]): Gang => ({
    id: need(reader, 'gang'),
    size: needNumber(reader, 'size'),
  }),
  keyOf: (gang: Gang) => gang.id,
  duplicate: (id: string) => `gang ${id} appears twice`,
  keyColumn: 'gang',
};

const csv = ['gang,size', 'G1,6', 'G2,4'].join('\n');

describe('readTable', () => {
  it('reads every row of a clean file', () => {
    const report = readTable(csv, spec);
    expect(report.rowsRead).toBe(2);
    expect(report.imported).toEqual([
      { id: 'G1', size: 6 },
      { id: 'G2', size: 4 },
    ]);
    expect(report.issues).toEqual([]);
  });

  it('reports the missing column once rather than once a row', () => {
    const report = readTable('gang\nG1\nG2\nG3', spec);
    expect(errors(report)).toHaveLength(1);
    expect(errors(report)[0]!.message).toContain('no size column');
    expect(report.imported).toEqual([]);
  });

  it('leaves out the row it could not read and carries on', () => {
    const report = readTable(['gang,size', 'G1,6', 'G2,lots', 'G3,3'].join('\n'), spec);
    expect(rowsAccepted(report)).toBe(2);
    expect(rowsRejected(report)).toBe(1);
    expect(errors(report)[0]!.line).toBe(3);
  });

  it('leaves out the row that repeats a name and says which column', () => {
    const report = readTable(['gang,size', 'G1,6', 'G1,4'].join('\n'), spec);
    expect(rowsAccepted(report)).toBe(1);
    expect(report.imported[0]!.size).toBe(6);
    expect(errors(report)[0]!.message).toBe('gang G1 appears twice');
    expect(errors(report)[0]!.column).toBe('gang');
  });

  it('files the duplicate against the first column when none is named', () => {
    const report = readTable(['gang,size', 'G1,6', 'G1,4'].join('\n'), {
      columns: ['gang', 'size'],
      read: spec.read,
      keyOf: spec.keyOf,
    });
    expect(errors(report)[0]!.message).toBe('G1 appears twice');
    expect(errors(report)[0]!.column).toBe('gang');
  });

  it('keeps every row when nothing names a key', () => {
    const report = readTable(['gang,size', 'G1,6', 'G1,4'].join('\n'), {
      columns: ['gang', 'size'],
      read: spec.read,
    });
    expect(rowsAccepted(report)).toBe(2);
    expect(report.issues).toEqual([]);
  });

  it('lets a reader file a warning of its own without losing the row', () => {
    const report = readTable(csv, {
      columns: ['gang', 'size'],
      read: (reader, issues) => {
        issues.push(issue('taking the standard shift', reader.line, 'shift', 'warning'));
        return spec.read(reader);
      },
    });
    expect(rowsAccepted(report)).toBe(2);
    expect(warnings(report)).toHaveLength(2);
    expect(errors(report)).toEqual([]);
  });

  it('turns whatever the reader threw into an issue on that line', () => {
    const report = readTable(csv, {
      columns: ['gang', 'size'],
      read: (reader) => {
        if (reader.line === 3) throw new BadInputError('that gang was stood down');
        return spec.read(reader);
      },
    });
    expect(rowsAccepted(report)).toBe(1);
    expect(errors(report)[0]!.message).toBe('that gang was stood down');
    expect(errors(report)[0]!.line).toBe(3);
  });

  it('reads a file with headings and no rows at all', () => {
    const report = readTable('gang,size', spec);
    expect(report.rowsRead).toBe(0);
    expect(report.imported).toEqual([]);
    expect(report.issues).toEqual([]);
  });
});
