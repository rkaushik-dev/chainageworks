import { describe, expect, it } from 'vitest';
import { NotFoundError } from '../../../../src/errors.js';
import { moneyFromMajor, moneyMajor } from '../../../../src/domain/money/money.js';
import { percent } from '../../../../src/domain/money/percent.js';
import { rate } from '../../../../src/domain/money/rate.js';
import {
  buildDayworkSchedule,
  dayworkRate,
  DEFAULT_ADDITIONS,
  describeSchedule,
  findLabourRate,
  requirePlantRate,
} from '../../../../src/domain/daywork/schedule.js';
import type { DayworkSheetInput } from '../../../../src/domain/daywork/sheet.js';
import {
  buildDayworkSheet,
  dayworkValue,
  describeSheet,
  isSignedAt,
  unsignedSheets,
  valueSheet,
} from '../../../../src/domain/daywork/sheet.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';

const d = parseSiteDate;

const schedule = buildDayworkSchedule({
  labour: [
    dayworkRate('ganger', 'ganger', 24),
    dayworkRate('labourer', 'general labourer', 19.5),
  ],
  plant: [
    dayworkRate('excavator-13t', '13 tonne tracked excavator', 42),
    dayworkRate('dumper-6t', '6 tonne dumper', 18),
  ],
});

const base: DayworkSheetInput = {
  id: 'DW-01',
  reference: 'DW/2025/014',
  packageId: 'WP-01',
  instruction: 'EI-045',
  workedOn: d('2025-03-12'),
  signedOn: d('2025-03-13'),
  signedBy: 'R Engineer',
  labour: [{ rateId: 'ganger', people: 1, hours: 8, overtimeHours: 0 }],
  plant: [{ rateId: 'excavator-13t', hours: 8, overtimeHours: 0 }],
  materials: [{ description: 'imported stone', value: moneyFromMajor(400, 'GBP') }],
};

const sheet = buildDayworkSheet(base);

describe('buildDayworkSchedule', () => {
  it('carries the contract additions and an overtime factor', () => {
    expect(schedule.additions).toEqual(DEFAULT_ADDITIONS);
    expect(schedule.overtimeFactor).toBe(1.5);
    expect(describeSchedule(schedule)).toBe('2 labour rates, 2 plant rates, overtime at 1.50');
  });

  it('takes an addition of its own', () => {
    const cheap = buildDayworkSchedule({
      labour: [dayworkRate('ganger', 'ganger', 24)],
      plant: [],
      additions: { labour: percent(50) },
    });
    expect(cheap.additions.labour).toEqual(percent(50));
    expect(cheap.additions.plant).toEqual(DEFAULT_ADDITIONS.plant);
  });

  it('refuses two rates sharing an id', () => {
    expect(() =>
      buildDayworkSchedule({
        labour: [dayworkRate('a', 'a', 1), dayworkRate('a', 'b', 2)],
        plant: [],
      }),
    ).toThrow(/share an id/);
  });

  it('refuses a rate in another currency and a negative one', () => {
    expect(() =>
      buildDayworkSchedule({
        labour: [{ id: 'a', description: 'a', rate: rate(10, 'EUR', 'hr') }],
        plant: [],
      }),
    ).toThrow(/another currency/);
    expect(() =>
      buildDayworkSchedule({ labour: [dayworkRate('a', 'a', -1)], plant: [] }),
    ).toThrow(/cannot be negative/);
  });

  it('refuses an overtime factor under one', () => {
    expect(() => buildDayworkSchedule({ labour: [], plant: [], overtimeFactor: 0.5 })).toThrow(
      /at least one/,
    );
  });

  it('finds rates and throws for one it does not carry', () => {
    expect(findLabourRate(schedule, 'ganger')?.description).toBe('ganger');
    expect(findLabourRate(schedule, 'nope')).toBeNull();
    expect(() => requirePlantRate(schedule, 'nope')).toThrow(NotFoundError);
  });
});

describe('buildDayworkSheet', () => {
  it('keeps what it was given', () => {
    expect(sheet.reference).toBe('DW/2025/014');
    expect(sheet.labour).toHaveLength(1);
    expect(isSignedAt(sheet, d('2025-03-31'))).toBe(true);
  });

  it('allows an unsigned sheet', () => {
    const unsigned = buildDayworkSheet({ ...base, signedOn: null, signedBy: '' });
    expect(unsigned.signedOn).toBeNull();
    expect(isSignedAt(unsigned, d('2025-12-31'))).toBe(false);
  });

  it('refuses a signed sheet with nobody named', () => {
    expect(() => buildDayworkSheet({ ...base, signedBy: '  ' })).toThrow(/name who signed it/);
  });

  it('refuses a sheet signed before the work was done', () => {
    expect(() => buildDayworkSheet({ ...base, signedOn: d('2025-03-01') })).toThrow(
      /signed before the work was done/,
    );
  });

  it('refuses hours that are negative or longer than a day', () => {
    expect(() =>
      buildDayworkSheet({
        ...base,
        labour: [{ rateId: 'ganger', people: 1, hours: -1, overtimeHours: 0 }],
      }),
    ).toThrow(/cannot be negative/);
    expect(() =>
      buildDayworkSheet({
        ...base,
        plant: [{ rateId: 'dumper-6t', hours: 30, overtimeHours: 0 }],
      }),
    ).toThrow(/more than a day/);
  });

  it('refuses a labour line with nobody on it', () => {
    expect(() =>
      buildDayworkSheet({
        ...base,
        labour: [{ rateId: 'ganger', people: 0, hours: 8, overtimeHours: 0 }],
      }),
    ).toThrow(/at least one person/);
  });

  it('refuses an empty sheet', () => {
    expect(() => buildDayworkSheet({ ...base, labour: [], plant: [], materials: [] })).toThrow(
      /nothing on it/,
    );
  });
});

describe('valueSheet', () => {
  it('adds each head separately before adding them together', () => {
    const value = valueSheet(schedule, sheet);
    expect(moneyMajor(value.labour)).toBe(360.96);
    expect(moneyMajor(value.plant)).toBe(378);
    expect(moneyMajor(value.materials)).toBe(450);
    expect(moneyMajor(value.total)).toBe(1188.96);
  });

  it('multiplies a labour line by the number of people', () => {
    const gang = buildDayworkSheet({
      ...base,
      labour: [{ rateId: 'labourer', people: 4, hours: 6, overtimeHours: 0 }],
      plant: [],
      materials: [],
    });
    expect(moneyMajor(valueSheet(schedule, gang).labour)).toBe(879.84);
  });

  it('charges overtime at the contract factor and not on the addition', () => {
    const evening = buildDayworkSheet({
      ...base,
      labour: [{ rateId: 'ganger', people: 1, hours: 8, overtimeHours: 2 }],
      plant: [],
      materials: [],
    });
    expect(moneyMajor(valueSheet(schedule, evening).labour)).toBe(496.32);
  });

  it('values a sheet with only materials', () => {
    const supply = buildDayworkSheet({ ...base, labour: [], plant: [] });
    const value = valueSheet(schedule, supply);
    expect(value.labour.minor).toBe(0);
    expect(moneyMajor(value.total)).toBe(450);
  });

  it('refuses a rate the schedule does not carry', () => {
    const stray = buildDayworkSheet({
      ...base,
      labour: [{ rateId: 'foreman', people: 1, hours: 8, overtimeHours: 0 }],
    });
    expect(() => valueSheet(schedule, stray)).toThrow(NotFoundError);
  });
});

describe('dayworkValue', () => {
  const later = buildDayworkSheet({
    ...base,
    id: 'DW-02',
    reference: 'DW/2025/015',
    workedOn: d('2025-03-26'),
    signedOn: d('2025-04-02'),
  });
  const unsigned = buildDayworkSheet({
    ...base,
    id: 'DW-03',
    reference: 'DW/2025/016',
    workedOn: d('2025-03-20'),
    signedOn: null,
    signedBy: '',
  });
  const sheets = [sheet, later, unsigned];

  it('counts only what was signed by the cut off', () => {
    expect(moneyMajor(dayworkValue(schedule, sheets, d('2025-03-31')))).toBe(1188.96);
  });

  it('picks up the late signature in the next period', () => {
    expect(moneyMajor(dayworkValue(schedule, sheets, d('2025-04-30')))).toBe(2377.92);
  });

  it('is nothing when nothing has been signed', () => {
    expect(dayworkValue(schedule, [unsigned], d('2025-12-31')).minor).toBe(0);
    expect(dayworkValue(schedule, [], d('2025-12-31')).minor).toBe(0);
  });

  it('lists what is waiting on a signature', () => {
    expect(unsignedSheets(sheets, d('2025-03-31')).map((s) => s.id)).toEqual([
      'DW-02',
      'DW-03',
    ]);
  });

  it('does not chase a sheet for work that has not happened yet', () => {
    expect(unsignedSheets(sheets, d('2025-03-15')).map((s) => s.id)).toEqual([]);
  });
});

describe('describeSheet', () => {
  it('reads like a register line', () => {
    expect(describeSheet(sheet)).toBe('DW/2025/014 worked 2025-03-12, signed 2025-03-13');
    const unsigned = buildDayworkSheet({ ...base, signedOn: null, signedBy: '' });
    expect(describeSheet(unsigned)).toContain('unsigned');
  });
});
