import { describe, expect, it } from 'vitest';
import { NotFoundError } from '../../../../src/errors.js';
import { billTotal, withItem } from '../../../../src/domain/bill/bill.js';
import { parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import type { BillRevision } from '../../../../src/domain/bill/revision.js';
import {
  billAsAt,
  buildBillHistory,
  describeRevision,
  findRevision,
  firstRevision,
  hasRevisionAsAt,
  itemAsAt,
  latestRevision,
  rateAsAt,
  revisionAsAt,
  revisionCount,
  revisionsIn,
  withRevision,
} from '../../../../src/domain/bill/revision.js';
import { moneyMajor } from '../../../../src/domain/money/money.js';
import { rateMajor } from '../../../../src/domain/money/rate.js';
import { monthRange } from '../../../../src/domain/time/dateRange.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';
import { item, sampleBill } from '../../../support/billFixtures.js';

const d = parseSiteDate;

const revA: BillRevision = {
  reference: 'Rev A',
  effectiveFrom: d('2025-01-01'),
  bill: sampleBill(),
  note: 'contract bill at award',
};

const revB: BillRevision = {
  reference: 'Rev B',
  effectiveFrom: d('2025-04-01'),
  bill: withItem(
    sampleBill(),
    item({ code: 'E423.1', rate: 5.2, quantity: 12_400, unit: 'm3' }),
  ),
  note: 'excavation rate agreed after the ground investigation',
};

const revC: BillRevision = {
  reference: 'Rev C',
  effectiveFrom: d('2025-07-01'),
  bill: withItem(revB.bill, item({ code: 'X400.1', rate: 94, quantity: 40, unit: 'm3' })),
  note: 'hand excavation around services added',
};

const history = buildBillHistory([revC, revA, revB]);

describe('buildBillHistory', () => {
  it('sorts revisions by the date they take effect', () => {
    expect(history.revisions.map((r) => r.reference)).toEqual(['Rev A', 'Rev B', 'Rev C']);
    expect(revisionCount(history)).toBe(3);
  });

  it('refuses an empty history', () => {
    expect(() => buildBillHistory([])).toThrow(/at least one revision/);
  });

  it('refuses two revisions taking effect on the same day', () => {
    expect(() => buildBillHistory([revA, { ...revB, effectiveFrom: d('2025-01-01') }])).toThrow(
      /same day/,
    );
  });

  it('refuses two revisions sharing a reference', () => {
    expect(() => buildBillHistory([revA, { ...revB, reference: 'Rev A' }])).toThrow(
      /share a reference/,
    );
  });

  it('names the first and the latest', () => {
    expect(firstRevision(history).reference).toBe('Rev A');
    expect(latestRevision(history).reference).toBe('Rev C');
  });
});

describe('revisionAsAt', () => {
  it('takes the revision in force on the day', () => {
    expect(revisionAsAt(history, d('2025-01-01')).reference).toBe('Rev A');
    expect(revisionAsAt(history, d('2025-03-31')).reference).toBe('Rev A');
    expect(revisionAsAt(history, d('2025-04-01')).reference).toBe('Rev B');
    expect(revisionAsAt(history, d('2025-06-30')).reference).toBe('Rev B');
    expect(revisionAsAt(history, d('2025-12-31')).reference).toBe('Rev C');
  });

  it('refuses a date before any revision was in force', () => {
    expect(() => revisionAsAt(history, d('2024-12-31'))).toThrow(
      /no bill revision was in force/,
    );
    expect(hasRevisionAsAt(history, d('2024-12-31'))).toBe(false);
    expect(hasRevisionAsAt(history, d('2025-01-01'))).toBe(true);
  });
});

describe('rateAsAt', () => {
  const excavation = parseItemCode('E423.1');

  it('gives the rate that was in force, not the latest one', () => {
    expect(rateMajor(rateAsAt(history, d('2025-03-31'), excavation))).toBe(4.85);
    expect(rateMajor(rateAsAt(history, d('2025-04-01'), excavation))).toBe(5.2);
  });

  it('values a march measurement at the march rate after the bill has moved on', () => {
    expect(moneyMajor(billTotal(billAsAt(history, d('2025-03-01'))))).toBe(312_190);
    expect(moneyMajor(billTotal(billAsAt(history, d('2025-05-01'))))).toBe(
      312_190 - 60_140 + 64_480,
    );
  });

  it('refuses an item the bill did not carry on that date', () => {
    const star = parseItemCode('X400.1');
    expect(() => rateAsAt(history, d('2025-05-01'), star)).toThrow(NotFoundError);
    expect(rateMajor(rateAsAt(history, d('2025-08-01'), star))).toBe(94);
  });

  it('reads the whole item, not only the rate', () => {
    expect(itemAsAt(history, d('2025-01-15'), excavation).description).toMatch(/2 to 5 m/);
  });
});

describe('revisionsIn', () => {
  it('finds revisions that take effect inside a period', () => {
    expect(revisionsIn(history, monthRange(2025, 4)).map((r) => r.reference)).toEqual([
      'Rev B',
    ]);
  });

  it('finds nothing in a quiet month', () => {
    expect(revisionsIn(history, monthRange(2025, 5))).toEqual([]);
  });
});

describe('findRevision', () => {
  it('finds by reference', () => {
    expect(findRevision(history, 'Rev B').note).toMatch(/ground investigation/);
  });

  it('throws for a reference nothing carries', () => {
    expect(() => findRevision(history, 'Rev Z')).toThrow(NotFoundError);
  });
});

describe('withRevision', () => {
  it('adds a revision and leaves the original alone', () => {
    const next = withRevision(history, {
      reference: 'Rev D',
      effectiveFrom: d('2025-10-01'),
      bill: sampleBill(),
      note: 'reissued after the variation account was agreed',
    });
    expect(revisionCount(next)).toBe(4);
    expect(revisionCount(history)).toBe(3);
    expect(latestRevision(next).reference).toBe('Rev D');
  });

  it('still refuses a clash', () => {
    expect(() => withRevision(history, { ...revA, reference: 'Rev D' })).toThrow(/same day/);
  });
});

describe('describeRevision', () => {
  it('reads like a transmittal line', () => {
    expect(describeRevision(revA)).toBe('Rev A from 2025-01-01: contract bill at award');
  });
});
