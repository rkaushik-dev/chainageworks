import { describe, expect, it } from 'vitest';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { createService, delayFor } from '../../src/app/service.js';
import { buildCompletionTerms } from '../../src/domain/delay/completion.js';
import { buildDelayEvent } from '../../src/domain/delay/event.js';
import { moneyFromMajor, moneyMajor } from '../../src/domain/money/money.js';
import { percent } from '../../src/domain/money/percent.js';
import { formatSiteDate } from '../../src/domain/time/siteDate.js';
import {
  barredLines,
  claimedDays,
  renderDelayReport,
  unassessedLines,
} from '../../src/reporting/delayReport.js';
import { jobFromJson, jobToJson } from '../../src/store/codec/job.js';
import type { Job } from '../../src/store/job.js';
import { d } from '../support/contextFixtures.js';
import { sampleJob } from '../support/jobFixtures.js';

const terms = buildCompletionTerms({
  baseCompletion: d('2025-09-26'),
  damagesPerDay: moneyFromMajor(2500, 'GBP'),
  damagesCap: percent(10),
});

function jobWithDelay(): Job {
  const job = sampleJob();
  job.settings = { ...job.settings, completion: terms };
  job.delayEvents.add(
    buildDelayEvent({
      id: 'DE-01',
      reference: 'EW/2025/003',
      cause: 'utilities',
      description: 'live water main outside its recorded position',
      from: d('2025-03-03'),
      to: d('2025-03-14'),
      notifiedOn: d('2025-03-05'),
      awardedDays: 8,
    }),
  );
  job.delayEvents.add(
    buildDelayEvent({
      id: 'DE-02',
      reference: 'EW/2025/004',
      cause: 'design',
      description: 'revised drainage layout issued late',
      from: d('2025-03-10'),
      to: d('2025-03-21'),
      notifiedOn: null,
    }),
  );
  job.delayEvents.add(
    buildDelayEvent({
      id: 'DE-03',
      reference: 'EW/2025/005',
      cause: 'weather',
      description: 'exceptional rainfall, no earthworks',
      from: d('2025-03-17'),
      to: d('2025-03-19'),
      notifiedOn: d('2025-03-18'),
    }),
  );
  return job;
}

describe('delayFor', () => {
  it('is null when the contract fixes no completion date', () => {
    expect(delayFor(createService(sampleJob(), DEFAULT_CONFIG), 3)).toBeNull();
  });

  it('reports the position at the cut off', () => {
    const report = delayFor(createService(jobWithDelay(), DEFAULT_CONFIG), 3)!;
    expect(formatSiteDate(report.asAt)).toBe('2025-03-25');
    expect(report.lines).toHaveLength(3);
    expect(report.position.awardedDays).toBe(11);
    expect(formatSiteDate(report.position.extendedCompletion)).toBe('2025-10-13');
  });

  it('separates what was claimed from what was awarded', () => {
    const report = delayFor(createService(jobWithDelay(), DEFAULT_CONFIG), 3)!;
    expect(claimedDays(report)).toBe(10 + 10 + 3);
    expect(report.position.barredDays).toBe(10);
    expect(barredLines(report).map((l) => l.reference)).toEqual(['EW/2025/004']);
  });

  it('lists the events still waiting on an assessment', () => {
    const report = delayFor(createService(jobWithDelay(), DEFAULT_CONFIG), 3)!;
    expect(unassessedLines(report).map((l) => l.reference)).toEqual(['EW/2025/005']);
  });

  it('says which events carry money and which only carry time', () => {
    const report = delayFor(createService(jobWithDelay(), DEFAULT_CONFIG), 3)!;
    const byRef = new Map(report.lines.map((l) => [l.reference, l]));
    expect(byRef.get('EW/2025/003')?.carriesMoney).toBe(true);
    expect(byRef.get('EW/2025/005')?.carriesMoney).toBe(false);
    expect(byRef.get('EW/2025/005')?.entitlement).toBe('time_only');
  });

  it('carries no damages while the works are inside the extended date', () => {
    const report = delayFor(createService(jobWithDelay(), DEFAULT_CONFIG), 3)!;
    expect(report.daysLate).toBe(0);
    expect(report.damages.minor).toBe(0);
  });

  it('charges damages once the extended date has passed', () => {
    const report = delayFor(createService(jobWithDelay(), DEFAULT_CONFIG), 11)!;
    expect(report.daysLate).toBeGreaterThan(0);
    expect(moneyMajor(report.damages)).toBeGreaterThan(0);
  });
});

describe('renderDelayReport', () => {
  it('opens with the position and tables the events', () => {
    const report = delayFor(createService(jobWithDelay(), DEFAULT_CONFIG), 3)!;
    const text = renderDelayReport(report);
    expect(text.split('\n')[0]).toBe('extension of time position at 2025-03-25');
    expect(text).toMatch(/days awarded +11/);
    expect(text).toMatch(/days barred +10/);
    expect(text).toContain('not notified');
    expect(text).toContain('EW/2025/003');
  });
});

describe('the delay register through the job file', () => {
  it('round trips the terms and the events', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(jobWithDelay()))) as unknown);
    expect(formatSiteDate(again.settings.completion!.baseCompletion)).toBe('2025-09-26');
    expect(moneyMajor(again.settings.completion!.damagesPerDay)).toBe(2500);
    expect(again.delayEvents.count()).toBe(3);
    expect(again.delayEvents.require('DE-01').awardedDays).toBe(8);
    expect(again.delayEvents.require('DE-02').notifiedOn).toBeNull();
  });

  it('reports the same position after the round trip', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(jobWithDelay()))) as unknown);
    const report = delayFor(createService(again, DEFAULT_CONFIG), 3)!;
    expect(report.position.awardedDays).toBe(11);
  });

  it('carries a job with no completion terms through as null', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(sampleJob()))) as unknown);
    expect(again.settings.completion).toBeNull();
    expect(again.delayEvents.count()).toBe(0);
  });
});
