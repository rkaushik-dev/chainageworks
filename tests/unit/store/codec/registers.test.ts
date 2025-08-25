import { describe, expect, it } from 'vitest';
import { jobFromJson, jobToJson } from '../../../../src/store/codec/job.js';
import type { Job } from '../../../../src/store/job.js';
import {
  commercialJob,
  fluctuatingJob,
  haulingJob,
  sampleJob,
} from '../../../support/jobFixtures.js';

/**
 * Every register the job document carries, and how to count it. The point of
 * the list is that a register added to the codec table and forgotten here
 * shows up as a document key nothing counts.
 */
const REGISTERS: readonly [string, (job: Job) => number][] = [
  ['alignments', (job) => job.alignments.count()],
  ['billRevisions', (job) => job.billRevisions.count()],
  ['packages', (job) => job.packages.count()],
  ['profiles', (job) => job.profiles.count()],
  ['plans', (job) => job.plans.count()],
  ['signOffs', (job) => job.signOffs.count()],
  ['ncrs', (job) => job.ncrs.count()],
  ['records', (job) => job.records.count()],
  ['materials', (job) => job.materials.count()],
  ['variations', (job) => job.variations.count()],
  ['dayworkSheets', (job) => job.dayworkSheets.count()],
  ['delayEvents', (job) => job.delayEvents.count()],
  ['defects', (job) => job.defects.count()],
  ['subcontracts', (job) => job.subcontracts.count()],
  ['paymentApplications', (job) => job.paymentApplications.count()],
  ['paymentNotices', (job) => job.paymentNotices.count()],
  ['payLessNotices', (job) => job.payLessNotices.count()],
  ['resourceReturns', (job) => job.resourceReturns.count()],
  ['indices', (job) => job.indices.count()],
  ['contraCharges', (job) => job.contraCharges.count()],
  ['haulSites', (job) => job.haulSites.count()],
];

const through = (job: Job): Job =>
  jobFromJson(JSON.parse(JSON.stringify(jobToJson(job))) as unknown);

describe('the job document holds every register', () => {
  it('writes a key for each one', () => {
    const doc = jobToJson(haulingJob()) as unknown as Record<string, unknown>;
    for (const [key] of REGISTERS) {
      expect([key, Array.isArray(doc[key])]).toEqual([key, true]);
    }
  });

  it('writes nothing under a name no register uses', () => {
    const doc = jobToJson(haulingJob()) as unknown as Record<string, unknown>;
    expect(doc['insurances']).toBeUndefined();
  });
});

describe('a full job through the file', () => {
  const job = haulingJob();
  const again = through(job);

  it('brings back the same count in every register', () => {
    for (const [key, count] of REGISTERS) {
      expect([key, count(again)]).toEqual([key, count(job)]);
    }
  });

  it('brings back a register that was carrying nothing', () => {
    expect(again.materials.count()).toBe(0);
    expect(again.dayworkSheets.count()).toBe(0);
  });

  it('survives a second trip unchanged', () => {
    expect(jobToJson(through(again))).toEqual(jobToJson(again));
  });
});

describe('a job with a fluctuation clause through the file', () => {
  it('keeps the clause and the bulletins', () => {
    const again = through(fluctuatingJob());
    expect(again.indices.count()).toBe(2);
    expect(again.settings.adjustment).not.toBeNull();
  });
});

describe('a bare job through the file', () => {
  const again = through(sampleJob());

  it('brings back the loaded registers and leaves the rest empty', () => {
    expect(again.packages.count()).toBe(3);
    expect(again.records.count()).toBe(3);
    expect(again.subcontracts.count()).toBe(0);
    expect(again.haulSites.count()).toBe(0);
  });

  it('replaces the seeded rules of credit with the ones on the file', () => {
    const job = sampleJob();
    expect(again.profiles.count()).toBe(job.profiles.count());
  });

  it('reads the plans against the profiles that came off the file', () => {
    expect(again.plans.count()).toBe(2);
    expect(again.plans.require('ITP-EW').profileId).toBe('bulk-earthworks');
  });
});

describe('a document missing a register a newer version writes', () => {
  it('reads it as empty rather than refusing the file', () => {
    const doc = jobToJson(commercialJob()) as unknown as Record<string, unknown>;
    delete doc['contraCharges'];
    delete doc['haulSites'];
    const again = jobFromJson(doc);
    expect(again.contraCharges.count()).toBe(0);
    expect(again.haulSites.count()).toBe(0);
    expect(again.subcontracts.count()).toBe(2);
  });

  it('still refuses a document written by another version', () => {
    const doc = jobToJson(sampleJob()) as unknown as Record<string, unknown>;
    doc['version'] = 99;
    expect(() => jobFromJson(doc)).toThrow(/different version/);
  });
});
