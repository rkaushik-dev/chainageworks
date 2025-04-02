import { describe, expect, it } from 'vitest';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { createService, defectsFor } from '../../src/app/service.js';
import { buildDefect } from '../../src/domain/defects/defect.js';
import { formatSiteDate } from '../../src/domain/time/siteDate.js';
import { chaseList, renderDefectsReport } from '../../src/reporting/defectsReport.js';
import { jobFromJson, jobToJson } from '../../src/store/codec/job.js';
import type { Job } from '../../src/store/job.js';
import { d, range } from '../support/contextFixtures.js';
import { sampleJob } from '../support/jobFixtures.js';

function jobWithDefects(): Job {
  const job = sampleJob();
  job.settings = { ...job.settings, defectsPeriodEnds: d('2025-06-30') };
  job.defects.add(
    buildDefect({
      id: 'DF-01',
      reference: 'DEF/2025/001',
      packageId: 'WP-03',
      extent: range('12+400', '12+600'),
      description: 'surface course ravelling at the joint',
      raisedOn: d('2025-03-03'),
      dueBy: d('2025-03-17'),
    }),
  );
  job.defects.add(
    buildDefect({
      id: 'DF-02',
      reference: 'DEF/2025/002',
      packageId: 'WP-01',
      description: 'missing barrier at the retaining wall',
      severity: 'safety',
      raisedOn: d('2025-03-20'),
      dueBy: d('2025-03-21'),
    }),
  );
  job.defects.add(
    buildDefect({
      id: 'DF-03',
      reference: 'DEF/2025/003',
      packageId: 'WP-02',
      description: 'damage by a third party',
      attribution: 'employer',
      raisedOn: d('2025-03-10'),
      dueBy: d('2025-03-24'),
      rectifiedOn: d('2025-03-18'),
    }),
  );
  return job;
}

describe('defectsFor', () => {
  it('is null when the contract has fixed no defects period', () => {
    expect(defectsFor(createService(sampleJob(), DEFAULT_CONFIG), 3)).toBeNull();
  });

  it('summarises the position at the cut off', () => {
    const report = defectsFor(createService(jobWithDefects(), DEFAULT_CONFIG), 3)!;
    expect(formatSiteDate(report.asAt)).toBe('2025-03-25');
    expect(report.summary.outstanding).toBe(2);
    expect(report.summary.bySeverity).toEqual({ minor: 1, major: 0, safety: 1 });
    expect(report.summary.overdue).toBe(2);
  });

  it('leaves out a defect that has been put right', () => {
    const report = defectsFor(createService(jobWithDefects(), DEFAULT_CONFIG), 3)!;
    expect(report.outstanding.map((x) => x.id)).not.toContain('DF-03');
  });

  it('says the period has not ended yet', () => {
    const report = defectsFor(createService(jobWithDefects(), DEFAULT_CONFIG), 3)!;
    expect(report.block).toBe('period_not_ended');
  });

  it('names the safety defect once the period has run', () => {
    const report = defectsFor(createService(jobWithDefects(), DEFAULT_CONFIG), 8)!;
    expect(report.block).toBe('safety_defect_open');
  });

  it('lists the chase order, overdue first', () => {
    const report = defectsFor(createService(jobWithDefects(), DEFAULT_CONFIG), 3)!;
    expect(chaseList(report)[0]).toContain('DEF/2025/001');
    expect(chaseList(report)).toHaveLength(2);
  });
});

describe('renderDefectsReport', () => {
  it('opens with the position and tables what is open', () => {
    const report = defectsFor(createService(jobWithDefects(), DEFAULT_CONFIG), 3)!;
    const text = renderDefectsReport(report);
    expect(text.split('\n')[0]).toBe('defects at 2025-03-25');
    expect(text).toMatch(/outstanding +2/);
    expect(text).toMatch(/certificate +period not ended/);
    expect(text).toContain('12+400.000 to 12+600.000');
  });

  it('leaves the table out when nothing is open', () => {
    const clean = sampleJob();
    clean.settings = { ...clean.settings, defectsPeriodEnds: d('2025-06-30') };
    const report = defectsFor(createService(clean, DEFAULT_CONFIG), 3)!;
    const text = renderDefectsReport(report);
    expect(text).toMatch(/outstanding +0/);
    expect(text).not.toContain('severity');
  });
});

describe('the defects list through the job file', () => {
  it('round trips the period and the defects', () => {
    const again = jobFromJson(
      JSON.parse(JSON.stringify(jobToJson(jobWithDefects()))) as unknown,
    );
    expect(formatSiteDate(again.settings.defectsPeriodEnds!)).toBe('2025-06-30');
    expect(again.defects.count()).toBe(3);
    expect(again.defects.require('DF-02').severity).toBe('safety');
    expect(again.defects.require('DF-03').attribution).toBe('employer');
    expect(again.defects.require('DF-01').extent).not.toBeNull();
  });

  it('carries a job with no defects period through as null', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(sampleJob()))) as unknown);
    expect(again.settings.defectsPeriodEnds).toBeNull();
    expect(again.defects.count()).toBe(0);
  });
});
