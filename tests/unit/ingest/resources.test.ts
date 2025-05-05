import { describe, expect, it } from 'vitest';
import { totalHours } from '../../../src/domain/resource/return.js';
import { formatSiteDate } from '../../../src/domain/time/siteDate.js';
import { errors, rowsAccepted } from '../../../src/ingest/report.js';
import { importResourceReturns } from '../../../src/ingest/resources.js';

const header =
  'return,package,step,date,shift,kind,resource,description,count,hours,hour kind,returned by,note';

const csv = [
  header,
  'RR-01,WP-01,strip,2025-03-03,day,labour,GANG-EARTH,earthworks gang,6,8,,A Kerrigan,',
  'RR-01,WP-01,strip,2025-03-03,day,plant,PL-D6,dozer,1,8,,A Kerrigan,',
  'RR-01,WP-01,strip,2025-03-03,day,plant,PL-360,22t excavator,1,3,standing,A Kerrigan,',
  'RR-02,WP-01,excavate,2025-03-04,night,labour,GANG-EARTH,earthworks gang,4,10,,A Kerrigan,water main',
].join('\n');

describe('importResourceReturns', () => {
  it('groups the rows back into returns', () => {
    const report = importResourceReturns(csv);
    expect(report.rowsRead).toBe(4);
    expect(report.imported).toHaveLength(2);
    expect(report.issues).toEqual([]);
  });

  it('puts the labour and the plant lines on the right side', () => {
    const [first] = importResourceReturns(csv).imported;
    expect(first!.labour).toHaveLength(1);
    expect(first!.plant).toHaveLength(2);
    expect(totalHours(first!.plant)).toBe(11);
  });

  it('takes the grouping fields off the first row of each return', () => {
    const [, second] = importResourceReturns(csv).imported;
    expect(second!.id).toBe('RR-02');
    expect(second!.packageId).toBe('WP-01');
    expect(second!.stepId).toBe('excavate');
    expect(formatSiteDate(second!.date)).toBe('2025-03-04');
    expect(second!.shift).toBe('night');
    expect(second!.note).toBe('water main');
  });

  it('reads an empty hour kind as hours that were worked', () => {
    const [first] = importResourceReturns(csv).imported;
    expect(first!.labour[0]!.kind).toBe('working');
    expect(first!.plant[1]!.kind).toBe('standing');
  });

  it('reads an empty step as no step named', () => {
    const text = [
      header,
      'RR-09,WP-01,,2025-03-03,day,labour,GANG-EARTH,gang,2,8,,A Kerrigan,tidy',
    ].join('\n');
    expect(importResourceReturns(text).imported[0]!.stepId).toBeNull();
  });

  it('refuses a file missing a column it needs', () => {
    const report = importResourceReturns('return,package\nRR-01,WP-01');
    expect(report.imported).toEqual([]);
    expect(errors(report).map((e) => e.column)).toContain('hours');
  });

  it('rejects the row whose grouping fields disagree with the first', () => {
    const text = [
      header,
      'RR-01,WP-01,strip,2025-03-03,day,labour,GANG-EARTH,gang,6,8,,A Kerrigan,',
      'RR-01,WP-02,strip,2025-03-03,day,labour,GANG-EARTH,gang,6,8,,A Kerrigan,',
    ].join('\n');
    const report = importResourceReturns(text);
    expect(report.imported).toHaveLength(1);
    expect(report.imported[0]!.labour).toHaveLength(1);
    expect(errors(report)[0]!.message).toContain('on one row and');
  });

  it('leaves out the row it could not read and keeps the return', () => {
    const text = [
      header,
      'RR-01,WP-01,strip,2025-03-03,day,labour,GANG-EARTH,gang,6,8,,A Kerrigan,',
      'RR-01,WP-01,strip,2025-03-03,day,plant,PL-D6,dozer,one,8,,A Kerrigan,',
    ].join('\n');
    const report = importResourceReturns(text);
    expect(report.imported).toHaveLength(1);
    expect(report.imported[0]!.plant).toEqual([]);
    expect(errors(report)[0]!.line).toBe(3);
  });

  it('rejects an hour kind nobody uses', () => {
    const text = [
      header,
      'RR-01,WP-01,strip,2025-03-03,day,labour,GANG-EARTH,gang,6,8,resting,A Kerrigan,',
    ].join('\n');
    expect(rowsAccepted(importResourceReturns(text))).toBe(0);
  });

  it('reads a file with headings and no rows at all', () => {
    const report = importResourceReturns(header);
    expect(report.rowsRead).toBe(0);
    expect(report.imported).toEqual([]);
    expect(report.issues).toEqual([]);
  });
});
