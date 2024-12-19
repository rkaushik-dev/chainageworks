import { describe, expect, it } from 'vitest';
import { missingColumns, parseCsv, readerFor, writeCsv } from '../../../src/io/csv.js';

const sheet = [
  'record,package,code,quantity',
  'SR-01,WP-01,E423.1,1800',
  'SR-02,WP-01,E423.1,1750',
].join('\n');

describe('parseCsv', () => {
  it('reads the headings and the rows', () => {
    const table = parseCsv(sheet);
    expect(table.headings).toEqual(['record', 'package', 'code', 'quantity']);
    expect(table.rows).toHaveLength(2);
    expect(table.rows[0]?.values).toEqual(['SR-01', 'WP-01', 'E423.1', '1800']);
  });

  it('numbers lines from the top of the file, counting the header', () => {
    expect(parseCsv(sheet).rows.map((r) => r.line)).toEqual([2, 3]);
  });

  it('trims whitespace around every cell', () => {
    const table = parseCsv('a, b\n 1 ,  2  ');
    expect(table.headings).toEqual(['a', 'b']);
    expect(table.rows[0]?.values).toEqual(['1', '2']);
  });

  it('reads a quoted field holding the delimiter', () => {
    const table = parseCsv('a,b\n"one, two",three');
    expect(table.rows[0]?.values).toEqual(['one, two', 'three']);
  });

  it('reads a doubled quote as one quote', () => {
    const table = parseCsv('a\n"he said ""no"""');
    expect(table.rows[0]?.values).toEqual(['he said "no"']);
  });

  it('reads a newline inside a quoted field', () => {
    const table = parseCsv('a,b\n"first\nsecond",x');
    expect(table.rows).toHaveLength(1);
    expect(table.rows[0]?.values[0]).toBe('first\nsecond');
  });

  it('reads windows line endings', () => {
    const table = parseCsv('a,b\r\n1,2\r\n');
    expect(table.rows[0]?.values).toEqual(['1', '2']);
  });

  it('strips a byte order mark', () => {
    expect(parseCsv('\uFEFFa,b\n1,2').headings).toEqual(['a', 'b']);
  });

  it('drops blank lines', () => {
    const table = parseCsv('a,b\n1,2\n\n3,4\n');
    expect(table.rows.map((r) => r.line)).toEqual([2, 4]);
  });

  it('keeps blank lines when asked to', () => {
    const table = parseCsv('a,b\n1,2\n\n3,4\n', { skipBlank: false });
    expect(table.rows).toHaveLength(3);
  });

  it('can read a file with no header', () => {
    const table = parseCsv('1,2\n3,4', { header: false });
    expect(table.headings).toEqual([]);
    expect(table.rows.map((r) => r.line)).toEqual([1, 2]);
  });

  it('honours a different delimiter', () => {
    expect(parseCsv('a;b\n1;2', { delimiter: ';' }).rows[0]?.values).toEqual(['1', '2']);
  });

  it('refuses a delimiter that is not one character', () => {
    expect(() => parseCsv('a,b', { delimiter: '||' })).toThrow(/one character/);
  });

  it('refuses a file that ends inside a quote', () => {
    expect(() => parseCsv('a\n"unclosed')).toThrow(/ends inside a quoted field/);
  });

  it('handles an empty file', () => {
    const table = parseCsv('');
    expect(table.headings).toEqual([]);
    expect(table.rows).toEqual([]);
  });

  it('keeps a short row short rather than padding it', () => {
    expect(parseCsv('a,b,c\n1,2').rows[0]?.values).toEqual(['1', '2']);
  });
});

describe('readerFor', () => {
  const table = parseCsv(sheet);
  const reader = readerFor(table, table.rows[0]!);

  it('reads a field by heading', () => {
    expect(reader.get('code')).toBe('E423.1');
    expect(reader.line).toBe(2);
    expect(reader.raw).toHaveLength(4);
  });

  it('ignores the case of the heading', () => {
    expect(reader.get('CODE')).toBe('E423.1');
  });

  it('gives null for a column the file does not have', () => {
    expect(reader.get('note')).toBeNull();
  });

  it('gives null for a blank cell', () => {
    const sparse = parseCsv('a,b\n1,');
    expect(readerFor(sparse, sparse.rows[0]!).get('b')).toBeNull();
  });

  it('gives null for a cell the row is too short to have', () => {
    const short = parseCsv('a,b,c\n1,2');
    expect(readerFor(short, short.rows[0]!).get('c')).toBeNull();
  });

  it('requires a field and names the line when it is missing', () => {
    const good = reader.require('code');
    expect(good.ok).toBe(true);
    const bad = reader.require('note');
    expect(bad.ok).toBe(false);
    if (!bad.ok) {
      expect(bad.error.message).toBe('line 2: note is missing');
      expect(bad.error.details).toEqual({ line: 2, column: 'note' });
    }
  });
});

describe('missingColumns', () => {
  it('names the headings the file does not carry', () => {
    const table = parseCsv(sheet);
    expect(missingColumns(table, ['record', 'measured on'])).toEqual(['measured on']);
    expect(missingColumns(table, ['RECORD'])).toEqual([]);
  });
});

describe('writeCsv', () => {
  it('writes a header and the rows', () => {
    expect(writeCsv(['a', 'b'], [['1', '2']])).toBe('a,b\n1,2\n');
  });

  it('quotes a cell holding the delimiter, a quote or a newline', () => {
    expect(writeCsv(['a'], [['one, two']])).toBe('a\n"one, two"\n');
    expect(writeCsv(['a'], [['he said "no"']])).toBe('a\n"he said ""no"""\n');
    expect(writeCsv(['a'], [['first\nsecond']])).toBe('a\n"first\nsecond"\n');
  });

  it('honours a different delimiter and line ending', () => {
    expect(writeCsv(['a', 'b'], [['1', '2']], { delimiter: ';', eol: '\r\n' })).toBe(
      'a;b\r\n1;2\r\n',
    );
  });

  it('round trips through the parser', () => {
    const text = writeCsv(['a', 'b'], [['one, two', 'he said "no"']]);
    expect(parseCsv(text).rows[0]?.values).toEqual(['one, two', 'he said "no"']);
  });

  it('writes a header on its own when there are no rows', () => {
    expect(writeCsv(['a', 'b'], [])).toBe('a,b\n');
  });
});
