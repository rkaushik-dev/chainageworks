import { describe, expect, it } from 'vitest';
import { createService, paymentsFor } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { parseItemCode } from '../../src/domain/bill/itemCode.js';
import { moneyFromMajor, moneyMajor } from '../../src/domain/money/money.js';
import { percent } from '../../src/domain/money/percent.js';
import { buildPaymentTerms } from '../../src/domain/subcontract/payment.js';
import { buildSubcontract } from '../../src/domain/subcontract/subcontract.js';
import { formatSiteDate } from '../../src/domain/time/siteDate.js';
import {
  noticesThisWeek,
  renderSubcontractReport,
} from '../../src/reporting/subcontractReport.js';
import { jobFromJson, jobToJson } from '../../src/store/codec/job.js';
import type { Job } from '../../src/store/job.js';
import { d } from '../support/contextFixtures.js';
import { sampleJob } from '../support/jobFixtures.js';

const gbp = (major: number) => moneyFromMajor(major, 'GBP');

function jobWithSubcontracts(): Job {
  const job = sampleJob();

  job.subcontracts.add(
    buildSubcontract({
      id: 'SC-EARTH',
      name: 'bulk earthworks and capping',
      contractor: 'Dowsett Plant Hire',
      orderValue: gbp(250_000),
      packageIds: ['WP-01', 'WP-02'],
      codes: [parseItemCode('E423.1')],
      placedOn: d('2024-11-04'),
    }),
  );
  job.subcontracts.add(
    buildSubcontract({
      id: 'SC-FENCE',
      name: 'permanent fencing',
      contractor: 'Marchmont Fencing',
      orderValue: gbp(31_000),
      packageIds: ['WP-03'],
      terms: buildPaymentTerms({ dueDays: 21, retention: percent(3) }),
      placedOn: d('2025-01-13'),
    }),
  );

  job.paymentApplications.add({
    id: 'SA-01',
    subcontractId: 'SC-EARTH',
    periodNumber: 1,
    appliedOn: d('2025-01-25'),
    gross: gbp(40_000),
    previouslyPaid: gbp(0),
  });
  job.paymentApplications.add({
    id: 'SA-02',
    subcontractId: 'SC-EARTH',
    periodNumber: 2,
    appliedOn: d('2025-02-25'),
    gross: gbp(90_000),
    previouslyPaid: gbp(38_000),
  });
  job.paymentApplications.add({
    id: 'SA-03',
    subcontractId: 'SC-EARTH',
    periodNumber: 3,
    appliedOn: d('2025-03-25'),
    gross: gbp(120_000),
    previouslyPaid: gbp(80_750),
  });

  job.paymentNotices.add({
    applicationId: 'SA-02',
    servedOn: d('2025-03-28'),
    valued: gbp(85_000),
  });
  return job;
}

describe('paymentsFor', () => {
  it('runs at the same cut off as the certificate', () => {
    const report = paymentsFor(createService(jobWithSubcontracts(), DEFAULT_CONFIG), 3);
    expect(formatSiteDate(report.asAt)).toBe('2025-03-25');
    expect(report.positions).toHaveLength(2);
  });

  it('values the order the payer answered and the one it did not', () => {
    const report = paymentsFor(createService(jobWithSubcontracts(), DEFAULT_CONFIG), 3);
    const earth = report.positions[0]!;
    expect(earth.subcontract.id).toBe('SC-EARTH');
    expect(earth.applications).toBe(3);
    expect(moneyMajor(earth.certifiedToDate)).toBe(114_000);
    expect(moneyMajor(earth.paidToDate)).toBe(114_000);
    expect(moneyMajor(earth.remaining)).toBe(136_000);
    expect(earth.defaulted).toEqual(['SA-01', 'SA-03']);
  });

  it('leaves an order nobody has applied against at its full value', () => {
    const report = paymentsFor(createService(jobWithSubcontracts(), DEFAULT_CONFIG), 3);
    const fence = report.positions[1]!;
    expect(fence.applications).toBe(0);
    expect(moneyMajor(fence.remaining)).toBe(31_000);
  });

  it('puts the closed window at the top of the diary', () => {
    const report = paymentsFor(createService(jobWithSubcontracts(), DEFAULT_CONFIG), 3);
    expect(report.diary.map((e) => e.applicationId)).toEqual(['SA-01', 'SA-03']);
    expect(report.diary[0]!.missed).toBe(true);
    expect(formatSiteDate(report.diary[0]!.noticeBy)).toBe('2025-03-01');
    expect(report.diary[1]!.missed).toBe(false);
  });

  it('prices what the missed window has already cost', () => {
    const report = paymentsFor(createService(jobWithSubcontracts(), DEFAULT_CONFIG), 3);
    expect(report.exposure).toBe('£40,000.00');
  });

  it('keeps the answered application out of the diary', () => {
    const report = paymentsFor(createService(jobWithSubcontracts(), DEFAULT_CONFIG), 3);
    expect(report.diary.map((e) => e.applicationId)).not.toContain('SA-02');
  });

  it('reports nothing over the order', () => {
    const report = paymentsFor(createService(jobWithSubcontracts(), DEFAULT_CONFIG), 3);
    expect(report.overOrder).toEqual([]);
  });

  it('names the order that has run past its value', () => {
    const job = jobWithSubcontracts();
    job.subcontracts.put(
      buildSubcontract({
        id: 'SC-EARTH',
        name: 'bulk earthworks and capping',
        contractor: 'Dowsett Plant Hire',
        orderValue: gbp(90_000),
        placedOn: d('2024-11-04'),
      }),
    );
    const report = paymentsFor(createService(job, DEFAULT_CONFIG), 3);
    expect(report.overOrder.map((p) => p.subcontract.id)).toEqual(['SC-EARTH']);
    expect(moneyMajor(report.overOrder[0]!.remaining)).toBe(-24_000);
  });

  it('is empty on a job with no orders placed', () => {
    const report = paymentsFor(createService(sampleJob(), DEFAULT_CONFIG), 3);
    expect(report.positions).toEqual([]);
    expect(report.diary).toEqual([]);
    expect(report.exposure).toBe('£0.00');
  });
});

describe('noticesThisWeek', () => {
  it('names only the windows closing inside the horizon', () => {
    const report = paymentsFor(createService(jobWithSubcontracts(), DEFAULT_CONFIG), 3);
    expect(noticesThisWeek(report, 7)).toEqual([]);
    expect(noticesThisWeek(report, 45)).toEqual([
      'SA-03 Dowsett Plant Hire, notice by 2025-04-29',
    ]);
  });
});

describe('renderSubcontractReport', () => {
  it('tables the orders and then the diary', () => {
    const report = paymentsFor(createService(jobWithSubcontracts(), DEFAULT_CONFIG), 3);
    const text = renderSubcontractReport(report);
    expect(text.split('\n')[0]).toBe('subcontract payments at 2025-03-25');
    expect(text).toMatch(/notices missed +1/);
    expect(text).toContain('notice diary');
    expect(text).toContain('window closed');
    expect(text).toContain('Marchmont Fencing');
  });

  it('leaves both tables out when there is nothing to say', () => {
    const report = paymentsFor(createService(sampleJob(), DEFAULT_CONFIG), 3);
    const text = renderSubcontractReport(report);
    expect(text).not.toContain('notice diary');
    expect(text).not.toContain('contractor');
  });
});

describe('the subcontract registers through the job file', () => {
  it('round trips the orders, the applications and the notices', () => {
    const again = jobFromJson(
      JSON.parse(JSON.stringify(jobToJson(jobWithSubcontracts()))) as unknown,
    );
    expect(again.subcontracts.count()).toBe(2);
    expect(again.paymentApplications.count()).toBe(3);
    expect(again.paymentNotices.count()).toBe(1);
    expect(again.subcontracts.require('SC-FENCE').terms.dueDays).toBe(21);
    expect(again.subcontracts.require('SC-EARTH').codes).toHaveLength(1);
    expect(moneyMajor(again.paymentApplications.require('SA-03').gross)).toBe(120_000);
  });

  it('reads the same position back off the file', () => {
    const again = jobFromJson(
      JSON.parse(JSON.stringify(jobToJson(jobWithSubcontracts()))) as unknown,
    );
    const report = paymentsFor(createService(again, DEFAULT_CONFIG), 3);
    expect(moneyMajor(report.positions[0]!.certifiedToDate)).toBe(114_000);
    expect(report.exposure).toBe('£40,000.00');
  });

  it('carries a job with no subcontracts through as empty registers', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(sampleJob()))) as unknown);
    expect(again.subcontracts.count()).toBe(0);
    expect(again.payLessNotices.count()).toBe(0);
  });
});
