import { describe, expect, it } from 'vitest';
import { BadInputError, ConflictError } from '../../../src/errors.js';
import { billTotal } from '../../../src/domain/bill/bill.js';
import { moneyMajor } from '../../../src/domain/money/money.js';
import { formatSiteDate } from '../../../src/domain/time/siteDate.js';
import { DEFAULT_CONFIG, mergeConfig } from '../../../src/config/config.js';
import { hasErrors } from '../../../src/ingest/report.js';
import {
  certificateFor,
  createService,
  curveFor,
  loadBill,
  loadDiary,
  loadHolidays,
  loadNcrs,
  loadRecords,
  loadSignOffs,
  periodOf,
  progressFor,
  scheduleOf,
  stripFor,
} from '../../../src/app/service.js';
import { d } from '../../support/contextFixtures.js';
import { sampleJob } from '../../support/jobFixtures.js';

const service = () => createService(sampleJob(), DEFAULT_CONFIG);

describe('scheduleOf', () => {
  it('anchors on the month the first bill took effect', () => {
    const schedule = scheduleOf(service(), 6);
    expect(formatSiteDate(schedule.periods[0]!.cutOff)).toBe('2025-01-24');
    expect(schedule.periods).toHaveLength(6);
  });

  it('numbers periods the same however far out it is asked for', () => {
    expect(formatSiteDate(periodOf(service(), 3).cutOff)).toBe('2025-03-25');
    expect(formatSiteDate(scheduleOf(service(), 60).periods[2]!.cutOff)).toBe('2025-03-25');
  });

  it('refuses a valuation number that is not a whole number from one', () => {
    expect(() => periodOf(service(), 0)).toThrow(/counts from one/);
    expect(() => periodOf(service(), 1.5)).toThrow(BadInputError);
  });
});

describe('progressFor', () => {
  it('reports the period asked for', () => {
    const report = progressFor(service(), 3);
    expect(report.period.number).toBe(3);
    expect(moneyMajor(report.earned)).toBe(16_152.5);
  });

  it('refuses when strict reconciliation is on and the register does not balance', () => {
    const strict = createService(
      sampleJob(),
      mergeConfig(DEFAULT_CONFIG, { strictReconciliation: true }),
    );
    expect(() => progressFor(strict, 3)).toThrow(ConflictError);
    expect(() => progressFor(strict, 3)).toThrow(/do not reconcile/);
  });
});

describe('certificateFor', () => {
  it('runs the series from period one so the previous figure is right', () => {
    const certificate = certificateFor(service(), 3);
    expect(certificate.period.number).toBe(3);
    expect(certificate.previouslyCertified.minor).toBe(0);
    expect(moneyMajor(certificate.amountDue)).toBe(15_344.87);
  });

  it('pays nothing in the month after, because nothing more was measured', () => {
    expect(certificateFor(service(), 4).amountDue.minor).toBe(0);
    expect(moneyMajor(certificateFor(service(), 4).netValuation)).toBe(15_344.87);
  });

  it('takes the materials on the job into the gross', () => {
    const withMaterials = service();
    expect(withMaterials.job.materials.count()).toBe(0);
    expect(moneyMajor(certificateFor(withMaterials, 3).materialsOnSite)).toBe(0);
  });
});

describe('curveFor', () => {
  it('gives one point per period against an s curve baseline', () => {
    const points = curveFor(service(), 6);
    expect(points).toHaveLength(6);
    expect(moneyMajor(points[5]!.planned)).toBe(312_190);
    expect(moneyMajor(points[2]!.earned)).toBe(16_152.5);
  });
});

describe('stripFor', () => {
  it('draws the strip for a package at a cut off', () => {
    const map = stripFor(service(), 'WP-01', 3);
    expect(map.packageId).toBe('WP-01');
    expect(map.rows).toHaveLength(4);
  });

  it('refuses a package the job does not carry', () => {
    expect(() => stripFor(service(), 'WP-99', 3)).toThrow(/no work package with id WP-99/);
  });
});

describe('loading site paperwork', () => {
  const recordSheet = [
    'record,package,code,step,from,to,quantity,unit,measured on,booked by',
    'SR-10,WP-02,E423.1,excavate,12+800,13+000,900,m3,2025-03-24,J Foreman',
  ].join('\n');

  it('adds clean records to the job', () => {
    const s = service();
    const report = loadRecords(s, recordSheet);
    expect(hasErrors(report)).toBe(false);
    expect(s.job.records.count()).toBe(4);
    expect(s.job.records.require('SR-10').packageId).toBe('WP-02');
  });

  it('applies nothing when a row failed', () => {
    const s = service();
    const report = loadRecords(s, `${recordSheet}\nSR-11,WP-02,nonsense,excavate,1,2,3,m3,x,y`);
    expect(hasErrors(report)).toBe(true);
    expect(s.job.records.count()).toBe(3);
  });

  it('applies the clean rows when asked to', () => {
    const s = service();
    loadRecords(s, `${recordSheet}\nSR-11,WP-02,nonsense,excavate,1,2,3,m3,x,y`, {
      partial: true,
    });
    expect(s.job.records.count()).toBe(4);
  });

  it('adds sign offs and reports', () => {
    const s = service();
    loadSignOffs(
      s,
      [
        'sign off,point,package,from,to,signed on,by',
        'SO-09,IP-E1,WP-02,12+800,13+000,2025-03-20,R Engineer',
      ].join('\n'),
    );
    expect(s.job.signOffs.count()).toBe(3);

    loadNcrs(
      s,
      [
        'ncr,package,from,to,raised on,description',
        'NCR-09,WP-02,12+800,12+900,2025-03-21,rutting',
      ].join('\n'),
    );
    expect(s.job.ncrs.count()).toBe(2);
  });

  it('adds standdowns and holidays to the calendar', () => {
    const s = service();
    loadDiary(s, ['date,reason', '2025-03-05,rain'].join('\n'));
    expect(s.job.standdowns).toHaveLength(1);

    loadHolidays(s, ['date', '2025-04-18', '2025-04-21'].join('\n'));
    expect(s.job.holidays).toHaveLength(2);
    loadHolidays(s, ['date', '2025-04-18'].join('\n'));
    expect(s.job.holidays).toHaveLength(2);
  });
});

describe('loadBill', () => {
  it('adds a new revision rather than replacing the last one', () => {
    const s = service();
    const report = loadBill(
      s,
      'code,description,unit,rate,quantity\nE423.1,excavation,m3,5.2,12400',
      {
        reference: 'Rev B',
        effectiveFrom: d('2025-04-01'),
        note: 'rate agreed',
      },
    );
    expect(hasErrors(report)).toBe(false);
    expect(s.job.billRevisions.count()).toBe(2);
    expect(moneyMajor(billTotal(s.job.billRevisions.require('Rev A').bill))).toBe(312_190);
    expect(moneyMajor(billTotal(s.job.billRevisions.require('Rev B').bill))).toBe(64_480);
  });

  it('refuses a reference that is already used', () => {
    const s = service();
    expect(() =>
      loadBill(s, 'code,description,unit,rate,quantity\nE423.1,excavation,m3,5.2,1', {
        reference: 'Rev A',
        effectiveFrom: d('2025-04-01'),
      }),
    ).toThrow(/already there/);
  });

  it('adds nothing when the sheet has an error', () => {
    const s = service();
    loadBill(s, 'code,description,unit,rate,quantity\nnonsense,excavation,m3,5.2,1', {
      reference: 'Rev B',
      effectiveFrom: d('2025-04-01'),
    });
    expect(s.job.billRevisions.count()).toBe(1);
  });
});
