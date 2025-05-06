import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createService, loadResourceReturns, outputFor } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { buildServer } from '../../src/http/server.js';
import { rowsAccepted } from '../../src/ingest/report.js';
import { renderOutputReport, unallocatedHours } from '../../src/reporting/outputReport.js';
import { jobFromJson, jobToJson } from '../../src/store/codec/job.js';
import { runCommand } from '../support/cliHarness.js';
import { commercialJob, sampleJob } from '../support/jobFixtures.js';

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(commercialJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

const service = () => createService(commercialJob(), DEFAULT_CONFIG);

describe('outputFor', () => {
  it('reports every package at the valuation cut off', () => {
    const report = outputFor(service(), 3);
    expect(report.packages.map((p) => p.packageId)).toEqual(['WP-01', 'WP-02', 'WP-03']);
    expect(report.totals.returns).toBe(4);
    expect(report.totals.labourHours).toBe(152);
  });

  it('names the step running behind its allowance', () => {
    const report = outputFor(service(), 3);
    expect(report.behind).toHaveLength(1);
    expect(report.behind[0]!.packageId).toBe('WP-01');
    expect(report.behind[0]!.step.stepId).toBe('strip');
    expect(report.behind[0]!.step.ratio).toBeCloseTo(0.8333, 4);
  });

  it('holds back the hours nobody booked to a step', () => {
    expect(unallocatedHours(outputFor(service(), 3))).toEqual([
      { packageId: 'WP-01', hours: 16 },
    ]);
  });

  it('prices the standing plant as time that bought nothing', () => {
    const report = outputFor(service(), 3);
    expect(report.totals.standingHours).toBe(4);
    expect(report.lostFraction).toBeCloseTo(4 / 164, 5);
  });

  it('is an honest set of zeroes on a job with no returns', () => {
    const report = outputFor(createService(sampleJob(), DEFAULT_CONFIG), 3);
    expect(report.totals.labourHours).toBe(0);
    expect(report.behind).toEqual([]);
    expect(report.barren).toEqual([]);
  });
});

describe('renderOutputReport', () => {
  it('opens with the totals and tables the steps that were worked', () => {
    const text = renderOutputReport(outputFor(service(), 3));
    expect(text.split('\n')[0]).toBe('output at 2025-03-25');
    expect(text).toMatch(/labour hours +152/);
    expect(text).toMatch(/steps behind the allowance +1/);
    expect(text).toContain('m per hr');
  });

  it('leaves the table out when nothing has been worked or measured', () => {
    const clean = createService(sampleJob(), DEFAULT_CONFIG);
    const text = renderOutputReport(outputFor(clean, 1));
    expect(text).not.toContain('m per hr');
    expect(text).toMatch(/labour hours +0/);
  });
});

describe('GET /valuations/:number/output', () => {
  it('answers with the totals and the steps behind', async () => {
    const body = (await app.inject({ method: 'GET', url: '/valuations/3/output' })).json<
      Record<string, unknown>
    >();
    expect(body['asAt']).toBe('2025-03-25');
    expect(body['behind']).toEqual([
      { packageId: 'WP-01', stepId: 'strip', ratio: 800 / 96 / 10 },
    ]);
    expect(body['unallocated']).toEqual([{ packageId: 'WP-01', hours: 16 }]);
  });

  it('counts the resource returns on the job', async () => {
    const body = (await app.inject({ method: 'GET', url: '/job' })).json<
      Record<string, unknown>
    >();
    expect(body['counts']).toMatchObject({ resourceReturns: 4 });
  });
});

describe('cw output', () => {
  it('prints the output report', async () => {
    const run = await runCommand(['output', '3'], { job: commercialJob() });
    expect(run.code).toBe(0);
    expect(run.out).toMatch(/plant hours +12/);
  });

  it('prints the labour hours as json', async () => {
    const run = await runCommand(['--json', 'output', '3'], { job: commercialJob() });
    expect(JSON.parse(run.out)).toBe(152);
  });
});

describe('cw import-returns', () => {
  const csv = [
    'return,package,step,date,shift,kind,resource,description,count,hours,hour kind,returned by',
    'RR-10,WP-02,strip,2025-03-17,day,labour,GANG-EARTH,earthworks gang,5,9,,D Illingworth',
    'RR-10,WP-02,strip,2025-03-17,day,plant,PL-D6,dozer,1,9,,D Illingworth',
  ].join('\n');

  it('loads a return export and saves the job', async () => {
    const run = await runCommand(['import-returns', 'returns.csv'], {
      job: commercialJob(),
      files: { 'returns.csv': csv },
    });
    expect(run.code).toBe(0);
    expect(run.out).toContain('read 1 resource returns, took 1');
    expect(run.saved).toHaveLength(1);
    expect(run.saved[0]!.job.resourceReturns.count()).toBe(5);
  });

  it('puts the loaded hours into the next output report', () => {
    const loaded = service();
    expect(rowsAccepted(loadResourceReturns(loaded, csv))).toBe(1);
    const report = outputFor(loaded, 3);
    expect(report.packages[1]!.labourHours).toBe(45);
    expect(report.totals.plantHours).toBe(21);
  });
});

describe('the resource returns through the job file', () => {
  it('round trips the returns and the output allowances', () => {
    const again = jobFromJson(
      JSON.parse(JSON.stringify(jobToJson(commercialJob()))) as unknown,
    );
    expect(again.resourceReturns.count()).toBe(4);
    expect(again.resourceReturns.require('RR-03').plant[0]!.kind).toBe('standing');
    expect(again.resourceReturns.require('RR-04').stepId).toBeNull();
    expect(again.settings.outputAllowances).toEqual([
      { stepId: 'strip', metresPerHour: 10 },
      { stepId: 'excavate', metresPerHour: 12 },
    ]);
  });

  it('carries a job with no returns through as empty', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(sampleJob()))) as unknown);
    expect(again.resourceReturns.count()).toBe(0);
    expect(again.settings.outputAllowances).toEqual([]);
  });
});
