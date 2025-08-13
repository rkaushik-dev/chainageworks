import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { diagnose, findingsIn, isHealthy } from '../../src/app/doctor.js';
import { createService } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { buildSignOff } from '../../src/domain/itp/signOff.js';
import { buildServer } from '../../src/http/server.js';
import { runCommand } from '../support/cliHarness.js';
import { d, range } from '../support/contextFixtures.js';
import { commercialJob, emptyJob, haulingJob, sampleJob } from '../support/jobFixtures.js';

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(haulingJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

describe('a job that is set up properly', () => {
  const diagnosis = diagnose(haulingJob());

  it('has no errors and no blockers', () => {
    expect(diagnosis.errors).toBe(0);
    expect(diagnosis.blockers).toEqual([]);
    expect(isHealthy(diagnosis)).toBe(true);
  });

  it('still warns about the packages nothing has been measured against', () => {
    expect(findingsIn(diagnosis, 'packages').map((f) => f.subject)).toEqual(['WP-02', 'WP-03']);
    expect(diagnosis.warnings).toBeGreaterThan(0);
  });
});

describe('a job with nothing loaded', () => {
  it('reports the blockers rather than a wall of findings', () => {
    const diagnosis = diagnose(emptyJob());
    expect(diagnosis.blockers).toHaveLength(3);
    expect(diagnosis.findings).toEqual([]);
    expect(isHealthy(diagnosis)).toBe(false);
  });
});

describe('a sign off against a point no plan carries', () => {
  it('is an error, because it gates nothing and nobody notices', () => {
    const job = sampleJob();
    job.signOffs.add(
      buildSignOff({
        id: 'SO-99',
        pointId: 'IP-NOWHERE',
        packageId: 'WP-01',
        extent: range('12+000', '12+400'),
        signedOn: d('2025-03-12'),
        by: 'R Engineer',
      }),
    );
    const diagnosis = diagnose(job);
    expect(diagnosis.errors).toBe(1);
    expect(findingsIn(diagnosis, 'sign offs')[0]!.message).toContain('gates nothing');
  });
});

describe('a payment application against an order nobody placed', () => {
  it('is an error', () => {
    const job = commercialJob();
    job.paymentApplications.add({
      id: 'SA-99',
      subcontractId: 'SC-NOWHERE',
      periodNumber: 1,
      appliedOn: d('2025-03-25'),
      gross: { minor: 100_000, currency: 'GBP' },
      previouslyPaid: { minor: 0, currency: 'GBP' },
    });
    const diagnosis = diagnose(job);
    expect(findingsIn(diagnosis, 'applications')[0]!.subject).toBe('SA-99');
  });
});

describe('a notice answering nothing', () => {
  it('is an error against the application it names', () => {
    const job = commercialJob();
    job.paymentNotices.add({
      applicationId: 'SA-NOWHERE',
      servedOn: d('2025-03-25'),
      valued: { minor: 100_000, currency: 'GBP' },
    });
    expect(findingsIn(diagnose(job), 'notices')[0]!.subject).toBe('SA-NOWHERE');
  });
});

describe('a fluctuation clause with no bulletin behind it', () => {
  it('is an error, because no certificate can be built at all', () => {
    const job = commercialJob();
    job.settings = {
      ...job.settings,
      adjustment: {
        baseDate: d('2024-10-25'),
        nonAdjustable: { basisPoints: 0 },
        weightings: [{ indexId: 'IDX-MISSING', weight: { basisPoints: 10_000 } }],
      },
    };
    const diagnosis = diagnose(job);
    expect(findingsIn(diagnosis, 'fluctuations')[0]!.message).toContain('no certificate');
    expect(isHealthy(diagnosis)).toBe(false);
  });
});

describe('a haul link naming a site nobody loaded', () => {
  it('is only a warning, because the plan still builds', () => {
    const job = haulingJob();
    job.settings = {
      ...job.settings,
      haulLinks: [...job.settings.haulLinks, { fromId: 'CUT-Z', toId: 'EMB-B', km: 1 }],
    };
    const diagnosis = diagnose(job);
    expect(diagnosis.errors).toBe(0);
    expect(findingsIn(diagnosis, 'haul')[0]!.message).toBe('no haul site CUT-Z');
  });
});

describe('an output allowance for a step nothing has', () => {
  it('is a warning that the allowance is never used', () => {
    const job = sampleJob();
    job.settings = {
      ...job.settings,
      outputAllowances: [{ stepId: 'landscaping', metresPerHour: 5 }],
    };
    expect(findingsIn(diagnose(job), 'allowances')[0]!.message).toContain('never used');
  });
});

describe('GET /doctor', () => {
  it('answers 200 with the findings', async () => {
    const response = await app.inject({ method: 'GET', url: '/doctor' });
    expect(response.statusCode).toBe(200);
    const body = response.json<{ healthy: boolean; errors: number; findings: unknown[] }>();
    expect(body.healthy).toBe(true);
    expect(body.errors).toBe(0);
    expect(body.findings.length).toBeGreaterThan(0);
  });
});

describe('cw doctor', () => {
  it('says so when nothing is wrong', async () => {
    const run = await runCommand(['doctor'], { job: haulingJob() });
    expect(run.code).toBe(0);
    expect(run.out).toMatch(/^nothing wrong, \d+ warnings not shown$/);
  });

  it('prints the warnings when asked for them', async () => {
    const run = await runCommand(['doctor', '--all'], { job: haulingJob() });
    expect(run.code).toBe(0);
    expect(run.out).toContain('warning packages WP-02');
  });

  it('exits non zero on a job with errors', async () => {
    const run = await runCommand(['doctor'], { job: emptyJob() });
    expect(run.code).toBe(1);
    expect(run.out).toContain('error job: no bill has been loaded');
  });

  it('prints the whole diagnosis as json', async () => {
    const run = await runCommand(['--json', 'doctor'], { job: haulingJob() });
    const body = JSON.parse(run.out) as { errors: number; warnings: number };
    expect(body.errors).toBe(0);
    expect(body.warnings).toBeGreaterThan(0);
  });
});
