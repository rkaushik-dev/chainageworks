import { describe, expect, it } from 'vitest';
import { BadInputError, ConflictError } from '../../../src/errors.js';
import { parseCsv } from '../../../src/io/csv.js';
import type { ImportReport } from '../../../src/ingest/report.js';
import {
  checkColumns,
  emptyReport,
  errors,
  formatImportReport,
  hasErrors,
  issue,
  issueFrom,
  mergeReports,
  rowsAccepted,
  rowsRejected,
  warnings,
} from '../../../src/ingest/report.js';

const report: ImportReport<string> = {
  imported: ['a', 'b'],
  issues: [
    issue('quantity is missing', 4, 'quantity'),
    issue('the method was not recognised, taking a tape', 6, 'method', 'warning'),
  ],
  rowsRead: 3,
};

describe('the report shape', () => {
  it('counts what was taken and what was left out', () => {
    expect(rowsAccepted(report)).toBe(2);
    expect(rowsRejected(report)).toBe(1);
  });

  it('separates errors from warnings', () => {
    expect(errors(report)).toHaveLength(1);
    expect(warnings(report)).toHaveLength(1);
    expect(hasErrors(report)).toBe(true);
  });

  it('starts empty', () => {
    const blank = emptyReport<string>();
    expect(blank.imported).toEqual([]);
    expect(blank.rowsRead).toBe(0);
    expect(hasErrors(blank)).toBe(false);
  });

  it('has no errors when everything is a warning', () => {
    expect(hasErrors({ ...report, issues: warnings(report) })).toBe(false);
  });
});

describe('issue', () => {
  it('defaults to a file level error', () => {
    const problem = issue('nothing here');
    expect(problem).toEqual({
      line: null,
      column: null,
      message: 'nothing here',
      severity: 'error',
    });
  });
});

describe('issueFrom', () => {
  it('takes the line and column out of the error details', () => {
    const error = new BadInputError('line 7: quantity is missing', {
      line: 7,
      column: 'quantity',
    });
    expect(issueFrom(error)).toEqual({
      line: 7,
      column: 'quantity',
      message: 'line 7: quantity is missing',
      severity: 'error',
    });
  });

  it('falls back to the line it was given', () => {
    expect(issueFrom(new ConflictError('already booked'), 9).line).toBe(9);
  });

  it('has no line when nothing supplies one', () => {
    expect(issueFrom(new ConflictError('already booked')).line).toBeNull();
  });

  it('stringifies anything that is not one of our errors', () => {
    expect(issueFrom('disk went away', 3)).toEqual({
      line: 3,
      column: null,
      message: 'disk went away',
      severity: 'error',
    });
  });
});

describe('checkColumns', () => {
  const table = parseCsv('record,package\nSR-01,WP-01');

  it('passes when every column is there', () => {
    expect(checkColumns(table, ['record', 'PACKAGE'])).toBeNull();
  });

  it('gives one issue per missing column and nothing imported', () => {
    const failed = checkColumns<string>(table, ['record', 'code', 'quantity']);
    expect(failed?.imported).toEqual([]);
    expect(failed?.issues.map((i) => i.column)).toEqual(['code', 'quantity']);
    expect(failed?.issues[0]?.message).toBe('the file has no code column');
  });

  it('still reports how many rows the file had', () => {
    expect(checkColumns<string>(table, ['code'])?.rowsRead).toBe(1);
  });
});

describe('mergeReports', () => {
  it('adds the imports, the issues and the counts', () => {
    const merged = mergeReports([report, report]);
    expect(merged.imported).toHaveLength(4);
    expect(merged.issues).toHaveLength(4);
    expect(merged.rowsRead).toBe(6);
  });

  it('merges nothing into an empty report', () => {
    expect(mergeReports<string>([])).toEqual(emptyReport<string>());
  });
});

describe('formatImportReport', () => {
  it('summarises the counts and lists every issue', () => {
    expect(formatImportReport(report, 'records')).toBe(
      [
        'read 3 records, took 2, left out 1',
        '  error: line 4 (quantity): quantity is missing',
        '  warning: line 6 (method): the method was not recognised, taking a tape',
      ].join('\n'),
    );
  });

  it('says file for an issue with no line', () => {
    const fileLevel: ImportReport<string> = {
      imported: [],
      issues: [issue('the file has no code column', null, 'code')],
      rowsRead: 2,
    };
    expect(formatImportReport(fileLevel)).toContain('error: file (code):');
  });

  it('is one line when nothing went wrong', () => {
    expect(formatImportReport({ imported: ['a'], issues: [], rowsRead: 1 })).toBe(
      'read 1 rows, took 1, left out 0',
    );
  });
});
