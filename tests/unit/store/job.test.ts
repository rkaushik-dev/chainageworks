import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../src/errors.js';
import { moneyFromMajor, moneyMajor } from '../../../src/domain/money/money.js';
import { percent, percentValue } from '../../../src/domain/money/percent.js';
import { isWorkingDay } from '../../../src/domain/time/calendar.js';
import { formatSiteDate } from '../../../src/domain/time/siteDate.js';
import { earnAll } from '../../../src/progress/earn.js';
import {
  createJob,
  isJobReady,
  jobBlockers,
  jobCalendar,
  jobContext,
  jobSchedule,
  jobSettings,
} from '../../../src/store/job.js';
import { d } from '../../support/contextFixtures.js';
import { emptyJob, sampleJob } from '../../support/jobFixtures.js';

const gbp = (major: number) => moneyFromMajor(major, 'GBP');

describe('jobSettings', () => {
  const base = { id: 'J1', name: 'a job', contractSum: gbp(1000) };

  it('fills in the contract defaults', () => {
    const settings = jobSettings(base);
    expect(settings.currency).toBe('GBP');
    expect(percentValue(settings.retention.rate)).toBe(5);
    expect(settings.cutOffDay).toBe(25);
    expect(settings.issueDays).toBe(7);
    expect(settings.paymentDays).toBe(14);
    expect(settings.workingDays).toEqual([1, 2, 3, 4, 5]);
    expect(settings.materialsAllowance).toBeNull();
    expect(settings.substantialCompletionOn).toBeNull();
  });

  it('takes what it is given', () => {
    const settings = jobSettings({
      ...base,
      cutOffDay: 28,
      issueDays: 3,
      paymentDays: 30,
      workingDays: [1, 2, 3, 4, 5, 6],
      materialsAllowance: percent(85),
      substantialCompletionOn: d('2025-11-01'),
    });
    expect(settings.cutOffDay).toBe(28);
    expect(settings.workingDays).toEqual([1, 2, 3, 4, 5, 6]);
    expect(percentValue(settings.materialsAllowance!)).toBe(85);
  });

  it('refuses a cut off day that is not a day of the month', () => {
    expect(() => jobSettings({ ...base, cutOffDay: 0 })).toThrow(/day of the month/);
    expect(() => jobSettings({ ...base, cutOffDay: 32 })).toThrow(/day of the month/);
  });

  it('refuses a negative contract sum', () => {
    expect(() => jobSettings({ ...base, contractSum: gbp(-1) })).toThrow(/cannot be negative/);
  });

  it('refuses a contract sum in another currency', () => {
    expect(() => jobSettings({ ...base, currency: 'EUR', contractSum: gbp(1000) })).toThrow(
      /different currency/,
    );
  });
});

describe('createJob', () => {
  it('starts with the standard rules of credit and nothing else', () => {
    const job = emptyJob();
    expect(job.profiles.count()).toBe(7);
    expect(job.packages.count()).toBe(0);
    expect(job.billRevisions.count()).toBe(0);
    expect(job.holidays).toEqual([]);
  });

  it('names every repository for its error messages', () => {
    const job = emptyJob();
    expect(() => job.packages.require('WP-99')).toThrow(/no work package with id WP-99/);
    expect(() => job.records.require('SR-99')).toThrow(/no survey record with id SR-99/);
  });
});

describe('jobCalendar', () => {
  it('builds from the settings and the holiday list', () => {
    const job = emptyJob();
    job.holidays = [d('2025-04-18')];
    const calendar = jobCalendar(job);
    expect(calendar.id).toBe('A1-IMP-calendar');
    expect(isWorkingDay(calendar, d('2025-04-18'))).toBe(false);
    expect(isWorkingDay(calendar, d('2025-04-17'))).toBe(true);
  });

  it('honours a six day week', () => {
    const job = createJob({
      id: 'J2',
      name: 'six day job',
      contractSum: gbp(1000),
      workingDays: [1, 2, 3, 4, 5, 6],
    });
    expect(isWorkingDay(jobCalendar(job), d('2025-03-15'))).toBe(true);
  });

  it('carries the standdowns through', () => {
    const job = emptyJob();
    job.standdowns = [{ date: d('2025-03-05'), reason: 'weather' }];
    expect(jobCalendar(job).standdowns).toHaveLength(1);
  });
});

describe('jobContext', () => {
  it('assembles a context the engine can value from', () => {
    const context = jobContext(sampleJob());
    expect(context.register.packages).toHaveLength(3);
    expect(context.records).toHaveLength(3);
    expect(context.plans.size).toBe(2);
    expect(moneyMajor(earnAll(context, d('2025-03-31'))[0]!.earnedValue)).toBe(16_152.5);
  });

  it('refuses a job with no bill', () => {
    expect(() => jobContext(emptyJob())).toThrow(BadInputError);
    expect(() => jobContext(emptyJob())).toThrow(/no bill yet/);
  });

  it('picks up a record added after the last call', () => {
    const job = sampleJob();
    expect(jobContext(job).records).toHaveLength(3);
    job.records.remove('SR-03');
    expect(jobContext(job).records).toHaveLength(2);
  });
});

describe('jobSchedule', () => {
  it('builds from the job cut off and lags', () => {
    const schedule = jobSchedule(sampleJob(), 2025, 1, 3);
    expect(schedule.periods).toHaveLength(3);
    expect(formatSiteDate(schedule.periods[2]!.cutOff)).toBe('2025-03-25');
    expect(formatSiteDate(schedule.periods[2]!.issuedOn)).toBe('2025-04-03');
  });

  it('moves the cut off when the job works six days', () => {
    const job = createJob({
      id: 'J2',
      name: 'six day job',
      contractSum: gbp(1000),
      workingDays: [1, 2, 3, 4, 5, 6],
    });
    expect(formatSiteDate(jobSchedule(job, 2025, 5, 1).periods[0]!.cutOff)).toBe('2025-05-24');
  });
});

describe('jobBlockers', () => {
  it('names everything a fresh job is missing', () => {
    expect(jobBlockers(emptyJob())).toEqual([
      'no bill has been loaded',
      'no work packages have been set up',
      'no alignment has been loaded',
    ]);
    expect(isJobReady(emptyJob())).toBe(false);
  });

  it('says nothing about a job that is ready', () => {
    expect(jobBlockers(sampleJob())).toEqual([]);
    expect(isJobReady(sampleJob())).toBe(true);
  });
});
