import { describe, expect, it } from 'vitest';
import { formatItemCode } from '../../../src/domain/bill/itemCode.js';
import { formatChainageRange } from '../../../src/domain/chainage/range.js';
import { formatQuantity } from '../../../src/domain/quantity/quantity.js';
import { formatSiteDate } from '../../../src/domain/time/siteDate.js';
import { importSurveyRecords, RECORD_COLUMNS } from '../../../src/ingest/records.js';
import { errors, hasErrors, warnings } from '../../../src/ingest/report.js';

const header = RECORD_COLUMNS.join(',');

const sheet = (...rows: string[]) => [header, ...rows].join('\n');

const good = 'SR-01,WP-01,E423.1,excavate,12+000,12+200,1800,m3,2025-03-06,J Foreman';

describe('importSurveyRecords', () => {
  it('reads a clean sheet', () => {
    const report = importSurveyRecords(sheet(good));
    expect(hasErrors(report)).toBe(false);
    expect(report.rowsRead).toBe(1);
    const record = report.imported[0]!;
    expect(record.id).toBe('SR-01');
    expect(record.packageId).toBe('WP-01');
    expect(formatItemCode(record.code)).toBe('E423.1');
    expect(record.stepId).toBe('excavate');
    expect(formatChainageRange(record.extent)).toBe('12+000.000 to 12+200.000');
    expect(formatQuantity(record.quantity)).toBe('1800.00 m3');
    expect(formatSiteDate(record.measuredOn)).toBe('2025-03-06');
    expect(record.bookedBy).toBe('J Foreman');
    expect(record.method).toBe('tape');
  });

  it('reads several rows and keeps going past a bad one', () => {
    const report = importSurveyRecords(
      sheet(
        good,
        'SR-02,WP-01,nonsense,excavate,12+200,12+400,1750,m3,2025-03-13,J Foreman',
        'SR-03,WP-01,E423.1,excavate,12+400,12+600,1600,m3,2025-03-20,J Foreman',
      ),
    );
    expect(report.rowsRead).toBe(3);
    expect(report.imported.map((r) => r.id)).toEqual(['SR-01', 'SR-03']);
    expect(errors(report)).toHaveLength(1);
    expect(errors(report)[0]?.line).toBe(3);
    expect(errors(report)[0]?.column).toBe('code');
  });

  it('reads the optional columns when they are there', () => {
    const withExtras = [
      `${header},method,supersedes,note`,
      `${good},total station,SR-00,retaken after the rain`,
    ].join('\n');
    const record = importSurveyRecords(withExtras).imported[0]!;
    expect(record.method).toBe('total_station');
    expect(record.supersedes).toBe('SR-00');
    expect(record.note).toBe('retaken after the rain');
  });

  it('warns about a method it does not know and takes a tape', () => {
    const report = importSurveyRecords(
      [`${header},method`, `${good},laser guesswork`].join('\n'),
    );
    expect(hasErrors(report)).toBe(false);
    expect(warnings(report)).toHaveLength(1);
    expect(warnings(report)[0]?.message).toMatch(/was not recognised/);
    expect(report.imported[0]?.method).toBe('tape');
  });

  it('names the missing field and the line', () => {
    const report = importSurveyRecords(
      sheet('SR-01,WP-01,E423.1,excavate,12+000,12+200,,m3,2025-03-06,J Foreman'),
    );
    expect(report.imported).toEqual([]);
    expect(errors(report)[0]?.message).toBe('line 2: quantity is missing');
    expect(errors(report)[0]?.column).toBe('quantity');
  });

  it('rejects a quantity that is not a number', () => {
    const report = importSurveyRecords(
      sheet('SR-01,WP-01,E423.1,excavate,12+000,12+200,lots,m3,2025-03-06,J Foreman'),
    );
    expect(errors(report)[0]?.message).toBe('the quantity is not a number');
  });

  it('reads a quantity with thousands separators', () => {
    const withCommas = [
      header,
      'SR-01,WP-01,E423.1,excavate,12+000,12+200,"12,400",m3,2025-03-06,J Foreman',
    ].join('\n');
    expect(formatQuantity(importSurveyRecords(withCommas).imported[0]!.quantity)).toBe(
      '12400.00 m3',
    );
  });

  it('rejects a chainage that runs backwards', () => {
    const report = importSurveyRecords(
      sheet('SR-01,WP-01,E423.1,excavate,12+400,12+200,10,m3,2025-03-06,J Foreman'),
    );
    expect(errors(report)[0]?.message).toMatch(/runs forwards/);
  });

  it('rejects a date that is not a real day', () => {
    const report = importSurveyRecords(
      sheet('SR-01,WP-01,E423.1,excavate,12+000,12+200,10,m3,2025-02-30,J Foreman'),
    );
    expect(errors(report)[0]?.column).toBe('measured on');
  });

  it('rejects a unit it does not know', () => {
    const report = importSurveyRecords(
      sheet('SR-01,WP-01,E423.1,excavate,12+000,12+200,10,furlongs,2025-03-06,J Foreman'),
    );
    expect(errors(report)[0]?.message).toMatch(/unknown unit/);
  });

  it('rejects the second row that reuses an id', () => {
    const report = importSurveyRecords(sheet(good, good));
    expect(report.imported).toHaveLength(1);
    expect(errors(report)[0]?.message).toBe('record SR-01 appears twice');
    expect(errors(report)[0]?.line).toBe(3);
  });

  it('reports one issue per missing column rather than one per row', () => {
    const report = importSurveyRecords('record,package\nSR-01,WP-01\nSR-02,WP-01');
    expect(report.imported).toEqual([]);
    expect(report.issues).toHaveLength(8);
    expect(report.issues.every((i) => i.line === null)).toBe(true);
  });

  it('reads an empty sheet as nothing', () => {
    const report = importSurveyRecords(header);
    expect(report.imported).toEqual([]);
    expect(report.rowsRead).toBe(0);
    expect(hasErrors(report)).toBe(false);
  });

  it('reads columns in any order and ignores their case', () => {
    const shuffled = [
      'CODE,record,PACKAGE,step,to,from,unit,quantity,booked by,measured on',
      'E423.1,SR-01,WP-01,excavate,12+200,12+000,m3,1800,J Foreman,2025-03-06',
    ].join('\n');
    expect(importSurveyRecords(shuffled).imported[0]?.id).toBe('SR-01');
  });
});
