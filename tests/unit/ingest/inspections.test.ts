import { describe, expect, it } from 'vitest';
import { formatChainageRange } from '../../../src/domain/chainage/range.js';
import { formatSiteDate } from '../../../src/domain/time/siteDate.js';
import {
  importNcrs,
  importSignOffs,
  NCR_COLUMNS,
  SIGN_OFF_COLUMNS,
} from '../../../src/ingest/inspections.js';
import { errors, hasErrors } from '../../../src/ingest/report.js';

const signHeader = SIGN_OFF_COLUMNS.join(',');
const ncrHeader = NCR_COLUMNS.join(',');

const signSheet = (...rows: string[]) => [signHeader, ...rows].join('\n');
const ncrSheet = (...rows: string[]) => [ncrHeader, ...rows].join('\n');

describe('importSignOffs', () => {
  it('reads a clean row', () => {
    const report = importSignOffs(
      signSheet('SO-01,IP-E1,WP-01,12+000,12+400,2025-03-12,R Engineer'),
    );
    expect(hasErrors(report)).toBe(false);
    const signOff = report.imported[0]!;
    expect(signOff.id).toBe('SO-01');
    expect(signOff.pointId).toBe('IP-E1');
    expect(formatChainageRange(signOff.extent)).toBe('12+000.000 to 12+400.000');
    expect(formatSiteDate(signOff.signedOn)).toBe('2025-03-12');
    expect(signOff.outcome).toBe('passed');
    expect(signOff.note).toBe('');
  });

  it('reads the optional outcome and note', () => {
    const report = importSignOffs(
      [
        `${signHeader},outcome,note`,
        'SO-01,IP-E1,WP-01,12+000,12+400,2025-03-12,R Engineer,passed with comment,minor step',
      ].join('\n'),
    );
    expect(report.imported[0]?.outcome).toBe('passed_with_comment');
    expect(report.imported[0]?.note).toBe('minor step');
  });

  it('rejects an outcome it does not know', () => {
    const report = importSignOffs(
      [
        `${signHeader},outcome`,
        'SO-01,IP-E1,WP-01,12+000,12+400,2025-03-12,R Engineer,maybe',
      ].join('\n'),
    );
    expect(errors(report)[0]?.message).toMatch(/is not one of passed/);
    expect(errors(report)[0]?.column).toBe('outcome');
  });

  it('rejects a row with nobody named', () => {
    const report = importSignOffs(signSheet('SO-01,IP-E1,WP-01,12+000,12+400,2025-03-12,'));
    expect(errors(report)[0]?.message).toBe('line 2: by is missing');
  });

  it('rejects the second row that reuses an id', () => {
    const row = 'SO-01,IP-E1,WP-01,12+000,12+400,2025-03-12,R Engineer';
    const report = importSignOffs(signSheet(row, row));
    expect(report.imported).toHaveLength(1);
    expect(errors(report)[0]?.message).toBe('sign off SO-01 appears twice');
  });

  it('names every missing column once', () => {
    const report = importSignOffs('sign off,point\nSO-01,IP-E1');
    expect(report.issues).toHaveLength(5);
  });
});

describe('importNcrs', () => {
  it('reads a clean row and defaults the rest', () => {
    const report = importNcrs(
      ncrSheet('NCR-01,WP-01,12+100,12+150,2025-03-25,layer too thick'),
    );
    expect(hasErrors(report)).toBe(false);
    const ncr = report.imported[0]!;
    expect(ncr.id).toBe('NCR-01');
    expect(ncr.status).toBe('open');
    expect(ncr.settledOn).toBeNull();
    expect(ncr.stepId).toBeNull();
    expect(ncr.withholdsCredit).toBe(true);
    expect(ncr.description).toBe('layer too thick');
  });

  it('reads the step, the status and the settled date', () => {
    const report = importNcrs(
      [
        `${ncrHeader},step,status,settled on`,
        'NCR-02,WP-01,12+000,12+200,2025-02-10,density short,place,closed,2025-03-14',
      ].join('\n'),
    );
    const ncr = report.imported[0]!;
    expect(ncr.stepId).toBe('place');
    expect(ncr.status).toBe('closed');
    expect(formatSiteDate(ncr.settledOn!)).toBe('2025-03-14');
  });

  it('reads the withholding flag in either spelling', () => {
    for (const [text, expected] of [
      ['yes', true],
      ['Y', true],
      ['true', true],
      ['no', false],
      ['N', false],
      ['0', false],
    ] as const) {
      const report = importNcrs(
        [
          `${ncrHeader},withholds credit`,
          `NCR-01,WP-01,12+100,12+150,2025-03-25,note,${text}`,
        ].join('\n'),
      );
      expect(report.imported[0]?.withholdsCredit).toBe(expected);
    }
  });

  it('rejects a withholding flag it cannot read', () => {
    const report = importNcrs(
      [
        `${ncrHeader},withholds credit`,
        'NCR-01,WP-01,12+100,12+150,2025-03-25,note,perhaps',
      ].join('\n'),
    );
    expect(errors(report)[0]?.message).toBe('perhaps is not a yes or a no');
  });

  it('rejects a closed report with no settled date', () => {
    const report = importNcrs(
      [`${ncrHeader},status`, 'NCR-01,WP-01,12+100,12+150,2025-03-25,note,closed'].join('\n'),
    );
    expect(errors(report)[0]?.message).toMatch(/needs the date it was settled/);
  });

  it('rejects a report settled before it was raised', () => {
    const report = importNcrs(
      [
        `${ncrHeader},status,settled on`,
        'NCR-01,WP-01,12+100,12+150,2025-03-25,note,closed,2025-01-01',
      ].join('\n'),
    );
    expect(errors(report)[0]?.message).toMatch(/settled before it was raised/);
  });

  it('rejects the second row that reuses an id', () => {
    const row = 'NCR-01,WP-01,12+100,12+150,2025-03-25,layer too thick';
    const report = importNcrs(ncrSheet(row, row));
    expect(report.imported).toHaveLength(1);
    expect(errors(report)[0]?.message).toBe('report NCR-01 appears twice');
  });

  it('keeps reading past a bad row', () => {
    const report = importNcrs(
      ncrSheet(
        'NCR-01,WP-01,12+100,12+150,2025-03-25,first',
        'NCR-02,WP-01,nonsense,12+150,2025-03-25,second',
        'NCR-03,WP-01,12+200,12+250,2025-03-26,third',
      ),
    );
    expect(report.imported.map((n) => n.id)).toEqual(['NCR-01', 'NCR-03']);
    expect(errors(report)).toHaveLength(1);
  });
});
