import { describe, expect, it } from 'vitest';
import { createService } from '../../src/app/service.js';
import { configFromEnv, DEFAULT_CONFIG, mergeConfig } from '../../src/config/config.js';
import { buildServer } from '../../src/http/server.js';
import { runCommand } from '../support/cliHarness.js';
import { commercialJob } from '../support/jobFixtures.js';

describe('the report defaults on the config', () => {
  it('has a sensible set out of the box', () => {
    expect(DEFAULT_CONFIG.reports).toEqual({
      periods: 12,
      lookaheadWeeks: 4,
      gangs: 1,
      worstLines: 10,
    });
  });

  it('takes each one off the environment', () => {
    const config = configFromEnv({
      CHAINAGEWORKS_PERIODS: '24',
      CHAINAGEWORKS_LOOKAHEAD_WEEKS: '6',
      CHAINAGEWORKS_GANGS: '3',
      CHAINAGEWORKS_WORST_LINES: '40',
    });
    expect(config.reports).toEqual({
      periods: 24,
      lookaheadWeeks: 6,
      gangs: 3,
      worstLines: 40,
    });
  });

  it('keeps the defaults for anything the environment leaves out', () => {
    const config = configFromEnv({ CHAINAGEWORKS_PERIODS: '24' });
    expect(config.reports.periods).toBe(24);
    expect(config.reports.lookaheadWeeks).toBe(4);
  });

  it('refuses a count that is not a whole number from one', () => {
    expect(() => configFromEnv({ CHAINAGEWORKS_GANGS: 'lots' })).toThrow(
      /whole number from one/,
    );
    expect(() => configFromEnv({ CHAINAGEWORKS_PERIODS: '0' })).toThrow(
      /whole number from one/,
    );
  });

  it('refuses a count outside what the schema allows', () => {
    expect(() => configFromEnv({ CHAINAGEWORKS_LOOKAHEAD_WEEKS: '400' })).toThrow(
      /lookaheadWeeks/,
    );
  });

  it('merges a partial set over a base', () => {
    const merged = mergeConfig(DEFAULT_CONFIG, { reports: { periods: 18 } });
    expect(merged.reports.periods).toBe(18);
    expect(merged.reports.gangs).toBe(1);
  });

  it('prints them all', () => {
    const run = configFromEnv({});
    expect(run.reports.worstLines).toBe(10);
  });
});

describe('cw config', () => {
  it('prints the report defaults alongside the rest', async () => {
    const run = await runCommand(['config']);
    expect(run.out).toContain('periods             12');
    expect(run.out).toContain('lookahead weeks     4');
    expect(run.out).toContain('worst lines         10');
  });

  it('prints what the environment said', async () => {
    const run = await runCommand(['config'], { env: { CHAINAGEWORKS_PERIODS: '24' } });
    expect(run.out).toContain('periods             24');
  });
});

describe('the commands take their defaults from the config', () => {
  it('runs a curve to the configured number of periods', async () => {
    const run = await runCommand(['--json', 'curve'], {
      job: commercialJob(),
      env: { CHAINAGEWORKS_PERIODS: '5' },
    });
    expect(JSON.parse(run.out)).toBe(5);
  });

  it('lets the flag win over the config', async () => {
    const run = await runCommand(['--json', 'curve', '-n', '7'], {
      job: commercialJob(),
      env: { CHAINAGEWORKS_PERIODS: '5' },
    });
    expect(JSON.parse(run.out)).toBe(7);
  });

  it('runs a cash forecast to the configured number of periods', async () => {
    const run = await runCommand(['--json', 'cash'], {
      job: commercialJob(),
      env: { CHAINAGEWORKS_PERIODS: '3' },
    });
    expect(JSON.parse(run.out)).toBe(3);
  });

  it('runs a lookahead over the configured window', async () => {
    const run = await runCommand(['lookahead', '2025-03-26'], {
      job: commercialJob(),
      env: { CHAINAGEWORKS_LOOKAHEAD_WEEKS: '2' },
    });
    expect(run.out).toMatch(/working days in the window +10/);
  });

  it('refuses a flag that is not a whole number from one', async () => {
    const run = await runCommand(['curve', '-n', 'lots'], { job: commercialJob() });
    expect(run.code).toBe(1);
    expect(run.err).toContain('whole number from one');
  });
});

describe('the api takes its defaults from the config too', () => {
  it('runs a curve to the configured number of periods', async () => {
    const config = mergeConfig(DEFAULT_CONFIG, { reports: { periods: 4 } });
    const app = buildServer(createService(commercialJob(), config));
    const body = (await app.inject({ method: 'GET', url: '/curve' })).json<unknown[]>();
    expect(body).toHaveLength(4);
    await app.close();
  });

  it('lets the query win over the config', async () => {
    const config = mergeConfig(DEFAULT_CONFIG, { reports: { periods: 4 } });
    const app = buildServer(createService(commercialJob(), config));
    const body = (await app.inject({ method: 'GET', url: '/curve?periods=6' })).json<
      unknown[]
    >();
    expect(body).toHaveLength(6);
    await app.close();
  });

  it('runs a lookahead over the configured window', async () => {
    const config = mergeConfig(DEFAULT_CONFIG, { reports: { lookaheadWeeks: 2 } });
    const app = buildServer(createService(commercialJob(), config));
    const body = (await app.inject({ method: 'GET', url: '/lookahead' })).json<{
      workingDays: number;
    }>();
    expect(body.workingDays).toBe(10);
    await app.close();
  });
});
