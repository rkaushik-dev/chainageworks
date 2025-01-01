import { describe, expect, it } from 'vitest';
import { billTotal, requireItem } from '../../../src/domain/bill/bill.js';
import { formatItemCode, parseItemCode } from '../../../src/domain/bill/itemCode.js';
import { formatChainageRange } from '../../../src/domain/chainage/range.js';
import { moneyMajor } from '../../../src/domain/money/money.js';
import { rateMajor } from '../../../src/domain/money/rate.js';
import { formatQuantity } from '../../../src/domain/quantity/quantity.js';
import {
  BILL_COLUMNS,
  exportBill,
  importBill,
  importBillItems,
} from '../../../src/ingest/bill.js';
import { errors, hasErrors } from '../../../src/ingest/report.js';
import { sampleBill } from '../../support/billFixtures.js';

const header = BILL_COLUMNS.join(',');
const sheet = (...rows: string[]) => [header, ...rows].join('\n');

const excavation = 'E423.1,excavation for cuttings,m3,4.85,12400';

describe('importBillItems', () => {
  it('reads a clean row', () => {
    const report = importBillItems(sheet(excavation));
    expect(hasErrors(report)).toBe(false);
    const item = report.imported[0]!;
    expect(formatItemCode(item.code)).toBe('E423.1');
    expect(item.description).toBe('excavation for cuttings');
    expect(rateMajor(item.rate)).toBe(4.85);
    expect(formatQuantity(item.quantity)).toBe('12400.00 m3');
    expect(item.kind).toBe('measured');
    expect(item.chainage).toBeNull();
  });

  it('reads a rate with a currency symbol and separators', () => {
    const report = importBillItems(sheet('A213.1,establish site,sum,"£48,500.00",1'));
    expect(rateMajor(report.imported[0]!.rate)).toBe(48_500);
  });

  it('defaults a sum item to a lump and everything else to measured', () => {
    const report = importBillItems(sheet(excavation, 'A213.1,establish site,sum,48500,1'));
    expect(report.imported.map((i) => i.kind)).toEqual(['measured', 'lump']);
  });

  it('reads the kind column when it is there', () => {
    const report = importBillItems(
      [`${header},kind`, 'E423.9,rock excavation,m3,28,500,provisional'].join('\n'),
    );
    expect(report.imported[0]?.kind).toBe('provisional');
  });

  it('reads a kind written with a space', () => {
    const report = importBillItems(
      [`${header},kind`, 'A253.1,undertaker diversion,sum,120000,1,prime cost'].join('\n'),
    );
    expect(report.imported[0]?.kind).toBe('prime_cost');
  });

  it('rejects a kind it does not know', () => {
    const report = importBillItems(
      [`${header},kind`, 'E423.1,excavation,m3,4.85,100,guesswork'].join('\n'),
    );
    expect(errors(report)[0]?.column).toBe('kind');
  });

  it('reads the chainage columns when both are there', () => {
    const report = importBillItems(
      [`${header},from,to`, 'R712.1,sub base,m2,11.4,4000,12+000,12+800'].join('\n'),
    );
    expect(formatChainageRange(report.imported[0]!.chainage!)).toBe('12+000.000 to 12+800.000');
  });

  it('leaves the chainage null when only one end is given', () => {
    const report = importBillItems(
      [`${header},from,to`, 'R712.1,sub base,m2,11.4,4000,12+000,'].join('\n'),
    );
    expect(report.imported[0]?.chainage).toBeNull();
  });

  it('rejects a duplicate code and keeps the first', () => {
    const report = importBillItems(sheet(excavation, excavation));
    expect(report.imported).toHaveLength(1);
    expect(errors(report)[0]?.message).toBe('item E423.1 appears twice');
  });

  it('rejects a lump item that is not measured in a sum', () => {
    const report = importBillItems(
      [`${header},kind`, 'A213.1,establish site,m3,48500,1,lump'].join('\n'),
    );
    expect(errors(report)[0]?.message).toMatch(/billed as a sum/);
  });

  it('rejects a negative quantity and an empty description', () => {
    expect(errors(importBillItems(sheet('E423.1,excavation,m3,4.85,-1')))).toHaveLength(1);
    expect(errors(importBillItems(sheet('E423.1,,m3,4.85,100')))[0]?.column).toBe(
      'description',
    );
  });

  it('names every missing column once', () => {
    const report = importBillItems('code,description\nE423.1,excavation');
    expect(report.issues).toHaveLength(3);
    expect(report.imported).toEqual([]);
  });
});

describe('importBill', () => {
  it('builds a whole bill', () => {
    const bill = importBill(sheet(excavation, 'E532.1,filling,m3,3.15,9800'), {
      id: 'B9',
      title: 'test bill',
    });
    expect(bill.id).toBe('B9');
    expect(bill.items).toHaveLength(2);
    expect(moneyMajor(billTotal(bill))).toBe(12_400 * 4.85 + 9800 * 3.15);
  });

  it('throws on the first error rather than returning a part bill', () => {
    expect(() => importBill(sheet('nonsense,excavation,m3,4.85,100'))).toThrow(/line 2/);
  });

  it('reports a missing column with no line number', () => {
    expect(() => importBill('code\nE423.1')).toThrow(/the file has no description column/);
  });

  it('takes a currency other than sterling', () => {
    const bill = importBill(sheet(excavation), { currency: 'EUR' });
    expect(bill.currency).toBe('EUR');
    expect(requireItem(bill, parseItemCode('E423.1')).rate.currency).toBe('EUR');
  });
});

describe('exportBill', () => {
  it('writes a header and one line per item', () => {
    const text = exportBill(sampleBill());
    const lines = text.trimEnd().split('\n');
    expect(lines[0]).toBe('code,description,unit,rate,quantity,kind,from,to');
    expect(lines).toHaveLength(9);
  });

  it('round trips through the importer', () => {
    const original = sampleBill();
    const again = importBill(exportBill(original), {
      id: original.id,
      title: original.title,
      currency: original.currency,
    });
    expect(billTotal(again)).toEqual(billTotal(original));
    expect(again.items.map((i) => formatItemCode(i.code))).toEqual(
      original.items.map((i) => formatItemCode(i.code)),
    );
    expect(again.items.map((i) => i.kind)).toEqual(original.items.map((i) => i.kind));
  });
});
