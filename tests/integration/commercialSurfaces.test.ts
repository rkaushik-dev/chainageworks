import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createService } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { buildServer } from '../../src/http/server.js';
import { runCommand } from '../support/cliHarness.js';
import { commercialJob, sampleJob } from '../support/jobFixtures.js';

let app: FastifyInstance;
let plain: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(commercialJob(), DEFAULT_CONFIG));
  plain = buildServer(createService(sampleJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
  await plain.close();
});

const get = (url: string) => app.inject({ method: 'GET', url });

describe('GET /valuations/:number/delay', () => {
  it('answers with the position and the events', async () => {
    const body = (await get('/valuations/3/delay')).json<Record<string, unknown>>();
    expect(body['asAt']).toBe('2025-03-25');
    expect(body['awardedDays']).toBe(8);
    expect(body['barred']).toEqual(['DE-02']);
    expect(body['events']).toHaveLength(2);
  });

  it('keeps the claimed days apart from the awarded days', async () => {
    const body = (await get('/valuations/3/delay')).json<Record<string, unknown>>();
    expect(body['claimedDays']).toBeGreaterThan(body['awardedDays'] as number);
  });

  it('answers 204 when the contract fixes no completion date', async () => {
    const response = await plain.inject({ method: 'GET', url: '/valuations/3/delay' });
    expect(response.statusCode).toBe(204);
    expect(response.body).toBe('');
  });
});

describe('GET /valuations/:number/defects', () => {
  it('summarises what is open and what it blocks', async () => {
    const body = (await get('/valuations/3/defects')).json<Record<string, unknown>>();
    expect(body['block']).toBe('period_not_ended');
    expect(body['summary']).toMatchObject({ outstanding: 2, overdue: 2 });
    expect(body['overdue']).toEqual(['DF-01', 'DF-02']);
  });

  it('gives the chainage of a defect that has one', async () => {
    const body = (await get('/valuations/3/defects')).json<{
      outstanding: { id: string; extent: string | null }[];
    }>();
    expect(body.outstanding[0]!.extent).toBe('12+400.000 to 12+600.000');
    expect(body.outstanding[1]!.extent).toBeNull();
  });

  it('answers 204 when the contract fixes no defects period', async () => {
    const response = await plain.inject({ method: 'GET', url: '/valuations/3/defects' });
    expect(response.statusCode).toBe(204);
  });
});

describe('GET /valuations/:number/payments', () => {
  it('values every order at the valuation cut off', async () => {
    const body = (await get('/valuations/3/payments')).json<{
      orders: { id: string; certified: { amount: number }; remaining: { amount: number } }[];
    }>();
    expect(body.orders).toHaveLength(2);
    expect(body.orders[0]!.certified.amount).toBe(80_750);
    expect(body.orders[0]!.remaining.amount).toBe(169_250);
    expect(body.orders[1]!.certified.amount).toBe(0);
  });

  it('names the window that closed unanswered', async () => {
    const body = (await get('/valuations/3/payments')).json<Record<string, unknown>>();
    expect(body['exposure']).toBe('£40,000.00');
    expect(body['diary']).toEqual([
      {
        applicationId: 'SA-01',
        subcontractId: 'SC-EARTH',
        contractor: 'Dowsett Plant Hire',
        appliedOn: '2025-01-25',
        noticeBy: '2025-03-01',
        amount: '£40,000.00',
        missed: true,
      },
    ]);
  });

  it('is an empty position on a job with no orders', async () => {
    const body = (await plain.inject({ method: 'GET', url: '/valuations/3/payments' })).json<
      Record<string, unknown>
    >();
    expect(body['orders']).toEqual([]);
    expect(body['diary']).toEqual([]);
  });
});

describe('GET /job counts the commercial registers', () => {
  it('reports the new registers alongside the old ones', async () => {
    const body = (await get('/job')).json<Record<string, unknown>>();
    expect(body['counts']).toMatchObject({
      delayEvents: 2,
      defects: 2,
      subcontracts: 2,
      paymentApplications: 2,
    });
  });
});

describe('cw delay', () => {
  it('prints the extension of time position', async () => {
    const run = await runCommand(['delay', '3'], { job: commercialJob() });
    expect(run.code).toBe(0);
    expect(run.out.split('\n')[0]).toBe('extension of time position at 2025-03-25');
    expect(run.out).toMatch(/days awarded +8/);
  });

  it('says so when the contract fixes no completion date', async () => {
    const run = await runCommand(['delay', '3']);
    expect(run.code).toBe(0);
    expect(run.out).toBe('the contract fixes no completion date');
  });

  it('prints null as json rather than an empty report', async () => {
    const run = await runCommand(['--json', 'delay', '3']);
    expect(JSON.parse(run.out)).toBeNull();
  });
});

describe('cw defects', () => {
  it('prints the defects position', async () => {
    const run = await runCommand(['defects', '3'], { job: commercialJob() });
    expect(run.out.split('\n')[0]).toBe('defects at 2025-03-25');
    expect(run.out).toMatch(/outstanding +2/);
    expect(run.out).toContain('DEF/2025/002');
  });

  it('says so when the contract fixes no defects period', async () => {
    const run = await runCommand(['defects', '3']);
    expect(run.out).toBe('the contract fixes no defects period');
  });
});

describe('cw payments', () => {
  it('prints the orders and the notice diary', async () => {
    const run = await runCommand(['payments', '3'], { job: commercialJob() });
    expect(run.out.split('\n')[0]).toBe('subcontract payments at 2025-03-25');
    expect(run.out).toContain('notice diary');
    expect(run.out).toMatch(/notices missed +1/);
    expect(run.out).toContain('Dowsett Plant Hire');
  });

  it('prints the order count as json', async () => {
    const run = await runCommand(['--json', 'payments', '3'], { job: commercialJob() });
    expect(JSON.parse(run.out)).toBe(2);
  });

  it('prints an empty position rather than failing on a job with no orders', async () => {
    const run = await runCommand(['payments', '3']);
    expect(run.code).toBe(0);
    expect(run.out).toMatch(/orders +0/);
  });
});
