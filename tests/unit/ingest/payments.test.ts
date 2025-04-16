import { describe, expect, it } from 'vitest';
import { moneyMajor } from '../../../src/domain/money/money.js';
import { formatSiteDate } from '../../../src/domain/time/siteDate.js';
import {
  importApplications,
  importNotices,
  importPayLessNotices,
} from '../../../src/ingest/payments.js';
import { errors, rowsAccepted, rowsRejected } from '../../../src/ingest/report.js';

const options = { currency: 'GBP' } as const;

const applicationCsv = [
  'application,subcontract,valuation,applied on,gross,previously paid',
  'SA-01,SC-EARTH,1,2025-01-25,40000.00,0.00',
  'SA-02,SC-EARTH,2,2025-02-25,90000.00,36100.00',
  'SA-03,SC-FENCE,2,2025-02-25,12500.50,',
].join('\n');

describe('importApplications', () => {
  it('reads a clean schedule', () => {
    const report = importApplications(applicationCsv, options);
    expect(report.rowsRead).toBe(3);
    expect(rowsAccepted(report)).toBe(3);
    expect(report.issues).toEqual([]);
  });

  it('reads the figures off each row', () => {
    const [first] = importApplications(applicationCsv, options).imported;
    expect(first!.id).toBe('SA-01');
    expect(first!.subcontractId).toBe('SC-EARTH');
    expect(first!.periodNumber).toBe(1);
    expect(formatSiteDate(first!.appliedOn)).toBe('2025-01-25');
    expect(moneyMajor(first!.gross)).toBe(40_000);
  });

  it('takes an empty previously paid as nothing paid', () => {
    const report = importApplications(applicationCsv, options);
    expect(report.imported[2]!.previouslyPaid.minor).toBe(0);
    expect(moneyMajor(report.imported[2]!.gross)).toBe(12_500.5);
  });

  it('refuses a file missing a column it needs', () => {
    const report = importApplications('application,subcontract\nSA-01,SC-EARTH', options);
    expect(rowsAccepted(report)).toBe(0);
    expect(errors(report)[0]!.message).toContain('valuation');
  });

  it('rejects the row with the unreadable date and keeps the rest', () => {
    const text = [
      'application,subcontract,valuation,applied on,gross',
      'SA-01,SC-EARTH,1,25/01/2025,40000.00',
      'SA-02,SC-EARTH,2,2025-02-25,90000.00',
    ].join('\n');
    const report = importApplications(text, options);
    expect(rowsAccepted(report)).toBe(1);
    expect(rowsRejected(report)).toBe(1);
    expect(errors(report)[0]!.line).toBe(2);
  });

  it('rejects the second row that carries an application id already used', () => {
    const text = [
      'application,subcontract,valuation,applied on,gross',
      'SA-01,SC-EARTH,1,2025-01-25,40000.00',
      'SA-01,SC-EARTH,2,2025-02-25,90000.00',
    ].join('\n');
    const report = importApplications(text, options);
    expect(rowsAccepted(report)).toBe(1);
    expect(errors(report)[0]!.message).toContain('appears twice');
  });

  it('rejects a valuation number that is not a number', () => {
    const text = [
      'application,subcontract,valuation,applied on,gross',
      'SA-01,SC-EARTH,first,2025-01-25,40000.00',
    ].join('\n');
    expect(rowsAccepted(importApplications(text, options))).toBe(0);
  });
});

describe('importNotices', () => {
  const csv = [
    'application,served on,valued',
    'SA-01,2025-02-27,38000.00',
    'SA-02,2025-03-28,85000.00',
  ].join('\n');

  it('reads what the payer said each application was worth', () => {
    const report = importNotices(csv, options);
    expect(rowsAccepted(report)).toBe(2);
    expect(report.imported[1]!.applicationId).toBe('SA-02');
    expect(moneyMajor(report.imported[1]!.valued)).toBe(85_000);
  });

  it('refuses a second notice against the same application', () => {
    const text = [
      'application,served on,valued',
      'SA-01,2025-02-27,38000.00',
      'SA-01,2025-02-28,37000.00',
    ].join('\n');
    const report = importNotices(text, options);
    expect(rowsAccepted(report)).toBe(1);
    expect(errors(report)[0]!.message).toContain('two notices');
  });

  it('rejects a row with no application named', () => {
    const report = importNotices('application,served on,valued\n,2025-02-27,38000.00', options);
    expect(rowsAccepted(report)).toBe(0);
  });
});

describe('importPayLessNotices', () => {
  const csv = [
    'application,served on,amount,reason',
    'SA-02,2025-05-30,20000.00,capping laid outside tolerance at 12+500',
  ].join('\n');

  it('carries the reason the notice has to give', () => {
    const report = importPayLessNotices(csv, options);
    expect(rowsAccepted(report)).toBe(1);
    expect(report.imported[0]!.reason).toContain('outside tolerance');
    expect(moneyMajor(report.imported[0]!.amount)).toBe(20_000);
  });

  it('rejects a notice with no reason given', () => {
    const text = ['application,served on,amount,reason', 'SA-02,2025-05-30,20000.00,'].join(
      '\n',
    );
    expect(rowsAccepted(importPayLessNotices(text, options))).toBe(0);
  });

  it('refuses two pay less notices against one application', () => {
    const text = [
      'application,served on,amount,reason',
      'SA-02,2025-05-30,20000.00,first',
      'SA-02,2025-05-31,19000.00,second',
    ].join('\n');
    const report = importPayLessNotices(text, options);
    expect(errors(report)[0]!.message).toContain('two pay less notices');
  });
});
