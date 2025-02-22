import { describe, expect, it } from 'vitest';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { certificateFor, createService } from '../../src/app/service.js';
import { buildDayworkSchedule, dayworkRate } from '../../src/domain/daywork/schedule.js';
import { buildDayworkSheet } from '../../src/domain/daywork/sheet.js';
import { moneyFromMajor, moneyMajor } from '../../src/domain/money/money.js';
import { renderCertificate } from '../../src/reporting/format/text.js';
import { certificateDto } from '../../src/http/dto.js';
import { jobFromJson, jobToJson } from '../../src/store/codec/job.js';
import type { Job } from '../../src/store/job.js';
import { d } from '../support/contextFixtures.js';
import { sampleJob } from '../support/jobFixtures.js';

const schedule = buildDayworkSchedule({
  labour: [dayworkRate('ganger', 'ganger', 24), dayworkRate('labourer', 'labourer', 19.5)],
  plant: [dayworkRate('excavator-13t', '13 tonne excavator', 42)],
});

function jobWithDaywork(signedOn: string | null = '2025-03-13'): Job {
  const job = sampleJob();
  job.settings = { ...job.settings, daywork: schedule };
  job.dayworkSheets.add(
    buildDayworkSheet({
      id: 'DW-01',
      reference: 'DW/2025/014',
      packageId: 'WP-01',
      instruction: 'EI-045',
      workedOn: d('2025-03-12'),
      signedOn: signedOn === null ? null : d(signedOn),
      signedBy: signedOn === null ? '' : 'R Engineer',
      labour: [{ rateId: 'ganger', people: 2, hours: 8, overtimeHours: 0 }],
      plant: [{ rateId: 'excavator-13t', hours: 8, overtimeHours: 0 }],
      materials: [{ description: 'imported stone', value: moneyFromMajor(400, 'GBP') }],
    }),
  );
  return job;
}

describe('daywork on the certificate', () => {
  it('is nothing when the contract has no daywork schedule', () => {
    const certificate = certificateFor(createService(sampleJob(), DEFAULT_CONFIG), 3);
    expect(certificate.daywork.minor).toBe(0);
    expect(moneyMajor(certificate.gross)).toBe(16_152.5);
  });

  it('adds a signed sheet into the gross', () => {
    const certificate = certificateFor(createService(jobWithDaywork(), DEFAULT_CONFIG), 3);
    expect(moneyMajor(certificate.daywork)).toBe(1549.92);
    expect(moneyMajor(certificate.gross)).toBe(16_152.5 + 1549.92);
  });

  it('leaves an unsigned sheet out, however much work is on it', () => {
    const certificate = certificateFor(createService(jobWithDaywork(null), DEFAULT_CONFIG), 3);
    expect(certificate.daywork.minor).toBe(0);
  });

  it('picks the sheet up in the period the signature lands in', () => {
    const service = createService(jobWithDaywork('2025-04-02'), DEFAULT_CONFIG);
    expect(certificateFor(service, 3).daywork.minor).toBe(0);
    expect(moneyMajor(certificateFor(service, 4).daywork)).toBe(1549.92);
  });

  it('holds retention on the daywork like everything else', () => {
    const certificate = certificateFor(createService(jobWithDaywork(), DEFAULT_CONFIG), 3);
    expect(moneyMajor(certificate.retention.accrued)).toBe(885.12);
  });

  it('shows on the printed certificate', () => {
    const certificate = certificateFor(createService(jobWithDaywork(), DEFAULT_CONFIG), 3);
    expect(renderCertificate(certificate)).toMatch(/daywork +£1,549\.92/);
  });

  it('shows on the wire', () => {
    const certificate = certificateFor(createService(jobWithDaywork(), DEFAULT_CONFIG), 3);
    const dto = certificateDto(certificate);
    expect((dto['daywork'] as { amount: number }).amount).toBe(1549.92);
  });
});

describe('daywork through the job file', () => {
  it('round trips the schedule and the sheets', () => {
    const again = jobFromJson(
      JSON.parse(JSON.stringify(jobToJson(jobWithDaywork()))) as unknown,
    );
    expect(again.settings.daywork?.labour).toHaveLength(2);
    expect(again.settings.daywork?.overtimeFactor).toBe(1.5);
    expect(again.dayworkSheets.count()).toBe(1);
    const sheet = again.dayworkSheets.require('DW-01');
    expect(sheet.labour[0]?.people).toBe(2);
    expect(moneyMajor(sheet.materials[0]!.value)).toBe(400);
  });

  it('values the same after the round trip', () => {
    const again = jobFromJson(
      JSON.parse(JSON.stringify(jobToJson(jobWithDaywork()))) as unknown,
    );
    expect(moneyMajor(certificateFor(createService(again, DEFAULT_CONFIG), 3).daywork)).toBe(
      1549.92,
    );
  });

  it('carries a job with no daywork through as null', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(sampleJob()))) as unknown);
    expect(again.settings.daywork).toBeNull();
    expect(again.dayworkSheets.count()).toBe(0);
  });
});
