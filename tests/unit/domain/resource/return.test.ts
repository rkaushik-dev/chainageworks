import { describe, expect, it } from 'vitest';
import type { ResourceLine } from '../../../../src/domain/resource/return.js';
import {
  buildResourceReturn,
  describeReturn,
  hoursOfKind,
  lineHours,
  lostFraction,
  returnsFor,
  returnsIn,
  returnsTo,
  totalHours,
  totalResources,
} from '../../../../src/domain/resource/return.js';
import { dateRange } from '../../../../src/domain/time/dateRange.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';

const d = parseSiteDate;

const gang: ResourceLine = {
  resourceId: 'GANG-EARTH',
  description: 'earthworks gang',
  count: 6,
  hours: 8,
  kind: 'working',
};

const standing: ResourceLine = {
  resourceId: 'GANG-EARTH',
  description: 'earthworks gang',
  count: 6,
  hours: 2,
  kind: 'standing',
};

const excavator: ResourceLine = {
  resourceId: 'PL-360-22T',
  description: '22 tonne tracked excavator',
  count: 2,
  hours: 8,
  kind: 'working',
};

const monday = buildResourceReturn({
  id: 'RR-01',
  packageId: 'WP-01',
  stepId: 'excavate',
  date: d('2025-03-03'),
  labour: [gang, standing],
  plant: [excavator],
  returnedBy: 'A Kerrigan',
});

const tuesday = buildResourceReturn({
  id: 'RR-02',
  packageId: 'WP-01',
  date: d('2025-03-04'),
  shift: 'night',
  labour: [{ ...gang, hours: 10 }],
  plant: [{ ...excavator, hours: 4, kind: 'idle' }],
  returnedBy: 'A Kerrigan',
  note: 'water main strike, stood down after four hours',
});

const elsewhere = buildResourceReturn({
  id: 'RR-03',
  packageId: 'WP-02',
  date: d('2025-03-05'),
  labour: [{ ...gang, count: 4 }],
  returnedBy: 'D Illingworth',
});

describe('buildResourceReturn', () => {
  it('fills in the shift, the step and the note', () => {
    expect(monday.shift).toBe('day');
    expect(elsewhere.stepId).toBeNull();
    expect(elsewhere.note).toBe('');
    expect(tuesday.shift).toBe('night');
  });

  it('refuses a return with nothing on it', () => {
    expect(() =>
      buildResourceReturn({
        id: 'RR-99',
        packageId: 'WP-01',
        date: d('2025-03-03'),
        returnedBy: 'A Kerrigan',
      }),
    ).toThrow(/says nothing/);
  });

  it('refuses a line covering less than one of something', () => {
    expect(() =>
      buildResourceReturn({
        id: 'RR-99',
        packageId: 'WP-01',
        date: d('2025-03-03'),
        labour: [{ ...gang, count: 0 }],
        returnedBy: 'A Kerrigan',
      }),
    ).toThrow(/at least one of something/);
  });

  it('refuses negative hours', () => {
    expect(() =>
      buildResourceReturn({
        id: 'RR-99',
        packageId: 'WP-01',
        date: d('2025-03-03'),
        plant: [{ ...excavator, hours: -1 }],
        returnedBy: 'A Kerrigan',
      }),
    ).toThrow(/cannot be negative/);
  });

  it('refuses a day longer than a day', () => {
    expect(() =>
      buildResourceReturn({
        id: 'RR-99',
        packageId: 'WP-01',
        date: d('2025-03-03'),
        labour: [{ ...gang, hours: 25 }],
        returnedBy: 'A Kerrigan',
      }),
    ).toThrow(/twenty four hours/);
  });
});

describe('counting hours', () => {
  it('multiplies the count by the hours', () => {
    expect(lineHours(gang)).toBe(48);
    expect(lineHours(excavator)).toBe(16);
  });

  it('adds up a set of lines', () => {
    expect(totalHours(monday.labour)).toBe(60);
  });

  it('picks out one kind of hour', () => {
    expect(hoursOfKind(monday.labour, 'working')).toBe(48);
    expect(hoursOfKind(monday.labour, 'standing')).toBe(12);
    expect(hoursOfKind(monday.labour, 'idle')).toBe(0);
  });
});

describe('totalResources', () => {
  it('keeps labour and plant apart', () => {
    const totals = totalResources([monday, tuesday]);
    expect(totals.labourHours).toBe(120);
    expect(totals.plantHours).toBe(24);
    expect(totals.returns).toBe(2);
    expect(totals.days).toBe(2);
  });

  it('adds the kinds across both', () => {
    const totals = totalResources([monday, tuesday]);
    expect(totals.workingHours).toBe(124);
    expect(totals.standingHours).toBe(12);
    expect(totals.idleHours).toBe(8);
  });

  it('counts a second return on the same day once', () => {
    const again = buildResourceReturn({
      id: 'RR-04',
      packageId: 'WP-02',
      date: d('2025-03-03'),
      labour: [gang],
      returnedBy: 'D Illingworth',
    });
    expect(totalResources([monday, again]).days).toBe(1);
  });

  it('is all zeroes on nothing at all', () => {
    expect(totalResources([])).toEqual({
      labourHours: 0,
      plantHours: 0,
      workingHours: 0,
      standingHours: 0,
      idleHours: 0,
      returns: 0,
      days: 0,
    });
  });
});

describe('picking returns out', () => {
  const all = [monday, tuesday, elsewhere];

  it('takes the ones against a package', () => {
    expect(returnsFor(all, 'WP-01').map((r) => r.id)).toEqual(['RR-01', 'RR-02']);
  });

  it('takes the ones inside a date range', () => {
    const range = dateRange(d('2025-03-04'), d('2025-03-05'));
    expect(returnsIn(all, range).map((r) => r.id)).toEqual(['RR-02', 'RR-03']);
  });

  it('takes the ones up to a cut off, including the day itself', () => {
    expect(returnsTo(all, d('2025-03-04')).map((r) => r.id)).toEqual(['RR-01', 'RR-02']);
  });
});

describe('lostFraction', () => {
  it('is the standing and idle hours over everything', () => {
    expect(lostFraction(totalResources([monday, tuesday]))).toBeCloseTo(20 / 144, 6);
  });

  it('is nothing when every hour worked', () => {
    expect(lostFraction(totalResources([elsewhere]))).toBe(0);
  });

  it('is nothing rather than a division by zero on an empty set', () => {
    expect(lostFraction(totalResources([]))).toBe(0);
  });
});

describe('describeReturn', () => {
  it('says the day in one line', () => {
    expect(describeReturn(monday)).toBe(
      'RR-01 WP-01 2025-03-03 day, 60 labour hours, 16 plant hours',
    );
  });
});
