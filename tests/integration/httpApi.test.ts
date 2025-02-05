import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { createService } from '../../src/app/service.js';
import { buildServer } from '../../src/http/server.js';
import { sampleJob } from '../support/jobFixtures.js';

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(sampleJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

const get = (url: string) => app.inject({ method: 'GET', url });
const postCsv = (url: string, body: string) =>
  app.inject({ method: 'POST', url, payload: body, headers: { 'content-type': 'text/csv' } });

describe('GET /health', () => {
  it('answers ok', async () => {
    const response = await get('/health');
    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ status: 'ok' });
  });
});

describe('GET /job', () => {
  it('describes the job and counts every register', async () => {
    const body = (await get('/job')).json<Record<string, unknown>>();
    expect(body['id']).toBe('A1-IMP');
    expect((body['contractSum'] as { amount: number }).amount).toBe(312_190);
    expect(body['counts']).toMatchObject({ packages: 3, records: 3, signOffs: 2 });
    expect(body['blockers']).toEqual([]);
  });
});

describe('GET /schedule', () => {
  it('gives twelve periods by default', async () => {
    const body = (await get('/schedule')).json<unknown[]>();
    expect(body).toHaveLength(12);
  });

  it('honours a period count', async () => {
    expect((await get('/schedule?periods=3')).json<unknown[]>()).toHaveLength(3);
  });

  it('refuses a period count that is not a whole number', async () => {
    const response = await get('/schedule?periods=lots');
    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({ code: 'bad_input' });
  });
});

describe('GET /bill', () => {
  it('gives the bill in force', async () => {
    const body = (await get('/bill')).json<Record<string, unknown>>();
    expect(body['currency']).toBe('GBP');
    expect(body['items']).toHaveLength(8);
  });

  it('refuses a date it cannot read', async () => {
    const response = await get('/bill?asAt=yesterday');
    expect(response.statusCode).toBe(400);
  });

  it('refuses a date before any bill was in force', async () => {
    const response = await get('/bill?asAt=2024-01-01');
    expect(response.statusCode).toBe(400);
    expect(response.json<{ message: string }>().message).toMatch(/no bill revision/);
  });
});

describe('GET /packages', () => {
  it('lists them all', async () => {
    const body = (await get('/packages')).json<Record<string, unknown>[]>();
    expect(body).toHaveLength(3);
    expect(body[0]?.['extent']).toBe('12+000.000 to 12+800.000');
  });

  it('gives one by id', async () => {
    const body = (await get('/packages/WP-02')).json<Record<string, unknown>>();
    expect(body['profileId']).toBe('bulk-earthworks');
    expect(body['allocations']).toHaveLength(3);
  });

  it('gives a 404 with the domain code for one it does not have', async () => {
    const response = await get('/packages/WP-99');
    expect(response.statusCode).toBe(404);
    expect(response.json()).toMatchObject({
      code: 'not_found',
      details: { kind: 'work package', id: 'WP-99' },
    });
  });
});

describe('GET /records', () => {
  it('lists the records that count at a date', async () => {
    const body = (await get('/records?asAt=2025-03-31')).json<unknown[]>();
    expect(body).toHaveLength(3);
  });

  it('leaves out anything not measured yet', async () => {
    expect((await get('/records?asAt=2025-03-05')).json<unknown[]>()).toHaveLength(1);
  });

  it('filters by package', async () => {
    expect(
      (await get('/records?asAt=2025-03-31&package=WP-02')).json<unknown[]>(),
    ).toHaveLength(0);
  });
});

describe('GET /valuations/:number/progress', () => {
  it('reports the period', async () => {
    const body = (await get('/valuations/3/progress')).json<Record<string, unknown>>();
    expect((body['period'] as { number: number }).number).toBe(3);
    expect((body['earned'] as { amount: number }).amount).toBe(16_152.5);
    expect(body['packages']).toHaveLength(3);
  });

  it('refuses a valuation number that is not a whole number', async () => {
    expect((await get('/valuations/nought/progress')).statusCode).toBe(400);
    expect((await get('/valuations/0/progress')).statusCode).toBe(400);
  });
});

describe('GET /valuations/:number/certificate', () => {
  it('walks down to the amount due', async () => {
    const body = (await get('/valuations/3/certificate')).json<Record<string, unknown>>();
    expect((body['amountDue'] as { amount: number }).amount).toBe(15_344.87);
    expect((body['retention'] as { capReached: boolean }).capReached).toBe(false);
  });
});

describe('GET /valuations/:number/strip/:packageId', () => {
  it('draws the strip', async () => {
    const body = (await get('/valuations/3/strip/WP-01')).json<Record<string, unknown>>();
    expect(body['packageId']).toBe('WP-01');
    expect(body['rows'] as unknown[]).toHaveLength(4);
  });

  it('gives a 404 for a package it does not have', async () => {
    expect((await get('/valuations/3/strip/WP-99')).statusCode).toBe(404);
  });
});

describe('GET /curve', () => {
  it('gives one point per period', async () => {
    const body = (await get('/curve?periods=6')).json<unknown[]>();
    expect(body).toHaveLength(6);
  });
});

describe('POST /import/records', () => {
  const header = 'record,package,code,step,from,to,quantity,unit,measured on,booked by';

  it('takes a clean sheet and answers 200', async () => {
    const response = await postCsv(
      '/import/records',
      [header, 'SR-20,WP-02,E423.1,excavate,12+800,13+000,900,m3,2025-03-24,J Foreman'].join(
        '\n',
      ),
    );
    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({ rowsRead: 1, accepted: 1, rejected: 0 });
  });

  it('answers 207 and names the line when a row is wrong', async () => {
    const response = await postCsv(
      '/import/records',
      [header, 'SR-21,WP-02,nonsense,excavate,12+800,13+000,900,m3,2025-03-24,J'].join('\n'),
    );
    expect(response.statusCode).toBe(207);
    const body = response.json<Record<string, unknown>>();
    expect(body['rejected']).toBe(1);
    expect((body['issues'] as Record<string, unknown>[])[0]?.['line']).toBe(2);
  });

  it('takes a diary too', async () => {
    const response = await postCsv(
      '/import/diary',
      ['date,reason', '2025-03-05,rain'].join('\n'),
    );
    expect(response.statusCode).toBe(200);
  });
});

describe('an unknown route', () => {
  it('answers 404 in the same shape as everything else', async () => {
    const response = await get('/nothing-here');
    expect(response.statusCode).toBe(404);
    expect(response.json()).toEqual({
      code: 'not_found',
      message: 'no such route',
      details: {},
    });
  });
});
