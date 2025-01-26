import { describe, expect, it } from 'vitest';
import { moneyFromMajor, zeroMoney } from '../../../../src/domain/money/money.js';
import { buildCalendar } from '../../../../src/domain/time/calendar.js';
import { buildProgressReport } from '../../../../src/reporting/progressReport.js';
import {
  renderCertificate,
  renderCurve,
  renderPackageTable,
  renderProgressReport,
  renderReconciliation,
  renderStepTable,
} from '../../../../src/reporting/format/text.js';
import { buildSCurve, linearBaseline } from '../../../../src/reporting/sCurve.js';
import { buildCertificate } from '../../../../src/valuation/certificate.js';
import { buildSchedule, periodByNumber } from '../../../../src/valuation/period.js';
import { DEFAULT_RETENTION } from '../../../../src/valuation/retention.js';
import { d, sampleContext } from '../../../support/contextFixtures.js';

const context = sampleContext();
const schedule = buildSchedule({
  firstYear: 2025,
  firstMonth: 1,
  count: 6,
  cutOffDay: 25,
  calendar: buildCalendar({ id: 'C1', name: 'main works' }),
});
const march = periodByNumber(schedule, 3);
const report = buildProgressReport(context, march);

describe('renderPackageTable', () => {
  const text = renderPackageTable(report.lines);

  it('heads the columns and rules them', () => {
    const lines = text.split('\n');
    expect(lines[0]).toMatch(/^package\s+name\s+budget\s+earned\s+percent\s+held$/);
    expect(lines[1]).toMatch(/^-+/);
  });

  it('gives one row per package with the money right aligned', () => {
    const lines = text.split('\n');
    expect(lines).toHaveLength(5);
    expect(lines[2]).toContain('WP-01');
    expect(lines[2]).toContain('£16,152.50');
    expect(lines[2]).toContain('32.5%');
  });

  it('clips a long package name', () => {
    expect(text).toContain('…');
  });
});

describe('renderStepTable', () => {
  it('shows built against credited and what is held back', () => {
    const lines = renderStepTable(report.lines[0]!).split('\n');
    expect(lines[0]).toMatch(/^step\s+built\s+credited\s+gate\s+held back$/);
    expect(lines[3]).toContain('75.0%');
    expect(lines[3]).toContain('50.0%');
    expect(lines[3]).toContain('part open');
    expect(lines[3]).toContain('12+400.000 to 12+600.000');
  });
});

describe('renderProgressReport', () => {
  const text = renderProgressReport(report);

  it('opens with the period heading', () => {
    expect(text.split('\n')[0]).toBe(
      'progress report, valuation 3 to 2025-03-25, issued 2025-04-03, due 2025-04-17',
    );
  });

  it('carries the headline figures', () => {
    expect(text).toMatch(/bill revision +Rev A/);
    expect(text).toMatch(/earned +£16,152\.50/);
    expect(text).toMatch(/working days +20/);
  });

  it('has a section for the work that cannot be paid for', () => {
    expect(text).toContain('work built and not payable');
    expect(text).toContain('hold points outstanding: IP-E1, IP-E2, IP-P1');
  });

  it('lists the reconciliation problems', () => {
    expect(text).toContain('to look at');
    expect(text).toContain('E423.1 is allocated');
  });

  it('leaves out the sections that have nothing in them', () => {
    const clean = buildProgressReport(
      sampleContext({ signOffs: [], ncrs: [], records: [] }),
      march,
    );
    expect(renderProgressReport(clean)).not.toContain('work built and not payable');
    expect(renderProgressReport(clean)).not.toContain('days lost');
  });

  it('lists the days lost when the diary has any', () => {
    const wet = buildProgressReport(
      {
        ...context,
        calendar: buildCalendar({
          id: 'C2',
          name: 'wet march',
          standdowns: [{ date: d('2025-03-05'), reason: 'weather', note: 'rain all day' }],
        }),
      },
      march,
    );
    const text = renderProgressReport(wet);
    expect(text).toContain('days lost');
    expect(text).toContain('2025-03-05  weather  rain all day');
  });
});

describe('renderCertificate', () => {
  const certificate = buildCertificate({
    context,
    period: march,
    contractSum: moneyFromMajor(312_190, 'GBP'),
    retention: DEFAULT_RETENTION,
    previouslyCertified: zeroMoney('GBP'),
  });
  const text = renderCertificate(certificate);

  it('heads the page with the certificate number', () => {
    expect(text.split('\n')[0]).toBe('payment certificate 3');
  });

  it('carries the dates that matter to a payer', () => {
    expect(text).toMatch(/valuation to +2025-03-25/);
    expect(text).toMatch(/payment due +2025-04-17/);
  });

  it('walks down from measured work to the amount due', () => {
    expect(text).toMatch(/measured work +£16,152\.50/);
    expect(text).toMatch(/less retention +£807\.63/);
    expect(text).toMatch(/amount due this certificate +£15,344\.87/);
  });

  it('says so when retention has capped out', () => {
    const capped = buildCertificate({
      context,
      period: march,
      contractSum: moneyFromMajor(1000, 'GBP'),
      retention: DEFAULT_RETENTION,
      previouslyCertified: zeroMoney('GBP'),
    });
    expect(renderCertificate(capped)).toContain('retention has reached its cap');
    expect(text).not.toContain('retention has reached its cap');
  });
});

describe('renderCurve', () => {
  it('gives one row per period', () => {
    const points = buildSCurve(
      context,
      schedule,
      linearBaseline(moneyFromMajor(120_000, 'GBP'), 6),
    );
    const lines = renderCurve(points).split('\n');
    expect(lines).toHaveLength(8);
    expect(lines[0]).toMatch(/^period\s+cut off\s+planned\s+earned\s+variance\s+earned %$/);
    expect(lines[4]).toContain('-£43,847.50');
  });
});

describe('renderReconciliation', () => {
  it('tables the problems', () => {
    const lines = renderReconciliation(report).split('\n');
    expect(lines[0]).toMatch(/^code\s+problem\s+billed\s+allocated$/);
    expect(lines).toHaveLength(5);
    expect(lines[3]).toContain('under allocated');
  });

  it('says so when there is nothing to report', () => {
    const clean = buildProgressReport(sampleContext({ records: [] }), march);
    const empty = { ...clean, reconciliation: [] };
    expect(renderReconciliation(empty)).toBe('the packages reconcile against the bill');
  });
});
