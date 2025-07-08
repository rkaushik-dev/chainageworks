import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createService, lookaheadFrom } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { formatSiteDate } from '../../src/domain/time/siteDate.js';
import { buildServer } from '../../src/http/server.js';
import { chaseInspections, renderLookahead } from '../../src/reporting/lookaheadReport.js';
import { runCommand } from '../support/cliHarness.js';
import { d } from '../support/contextFixtures.js';
import { commercialJob } from '../support/jobFixtures.js';

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(commercialJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

const service = () => createService(commercialJob(), DEFAULT_CONFIG);

describe('lookaheadFrom', () => {
  it('runs four weeks from the date and reads the job allowances', () => {
    const plan = lookaheadFrom(service(), d('2025-03-26'));
    expect(formatSiteDate(plan.to)).toBe('2025-04-22');
    expect(plan.workingDays).toBe(20);
    expect(plan.steps.some((s) => s.days !== null)).toBe(true);
  });

  it('takes the weeks and the gangs off the options', () => {
    const one = lookaheadFrom(service(), d('2025-03-26'), { weeks: 2 });
    const pushed = lookaheadFrom(service(), d('2025-03-26'), { weeks: 2, gangs: 2 });
    expect(one.workingDays).toBe(10);
    expect(pushed.daysOfWork).toBeCloseTo(one.daysOfWork / 2, 6);
  });

  it('leaves out a step with nothing left to build', () => {
    const plan = lookaheadFrom(service(), d('2025-03-26'));
    expect(plan.steps.some((s) => s.packageId === 'WP-01' && s.stepId === 'strip')).toBe(false);
  });

  it('refuses a lookahead with no weeks in it', () => {
    expect(() => lookaheadFrom(service(), d('2025-03-26'), { weeks: 0 })).toThrow(
      /at least one week/,
    );
  });
});

describe('renderLookahead', () => {
  it('opens with the window and splits ready from waiting', () => {
    const text = renderLookahead(lookaheadFrom(service(), d('2025-03-26')));
    expect(text.split('\n')[0]).toBe('lookahead 2025-03-26 to 2025-04-22');
    expect(text).toMatch(/working days in the window +20/);
    expect(text).toContain('ready to start');
    expect(text).toContain('waiting');
  });

  it('names what is holding each waiting step', () => {
    const text = renderLookahead(lookaheadFrom(service(), d('2025-03-26')));
    expect(text).toMatch(/held by IP-E[12]/);
  });
});

describe('chaseInspections', () => {
  it('lists the sign offs standing between a gang and the work', () => {
    const chase = chaseInspections(lookaheadFrom(service(), d('2025-03-26')));
    expect(chase.length).toBeGreaterThan(0);
    expect(chase[0]).toMatch(/^WP-\d+ \w+ at 1[23]\+/);
    expect(chase.every((line) => line.includes('held by'))).toBe(true);
  });
});

describe('GET /lookahead', () => {
  it('runs from the first cut off when nothing says otherwise', async () => {
    const body = (await app.inject({ method: 'GET', url: '/lookahead' })).json<
      Record<string, unknown>
    >();
    expect(body['from']).toBe('2025-01-24');
    expect(body['workingDays']).toBe(20);
  });

  it('takes the date, the weeks and the gangs off the query', async () => {
    const body = (
      await app.inject({ method: 'GET', url: '/lookahead?from=2025-03-26&weeks=2&gangs=2' })
    ).json<Record<string, unknown>>();
    expect(body['from']).toBe('2025-03-26');
    expect(body['to']).toBe('2025-04-08');
    expect(body['workingDays']).toBe(10);
  });

  it('keeps the ready list apart from the held list', async () => {
    const body = (await app.inject({ method: 'GET', url: '/lookahead?from=2025-03-26' })).json<{
      ready: string[];
      heldForInspection: string[];
    }>();
    expect(body.ready.length).toBeGreaterThan(0);
    expect(body.heldForInspection.length).toBeGreaterThan(0);
    for (const held of body.heldForInspection) {
      expect(body.ready).not.toContain(held);
    }
  });

  it('refuses a date it cannot read', async () => {
    const response = await app.inject({ method: 'GET', url: '/lookahead?from=26%2F03%2F2025' });
    expect(response.statusCode).toBe(400);
  });
});

describe('cw lookahead', () => {
  it('prints the plan', async () => {
    const run = await runCommand(['lookahead', '2025-03-26'], { job: commercialJob() });
    expect(run.code).toBe(0);
    expect(run.out.split('\n')[0]).toBe('lookahead 2025-03-26 to 2025-04-22');
  });

  it('takes the weeks and the gangs off the flags', async () => {
    const run = await runCommand(['lookahead', '2025-03-26', '-w', '2', '-g', '3'], {
      job: commercialJob(),
    });
    expect(run.out).toMatch(/working days in the window +10/);
  });

  it('prints only the chase list when asked', async () => {
    const run = await runCommand(['lookahead', '2025-03-26', '--chase'], {
      job: commercialJob(),
    });
    expect(run.out).toContain('held by');
    expect(run.out).not.toContain('ready to start');
  });

  it('prints the step count as json', async () => {
    const run = await runCommand(['--json', 'lookahead', '2025-03-26'], {
      job: commercialJob(),
    });
    expect(JSON.parse(run.out)).toBeGreaterThan(0);
  });

  it('refuses a date it cannot read', async () => {
    const run = await runCommand(['lookahead', 'monday'], { job: commercialJob() });
    expect(run.code).toBe(1);
    expect(run.err).toContain('bad_input');
  });
});
