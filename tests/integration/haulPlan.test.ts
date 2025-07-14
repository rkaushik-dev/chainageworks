import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createService, haulPlan } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { moneyMajor } from '../../src/domain/money/money.js';
import { quantityAmount } from '../../src/domain/quantity/quantity.js';
import { buildServer } from '../../src/http/server.js';
import { muckAwaySchedule, renderHaulPlan } from '../../src/reporting/haulReport.js';
import { jobFromJson, jobToJson } from '../../src/store/codec/job.js';
import { runCommand } from '../support/cliHarness.js';
import { commercialJob, haulingJob, sampleJob } from '../support/jobFixtures.js';

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(haulingJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

const service = () => createService(haulingJob(), DEFAULT_CONFIG);

describe('haulPlan', () => {
  it('splits the register into sources and destinations by kind', () => {
    const plan = haulPlan(service());
    expect(plan.movements.map((m) => `${m.fromId}->${m.toId}`)).toEqual([
      'CUT-A->EMB-B',
      'BOR-1->EMB-B',
    ]);
  });

  it('prices the plan off the job haul rate', () => {
    expect(moneyMajor(haulPlan(service()).cost)).toBe(23_000);
  });

  it('leaves the surplus unplaced rather than tipping bought material', () => {
    const plan = haulPlan(service());
    expect(quantityAmount(plan.unplaced)).toBe(3000);
    expect(quantityAmount(plan.spareTipCapacity)).toBe(5000);
  });

  it('is an empty plan on a job with no sites at all', () => {
    const plan = haulPlan(createService(commercialJob(), DEFAULT_CONFIG));
    expect(plan.movements).toEqual([]);
    expect(plan.cost.minor).toBe(0);
  });
});

describe('renderHaulPlan', () => {
  it('opens with the totals and names the sites in full', () => {
    const plan = haulPlan(service());
    const text = renderHaulPlan(plan, haulingJob().haulSites.all());
    expect(text.split('\n')[0]).toBe('haul plan');
    expect(text).toMatch(/movements +2/);
    expect(text).toMatch(/balanced +no/);
    expect(text).toContain('CUT-A cutting at 12+400');
  });

  it('puts the dearest movement first', () => {
    const plan = haulPlan(service());
    const text = renderHaulPlan(plan, haulingJob().haulSites.all());
    const rows = text
      .split('\n')
      .filter((line) => line.startsWith('CUT-A') || line.startsWith('BOR-1'));
    expect(rows[0]!.startsWith('CUT-A')).toBe(true);
  });

  it('leaves the table out when nothing moves', () => {
    const plan = haulPlan(createService(commercialJob(), DEFAULT_CONFIG));
    expect(renderHaulPlan(plan, [])).not.toContain('haul moment  0');
  });
});

describe('muckAwaySchedule', () => {
  it('says what has to leave each source and where it goes', () => {
    const sites = haulingJob().haulSites.all();
    const schedule = muckAwaySchedule(haulPlan(service()), sites);
    expect(schedule).toEqual([
      'BOR-1 borrow pit off the haul road, 1000 m3 to EMB-B',
      'CUT-A cutting at 12+400, 6000 m3 to EMB-B',
    ]);
  });

  it('says so when a source has nowhere to go', () => {
    const job = haulingJob();
    job.settings = { ...job.settings, haulLinks: [] };
    const schedule = muckAwaySchedule(
      haulPlan(createService(job, DEFAULT_CONFIG)),
      job.haulSites.all(),
    );
    expect(schedule.every((line) => line.endsWith('nowhere to go'))).toBe(true);
  });
});

describe('GET /haul', () => {
  it('answers with the movements and the totals', async () => {
    const body = (await app.inject({ method: 'GET', url: '/haul' })).json<{
      movements: { fromId: string; cost: { amount: number } }[];
      cost: { amount: number };
      balanced: boolean;
    }>();
    expect(body.movements).toHaveLength(2);
    expect(body.cost.amount).toBe(23_000);
    expect(body.balanced).toBe(false);
  });

  it('counts the haul sites on the job', async () => {
    const body = (await app.inject({ method: 'GET', url: '/job' })).json<
      Record<string, unknown>
    >();
    expect(body['counts']).toMatchObject({ haulSites: 4 });
  });
});

describe('cw haul', () => {
  it('prints the plan', async () => {
    const run = await runCommand(['haul'], { job: haulingJob() });
    expect(run.code).toBe(0);
    expect(run.out.split('\n')[0]).toBe('haul plan');
  });

  it('prints only the muck away schedule when asked', async () => {
    const run = await runCommand(['haul', '--muck-away'], { job: haulingJob() });
    expect(run.out.split('\n')).toHaveLength(2);
    expect(run.out).toContain('6000 m3 to EMB-B');
  });

  it('prints the movement count as json', async () => {
    const run = await runCommand(['--json', 'haul'], { job: haulingJob() });
    expect(JSON.parse(run.out)).toBe(2);
  });
});

describe('the haul register through the job file', () => {
  it('round trips the sites, the links and the rate', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(haulingJob()))) as unknown);
    expect(again.haulSites.count()).toBe(4);
    expect(again.haulSites.require('BOR-1').kind).toBe('borrow');
    expect(again.settings.haulLinks).toHaveLength(3);
    expect(moneyMajor(again.settings.haulRate)).toBe(1);
  });

  it('gives the same plan off the reloaded job', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(haulingJob()))) as unknown);
    expect(moneyMajor(haulPlan(createService(again, DEFAULT_CONFIG)).cost)).toBe(23_000);
  });

  it('carries a job with no haul set up through as empty', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(sampleJob()))) as unknown);
    expect(again.haulSites.count()).toBe(0);
    expect(again.settings.haulLinks).toEqual([]);
    expect(again.settings.haulRate.minor).toBe(0);
  });
});
