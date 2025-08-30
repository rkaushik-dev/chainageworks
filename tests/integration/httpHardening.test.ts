import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createService } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { buildServer } from '../../src/http/server.js';
import { commercialJob } from '../support/jobFixtures.js';

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(commercialJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

describe('every response carries a request id', () => {
  it('puts it on a route that answered', async () => {
    const response = await app.inject({ method: 'GET', url: '/health' });
    expect(response.headers['x-request-id']).toBeDefined();
  });

  it('puts it on a route that failed', async () => {
    const response = await app.inject({ method: 'GET', url: '/valuations/nought/progress' });
    expect(response.statusCode).toBe(400);
    expect(response.headers['x-request-id']).toBeDefined();
  });

  it('gives a different one to each request', async () => {
    const first = await app.inject({ method: 'GET', url: '/health' });
    const second = await app.inject({ method: 'GET', url: '/health' });
    expect(first.headers['x-request-id']).not.toBe(second.headers['x-request-id']);
  });
});

describe('a path that exists reached with the wrong method', () => {
  it('answers 405 and names what would have worked', async () => {
    const response = await app.inject({ method: 'POST', url: '/job' });
    expect(response.statusCode).toBe(405);
    expect(response.headers['allow']).toBe('GET, HEAD');
    expect(response.json<{ code: string }>().code).toBe('unsupported');
  });

  it('answers 405 on a route that takes a parameter', async () => {
    const response = await app.inject({ method: 'DELETE', url: '/valuations/3/progress' });
    expect(response.statusCode).toBe(405);
    expect(response.json<{ details: { method: string } }>().details.method).toBe('DELETE');
  });

  it('answers 405 on an import route reached with a get', async () => {
    const response = await app.inject({ method: 'GET', url: '/import/records' });
    expect(response.statusCode).toBe(405);
    expect(response.headers['allow']).toContain('POST');
  });

  it('still answers 404 on a path nothing serves', async () => {
    const response = await app.inject({ method: 'GET', url: '/insurances' });
    expect(response.statusCode).toBe(404);
    expect(response.json<{ code: string }>().code).toBe('not_found');
  });

  it('answers 404 rather than 405 on a path of the wrong depth', async () => {
    const response = await app.inject({ method: 'GET', url: '/valuations/3' });
    expect(response.statusCode).toBe(404);
  });
});

describe('GET /routes', () => {
  const listed = async () =>
    (await app.inject({ method: 'GET', url: '/routes' })).json<{
      routes: { path: string; methods: string[] }[];
    }>();

  it('lists the routes off the router itself', async () => {
    const body = await listed();
    expect(body.routes.length).toBeGreaterThan(15);
    expect(body.routes.some((route) => route.path === '/health')).toBe(true);
    expect(body.routes.some((route) => route.path === '/import/records')).toBe(true);
  });

  it('names the methods each route answers', async () => {
    const body = await listed();
    const health = body.routes.find((route) => route.path === '/health')!;
    const records = body.routes.find((route) => route.path === '/import/records')!;
    expect(health.methods).toEqual(['GET', 'HEAD']);
    expect(records.methods).toEqual(['POST']);
  });

  it('joins a nested route back onto its prefix', async () => {
    const body = await listed();
    expect(body.routes.some((route) => route.path === '/packages/:id')).toBe(true);
    expect(body.routes.some((route) => route.path === '/:id')).toBe(false);
  });

  it('lists the valuation routes it actually answers', async () => {
    const body = await listed();
    const valuations = body.routes.filter((route) => route.path.startsWith('/valuations/'));
    expect(valuations.length).toBeGreaterThan(5);
  });
});

describe('a body larger than the server takes', () => {
  it('is refused rather than read', async () => {
    const huge = 'a'.repeat(3 * 1024 * 1024);
    const response = await app.inject({
      method: 'POST',
      url: '/import/records',
      payload: huge,
      headers: { 'content-type': 'text/csv' },
    });
    expect(response.statusCode).toBeGreaterThanOrEqual(400);
    expect(response.statusCode).not.toBe(200);
  });

  it('still takes a body inside the limit', async () => {
    const header = 'record,package,code,step,from,to,quantity,unit,measured on,booked by';
    const row = 'SR-99,WP-01,E423.1,excavate,12+000,12+100,120,m3,2025-03-06,J Foreman';
    const response = await app.inject({
      method: 'POST',
      url: '/import/records',
      payload: [header, row].join('\n'),
      headers: { 'content-type': 'text/csv' },
    });
    expect(response.statusCode).toBe(200);
  });
});
