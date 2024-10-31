import { describe, expect, it } from 'vitest';
import { ConflictError } from '../../../../src/errors.js';
import { parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import { chainageRange } from '../../../../src/domain/chainage/range.js';
import { parseStation } from '../../../../src/domain/chainage/station.js';
import { formatQuantity, quantity } from '../../../../src/domain/quantity/quantity.js';
import {
  buildSurveyRecord,
  effectiveRecords,
  findDoubleSupersessions,
  indexRecords,
  isRemeasure,
  matchesQuery,
  measuredQuantity,
  selectRecords,
  supersessionChain,
} from '../../../../src/domain/survey/record.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';
import { record, records } from '../../../support/surveyFixtures.js';

const d = parseSiteDate;

const book = records([
  {
    id: 'SR-01',
    code: 'E423.1',
    stepId: 'excavate',
    from: '12+000',
    to: '12+200',
    amount: 1800,
    unit: 'm3',
    on: '2025-03-06',
  },
  {
    id: 'SR-02',
    code: 'E423.1',
    stepId: 'excavate',
    from: '12+200',
    to: '12+400',
    amount: 1750,
    unit: 'm3',
    on: '2025-03-13',
  },
  {
    id: 'SR-03',
    code: 'E423.1',
    stepId: 'excavate',
    from: '12+000',
    to: '12+200',
    amount: 1920,
    unit: 'm3',
    on: '2025-04-08',
    supersedes: 'SR-01',
  },
  {
    id: 'SR-04',
    code: 'E532.1',
    stepId: 'place',
    from: '12+000',
    to: '12+400',
    amount: 2400,
    unit: 'm3',
    on: '2025-03-20',
    packageId: 'WP-01',
  },
  {
    id: 'SR-05',
    code: 'E423.1',
    stepId: 'excavate',
    from: '12+800',
    to: '13+000',
    amount: 900,
    unit: 'm3',
    on: '2025-03-25',
    packageId: 'WP-02',
  },
]);

describe('buildSurveyRecord', () => {
  it('defaults the method to a tape and the note to nothing', () => {
    const r = record({
      id: 'SR-99',
      code: 'E423.1',
      stepId: 'excavate',
      from: '0+000',
      to: '0+100',
      amount: 1,
      unit: 'm3',
      on: '2025-03-01',
    });
    expect(r.method).toBe('tape');
    expect(r.note).toBe('');
    expect(r.supersedes).toBeNull();
    expect(isRemeasure(r)).toBe(false);
  });

  it('refuses a record with no id and one with nobody named', () => {
    const base = {
      packageId: 'WP-01',
      code: parseItemCode('E423.1'),
      stepId: 'excavate',
      extent: chainageRange(parseStation('0+000'), parseStation('0+100')),
      quantity: quantity(1, 'm3'),
      measuredOn: d('2025-03-01'),
    };
    expect(() => buildSurveyRecord({ ...base, id: ' ', bookedBy: 'J' })).toThrow(/needs an id/);
    expect(() => buildSurveyRecord({ ...base, id: 'SR', bookedBy: '  ' })).toThrow(
      /name who booked it/,
    );
  });

  it('refuses a record that supersedes itself', () => {
    expect(() =>
      record({
        id: 'SR-98',
        code: 'E423.1',
        stepId: 'excavate',
        from: '0+000',
        to: '0+100',
        amount: 1,
        unit: 'm3',
        on: '2025-03-01',
        supersedes: 'SR-98',
      }),
    ).toThrow(/cannot supersede itself/);
  });

  it('refuses two records sharing an id', () => {
    expect(() => indexRecords([book[0]!, book[0]!])).toThrow(/share an id/);
  });
});

describe('supersessionChain', () => {
  it('walks back to the first booking', () => {
    expect(supersessionChain(book, 'SR-03').map((r) => r.id)).toEqual(['SR-03', 'SR-01']);
  });

  it('is a single record when nothing was superseded', () => {
    expect(supersessionChain(book, 'SR-02').map((r) => r.id)).toEqual(['SR-02']);
  });

  it('stops when the record it names is not in the set', () => {
    const orphan = record({
      id: 'SR-90',
      code: 'E423.1',
      stepId: 'excavate',
      from: '0+000',
      to: '0+100',
      amount: 1,
      unit: 'm3',
      on: '2025-03-01',
      supersedes: 'SR-missing',
    });
    expect(supersessionChain([orphan], 'SR-90').map((r) => r.id)).toEqual(['SR-90']);
  });

  it('refuses a loop rather than spinning', () => {
    const a = record({
      id: 'A',
      code: 'E423.1',
      stepId: 'excavate',
      from: '0+000',
      to: '0+100',
      amount: 1,
      unit: 'm3',
      on: '2025-03-01',
      supersedes: 'B',
    });
    const b = record({
      id: 'B',
      code: 'E423.1',
      stepId: 'excavate',
      from: '0+000',
      to: '0+100',
      amount: 1,
      unit: 'm3',
      on: '2025-03-02',
      supersedes: 'A',
    });
    expect(() => supersessionChain([a, b], 'A')).toThrow(ConflictError);
  });
});

describe('findDoubleSupersessions', () => {
  it('finds nothing in a clean book', () => {
    expect(findDoubleSupersessions(book)).toEqual([]);
  });

  it('finds a record two remeasures both claim to replace', () => {
    const rival = record({
      id: 'SR-06',
      code: 'E423.1',
      stepId: 'excavate',
      from: '12+000',
      to: '12+200',
      amount: 1880,
      unit: 'm3',
      on: '2025-04-09',
      supersedes: 'SR-01',
    });
    expect(findDoubleSupersessions([...book, rival])).toEqual(['SR-01']);
  });
});

describe('effectiveRecords', () => {
  it('leaves out anything measured after the date', () => {
    expect(effectiveRecords(book, d('2025-03-10')).map((r) => r.id)).toEqual(['SR-01']);
  });

  it('keeps a record whose remeasure has not happened yet', () => {
    const march = effectiveRecords(book, d('2025-03-31')).map((r) => r.id);
    expect(march).toContain('SR-01');
    expect(march).not.toContain('SR-03');
  });

  it('drops the superseded record once the remeasure is in date', () => {
    const april = effectiveRecords(book, d('2025-04-30')).map((r) => r.id);
    expect(april).not.toContain('SR-01');
    expect(april).toContain('SR-03');
  });
});

describe('selectRecords', () => {
  it('filters by package, item and step', () => {
    const asAt = d('2025-03-31');
    expect(selectRecords(book, asAt, { packageId: 'WP-02' }).map((r) => r.id)).toEqual([
      'SR-05',
    ]);
    expect(
      selectRecords(book, asAt, { code: parseItemCode('E532.1') }).map((r) => r.id),
    ).toEqual(['SR-04']);
    expect(selectRecords(book, asAt, { stepId: 'place' }).map((r) => r.id)).toEqual(['SR-04']);
  });

  it('sorts by the date measured', () => {
    expect(selectRecords(book, d('2025-03-31')).map((r) => r.id)).toEqual([
      'SR-01',
      'SR-02',
      'SR-04',
      'SR-05',
    ]);
  });

  it('takes an empty query as everything', () => {
    expect(selectRecords(book, d('2025-03-10'))).toHaveLength(1);
  });

  it('matches a query directly too', () => {
    expect(matchesQuery(book[0]!, { packageId: 'WP-01', stepId: 'excavate' })).toBe(true);
    expect(matchesQuery(book[0]!, { packageId: 'WP-02' })).toBe(false);
    expect(matchesQuery(book[0]!, { code: parseItemCode('E532.1') })).toBe(false);
  });
});

describe('measuredQuantity', () => {
  const query = { packageId: 'WP-01', code: parseItemCode('E423.1'), stepId: 'excavate' };

  it('adds the records that count on the date', () => {
    expect(formatQuantity(measuredQuantity(book, d('2025-03-31'), query)!)).toBe('3550.00 m3');
  });

  it('picks up the remeasure once it is in date', () => {
    expect(formatQuantity(measuredQuantity(book, d('2025-04-30'), query)!)).toBe('3670.00 m3');
  });

  it('does not let a later remeasure change an earlier answer', () => {
    expect(formatQuantity(measuredQuantity(book, d('2025-03-31'), query)!)).toBe('3550.00 m3');
  });

  it('gives nothing when nothing matched', () => {
    expect(measuredQuantity(book, d('2025-01-01'), query)).toBeNull();
    expect(measuredQuantity(book, d('2025-04-30'), { packageId: 'WP-09' })).toBeNull();
  });
});
