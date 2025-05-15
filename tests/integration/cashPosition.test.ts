import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import {
  cashFlowFor,
  certificateFor,
  certificateSeries,
  createService,
} from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { moneyMajor } from '../../src/domain/money/money.js';
import { formatSiteDate } from '../../src/domain/time/siteDate.js';
import { buildServer } from '../../src/http/server.js';
import { renderCashFlow } from '../../src/reporting/format/text.js';
import { peakExposure, totalReceipts } from '../../src/valuation/cashFlow.js';
import { runCommand } from '../support/cliHarness.js';
import { sampleJob } from '../support/jobFixtures.js';

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(sampleJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

const service = () => createService(sampleJob(), DEFAULT_CONFIG);

describe('certificateSeries', () => {
  it('runs from period one whatever is asked for', () => {
    const series = certificateSeries(service(), 4);
    expect(series.map((c) => c.period.number)).toEqual([1, 2, 3, 4]);
  });

  it('gives the same certificate the single call gives', () => {
    const fromSeries = certificateSeries(service(), 3)[2]!;
    const alone = certificateFor(service(), 3);
    expect(moneyMajor(fromSeries.amountDue)).toBe(moneyMajor(alone.amountDue));
    expect(moneyMajor(fromSeries.netValuation)).toBe(moneyMajor(alone.netValuation));
  });
});

describe('cashFlowFor', () => {
  it('forecasts one point for every period asked for', () => {
    const points = cashFlowFor(service(), 6);
    expect(points).toHaveLength(6);
    expect(formatSiteDate(points[2]!.cutOff)).toBe('2025-03-25');
  });

  it('takes the certified series off the certificates themselves', () => {
    const points = cashFlowFor(service(), 4);
    const series = certificateSeries(service(), 4);
    expect(points.map((p) => moneyMajor(p.certified))).toEqual(
      series.map((c) => moneyMajor(c.netValuation)),
    );
  });

  it('owes nothing along the way when the payer is on time', () => {
    expect(cashFlowFor(service(), 6).every((p) => p.outstanding.minor === 0)).toBe(true);
  });

  it('holds a period of work back when the payer runs a period behind', () => {
    const points = cashFlowFor(service(), 6, 1);
    expect(moneyMajor(points[2]!.outstanding)).toBe(moneyMajor(points[2]!.certificate));
    expect(points[0]!.receipt.minor).toBe(0);
  });

  it('never brings cash in earlier than the on time forecast', () => {
    const onTime = cashFlowFor(service(), 6);
    const behind = cashFlowFor(service(), 6, 1);
    for (const [index, point] of behind.entries()) {
      expect(point.received.minor).toBeLessThanOrEqual(onTime[index]!.received.minor);
    }
    expect(moneyMajor(behind[2]!.received)).toBeLessThan(moneyMajor(onTime[2]!.received));
  });

  it('brings the same cash in eventually, once the works stop moving', () => {
    const onTime = moneyMajor(totalReceipts(cashFlowFor(service(), 6), 'GBP'));
    const behind = moneyMajor(totalReceipts(cashFlowFor(service(), 6, 1), 'GBP'));
    expect(behind).toBe(onTime);
  });
});

describe('renderCashFlow', () => {
  it('tables the periods and names the worst month', () => {
    const text = renderCashFlow(cashFlowFor(service(), 6, 1));
    expect(text.split('\n')[0]).toMatch(/^period +cut off +due +certificate/);
    expect(text).toContain('most owed at once');
  });

  it('leaves the peak line out when nothing is ever owed', () => {
    expect(renderCashFlow(cashFlowFor(service(), 6))).not.toContain('most owed at once');
  });
});

describe('GET /cash', () => {
  it('gives twelve periods by default', async () => {
    const body = (await app.inject({ method: 'GET', url: '/cash' })).json<{
      points: unknown[];
    }>();
    expect(body.points).toHaveLength(12);
  });

  it('takes the periods and the late flag off the query', async () => {
    const body = (await app.inject({ method: 'GET', url: '/cash?periods=4&late=1' })).json<{
      peak: { period: number } | null;
      points: { outstanding: { amount: number } }[];
    }>();
    expect(body.points).toHaveLength(4);
    expect(body.peak).not.toBeNull();
    expect(body.points[0]!.outstanding.amount).toBeGreaterThanOrEqual(0);
  });

  it('reports no peak at all when nothing is ever owed', async () => {
    const body = (await app.inject({ method: 'GET', url: '/cash?periods=4&late=0' })).json<{
      peak: unknown;
    }>();
    expect(body.peak).toBeNull();
  });

  it('refuses a late count that is not a whole number from zero', async () => {
    const response = await app.inject({ method: 'GET', url: '/cash?late=soon' });
    expect(response.statusCode).toBe(400);
    expect(response.json<{ message: string }>().message).toContain('whole number from zero');
  });
});

describe('cw cash', () => {
  it('prints the forecast', async () => {
    const run = await runCommand(['cash', '-n', '6']);
    expect(run.code).toBe(0);
    expect(run.out).toMatch(/^period +cut off +due /);
  });

  it('takes the late periods off the flag', async () => {
    const run = await runCommand(['cash', '-n', '6', '--late', '1']);
    expect(run.out).toContain('most owed at once');
  });

  it('prints the point count as json', async () => {
    const run = await runCommand(['--json', 'cash', '-n', '5']);
    expect(JSON.parse(run.out)).toBe(5);
  });
});

describe('peakExposure over the real series', () => {
  it('is the period the job has most work certified and unpaid', () => {
    const peak = peakExposure(cashFlowFor(service(), 6, 1))!;
    expect(peak.period).toBeGreaterThan(1);
    expect(peak.outstanding.minor).toBeGreaterThan(0);
  });
});
