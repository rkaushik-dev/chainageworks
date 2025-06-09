import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import {
  certificateFor,
  certificateSeries,
  createService,
  loadIndices,
} from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { moneyMajor } from '../../src/domain/money/money.js';
import { buildServer } from '../../src/http/server.js';
import { errors, rowsAccepted } from '../../src/ingest/report.js';
import { renderCertificate } from '../../src/reporting/format/text.js';
import { provisionalCertificates } from '../../src/valuation/certificate.js';
import { jobFromJson, jobToJson } from '../../src/store/codec/job.js';
import { runCommand } from '../support/cliHarness.js';
import { commercialJob, fluctuatingJob, sampleJob } from '../support/jobFixtures.js';

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(fluctuatingJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

const fluctuating = () => createService(fluctuatingJob(), DEFAULT_CONFIG);
const fixed = () => createService(commercialJob(), DEFAULT_CONFIG);

describe('a job with a fluctuation clause', () => {
  it('moves the certificate above the fixed price one', () => {
    const adjusted = certificateFor(fluctuating(), 3);
    const plain = certificateFor(fixed(), 3);
    expect(moneyMajor(adjusted.measuredWork)).toBe(moneyMajor(plain.measuredWork));
    expect(moneyMajor(adjusted.gross)).toBeGreaterThan(moneyMajor(plain.gross));
  });

  it('works the factor out of both indices', () => {
    const certificate = certificateFor(fluctuating(), 3);
    expect(certificate.priceAdjustment.factor.lines.map((l) => l.indexId)).toEqual([
      'IDX-LAB',
      'IDX-PLA',
    ]);
    expect(certificate.priceAdjustment.factor.factor).toBeCloseTo(0.6 * 0.2 + 0.3 * 0.025, 10);
  });

  it('says the certificate rests on a provisional figure', () => {
    expect(certificateFor(fluctuating(), 3).priceAdjustment.provisional).toBe(true);
    expect(certificateFor(fluctuating(), 2).priceAdjustment.provisional).toBe(false);
  });

  it('names every certificate that will need reworking', () => {
    const series = certificateSeries(fluctuating(), 4);
    expect(provisionalCertificates(series).map((c) => c.period.number)).toEqual([3, 4]);
  });

  it('leaves the fixed price job entirely alone', () => {
    const certificate = certificateFor(fixed(), 3);
    expect(certificate.priceAdjustment.amount.minor).toBe(0);
    expect(certificate.priceAdjustment.factor.lines).toEqual([]);
  });
});

describe('renderCertificate with a fluctuation clause', () => {
  it('prints the adjustment line and the provisional warning', () => {
    const text = renderCertificate(certificateFor(fluctuating(), 3));
    expect(text).toContain('price adjustment at 12.750%');
    expect(text).toContain('rests on a provisional index figure');
  });

  it('leaves the line off a fixed price certificate', () => {
    const text = renderCertificate(certificateFor(fixed(), 3));
    expect(text).not.toContain('price adjustment');
    expect(text).not.toContain('provisional');
  });
});

describe('GET /valuations/:number/certificate with a clause', () => {
  it('carries the factor and the indices behind it', async () => {
    const body = (await app.inject({ method: 'GET', url: '/valuations/3/certificate' })).json<{
      priceAdjustment: {
        factor: number;
        provisional: boolean;
        indices: { indexId: string; movement: number }[];
      };
    }>();
    expect(body.priceAdjustment.provisional).toBe(true);
    expect(body.priceAdjustment.indices).toHaveLength(2);
    expect(body.priceAdjustment.indices[0]!.movement).toBeCloseTo(0.2, 10);
  });

  it('counts the index series on the job', async () => {
    const body = (await app.inject({ method: 'GET', url: '/job' })).json<
      Record<string, unknown>
    >();
    expect(body['counts']).toMatchObject({ indices: 2 });
  });
});

describe('importing a bulletin', () => {
  const csv = [
    'index,name,year,month,value,provisional',
    'IDX-LAB,civil engineering labour,2025,4,124,yes',
    'IDX-LAB,civil engineering labour,2025,5,126,yes',
    'IDX-STL,structural steel,2025,4,88,no',
  ].join('\n');

  it('groups the rows into one series each', () => {
    const service = createService(sampleJob(), DEFAULT_CONFIG);
    const report = loadIndices(service, csv);
    expect(rowsAccepted(report)).toBe(2);
    expect(service.job.indices.require('IDX-LAB').readings).toHaveLength(2);
    expect(service.job.indices.require('IDX-STL').name).toBe('structural steel');
  });

  it('takes the provisional flag off the row', () => {
    const service = createService(sampleJob(), DEFAULT_CONFIG);
    loadIndices(service, csv);
    expect(service.job.indices.require('IDX-LAB').readings[0]!.provisional).toBe(true);
    expect(service.job.indices.require('IDX-STL').readings[0]!.provisional).toBe(false);
  });

  it('refuses a bulletin with two figures for one month', () => {
    const service = createService(sampleJob(), DEFAULT_CONFIG);
    const report = loadIndices(
      service,
      ['index,year,month,value', 'IDX-LAB,2025,4,124', 'IDX-LAB,2025,4,125'].join('\n'),
    );
    expect(errors(report)[0]!.message).toContain('two figures for one month');
    expect(service.job.indices.count()).toBe(0);
  });

  it('loads through the cli and saves the job', async () => {
    const run = await runCommand(['import-indices', 'idx.csv'], {
      job: fluctuatingJob(),
      files: { 'idx.csv': csv },
    });
    expect(run.code).toBe(0);
    expect(run.out).toContain('read 2 index series, took 2');
    expect(run.saved[0]!.job.indices.count()).toBe(3);
  });
});

describe('the fluctuation clause through the job file', () => {
  it('round trips the terms and the bulletins', () => {
    const again = jobFromJson(
      JSON.parse(JSON.stringify(jobToJson(fluctuatingJob()))) as unknown,
    );
    expect(again.indices.count()).toBe(2);
    expect(again.indices.require('IDX-LAB').readings).toHaveLength(6);
    expect(again.settings.adjustment!.weightings).toHaveLength(2);
    expect(again.settings.adjustment!.nonAdjustable.basisPoints).toBe(1000);
  });

  it('gives the same certificate off the reloaded job', () => {
    const again = jobFromJson(
      JSON.parse(JSON.stringify(jobToJson(fluctuatingJob()))) as unknown,
    );
    const reloaded = certificateFor(createService(again, DEFAULT_CONFIG), 3);
    expect(moneyMajor(reloaded.gross)).toBe(moneyMajor(certificateFor(fluctuating(), 3).gross));
  });

  it('carries a job with no clause through as null', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(sampleJob()))) as unknown);
    expect(again.settings.adjustment).toBeNull();
    expect(again.indices.count()).toBe(0);
  });
});
