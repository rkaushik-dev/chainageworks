import { describe, expect, it } from 'vitest';
import { buildCalendar, productiveDaysIn } from '../../../src/domain/time/calendar.js';
import { monthRange } from '../../../src/domain/time/dateRange.js';
import { formatSiteDate } from '../../../src/domain/time/siteDate.js';
import { importHolidays, importStanddowns } from '../../../src/ingest/diary.js';
import { errors, hasErrors, warnings } from '../../../src/ingest/report.js';

const diary = (...rows: string[]) => ['date,reason,note', ...rows].join('\n');

describe('importStanddowns', () => {
  it('reads a clean diary', () => {
    const report = importStanddowns(
      diary('2025-03-05,weather,rain all day', '2025-03-06,ground conditions,'),
    );
    expect(hasErrors(report)).toBe(false);
    expect(report.imported.map((s) => formatSiteDate(s.date))).toEqual([
      '2025-03-05',
      '2025-03-06',
    ]);
    expect(report.imported[0]?.reason).toBe('weather');
    expect(report.imported[0]?.note).toBe('rain all day');
    expect(report.imported[1]?.reason).toBe('ground_conditions');
  });

  it('maps the spellings a site diary actually uses', () => {
    const report = importStanddowns(
      diary(
        '2025-03-05,rain,',
        '2025-03-06,frost,',
        '2025-03-07,waterlogged,',
        '2025-03-10,no access,',
        '2025-03-11,strike,',
      ),
    );
    expect(report.imported.map((s) => s.reason)).toEqual([
      'weather',
      'weather',
      'ground_conditions',
      'access',
      'industrial',
    ]);
    expect(hasErrors(report)).toBe(false);
  });

  it('files an unknown reason as other and warns rather than dropping the day', () => {
    const report = importStanddowns(diary('2025-03-05,badger,'));
    expect(report.imported[0]?.reason).toBe('other');
    expect(warnings(report)[0]?.message).toMatch(/filing it as other/);
    expect(hasErrors(report)).toBe(false);
  });

  it('rejects a row with no date and one with no reason', () => {
    expect(errors(importStanddowns(diary(',weather,')))[0]?.column).toBe('date');
    expect(errors(importStanddowns(diary('2025-03-05,,')))[0]?.column).toBe('reason');
  });

  it('rejects a date that does not exist', () => {
    expect(errors(importStanddowns(diary('2025-02-30,weather,')))[0]?.column).toBe('date');
  });

  it('warns about the same day twice and keeps both, since the calendar dedupes', () => {
    const report = importStanddowns(diary('2025-03-05,weather,', '2025-03-05,access,'));
    expect(report.imported).toHaveLength(2);
    expect(warnings(report)[0]?.message).toBe('2025-03-05 is stood down twice');
    const calendar = buildCalendar({
      id: 'C1',
      name: 'wet march',
      standdowns: report.imported,
    });
    expect(calendar.standdowns).toHaveLength(1);
    expect(calendar.standdowns[0]?.reason).toBe('access');
  });

  it('feeds straight into the calendar', () => {
    const report = importStanddowns(diary('2025-03-05,rain,', '2025-03-06,rain,'));
    const calendar = buildCalendar({
      id: 'C1',
      name: 'wet march',
      standdowns: report.imported,
    });
    expect(productiveDaysIn(calendar, monthRange(2025, 3))).toBe(19);
  });

  it('names a missing column once', () => {
    const report = importStanddowns('date\n2025-03-05');
    expect(report.issues).toHaveLength(1);
    expect(report.issues[0]?.column).toBe('reason');
  });
});

describe('importHolidays', () => {
  it('reads a list of dates', () => {
    const report = importHolidays(
      ['date,name', '2025-04-18,good friday', '2025-04-21,easter monday'].join('\n'),
    );
    expect(report.imported.map(formatSiteDate)).toEqual(['2025-04-18', '2025-04-21']);
    expect(hasErrors(report)).toBe(false);
  });

  it('drops a repeated date with a warning', () => {
    const report = importHolidays(['date', '2025-04-18', '2025-04-18'].join('\n'));
    expect(report.imported).toHaveLength(1);
    expect(warnings(report)[0]?.message).toBe('2025-04-18 is listed twice');
  });

  it('rejects a date it cannot read and keeps going', () => {
    const report = importHolidays(['date', 'easter', '2025-04-21'].join('\n'));
    expect(report.imported.map(formatSiteDate)).toEqual(['2025-04-21']);
    expect(errors(report)).toHaveLength(1);
  });

  it('names a missing column', () => {
    expect(importHolidays('name\ngood friday').issues[0]?.column).toBe('date');
  });
});
