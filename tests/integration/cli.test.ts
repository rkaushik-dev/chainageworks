import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../src/errors.js';
import { runCommand } from '../support/cliHarness.js';
import { emptyJob } from '../support/jobFixtures.js';

describe('cw status', () => {
  it('prints what the job holds', async () => {
    const run = await runCommand(['status']);
    expect(run.code).toBe(0);
    expect(run.out).toMatch(/job +A1-IMP A1 improvement, section 2/);
    expect(run.out).toMatch(/contract sum +£312,190\.00/);
    expect(run.out).toMatch(/packages +3/);
    expect(run.out).toMatch(/blockers +none/);
  });

  it('names the blockers on an empty job', async () => {
    const run = await runCommand(['status'], { job: emptyJob() });
    expect(run.out).toMatch(/blockers +no bill has been loaded; /);
  });

  it('prints json when asked', async () => {
    const run = await runCommand(['--json', 'status']);
    expect(JSON.parse(run.out)).toMatchObject({ id: 'A1-IMP', blockers: [] });
  });
});

describe('cw config', () => {
  it('prints the settings this run would use', async () => {
    const run = await runCommand(['config']);
    expect(run.out).toContain('job file            chainageworks.json');
    expect(run.out).toContain('http                127.0.0.1:8730');
  });

  it('takes the file from the flag and the rest from the environment', async () => {
    const run = await runCommand(['--file', 'other.json', 'config'], {
      env: { CHAINAGEWORKS_PORT: '9100' },
    });
    expect(run.out).toContain('job file            other.json');
    expect(run.out).toContain('http                127.0.0.1:9100');
  });

  it('reports a bad environment variable rather than crashing', async () => {
    const run = await runCommand(['config'], { env: { CHAINAGEWORKS_PORT: 'lots' } });
    expect(run.code).toBe(1);
    expect(run.err).toContain('bad_input: CHAINAGEWORKS_PORT is not a whole number');
  });
});

describe('cw bill', () => {
  it('prints the bill in force', async () => {
    const run = await runCommand(['bill']);
    const lines = run.out.split('\n');
    expect(lines[0]).toMatch(/^code\s+description\s+quantity\s+rate\s+kind$/);
    expect(lines).toHaveLength(10);
    expect(run.out).toContain('E423.1');
    expect(run.out).toContain('£4.85/m3');
  });

  it('prices at the date asked for', async () => {
    const run = await runCommand(['bill', '--as-at', '2025-06-01']);
    expect(run.code).toBe(0);
  });

  it('refuses a date before the first bill', async () => {
    const run = await runCommand(['bill', '--as-at', '2024-01-01']);
    expect(run.code).toBe(1);
    expect(run.err).toContain('no bill revision was in force');
  });

  it('refuses a date it cannot read', async () => {
    const run = await runCommand(['bill', '--as-at', 'yesterday']);
    expect(run.code).toBe(1);
    expect(run.err).toContain('yyyy-mm-dd');
  });
});

describe('cw packages', () => {
  it('prints the register', async () => {
    const run = await runCommand(['packages']);
    const lines = run.out.split('\n');
    expect(lines[0]).toMatch(/^package\s+section\s+extent\s+rule of credit\s+items$/);
    expect(lines).toHaveLength(5);
    expect(run.out).toContain('bulk-earthworks');
  });
});

describe('cw progress', () => {
  it('prints the report for a valuation', async () => {
    const run = await runCommand(['progress', '3']);
    expect(run.code).toBe(0);
    expect(run.out).toContain('progress report, valuation 3 to 2025-03-25');
    expect(run.out).toMatch(/earned +£16,152\.50/);
  });

  it('refuses a valuation number that is not a whole number', async () => {
    const run = await runCommand(['progress', 'three']);
    expect(run.code).toBe(1);
    expect(run.err).toContain('counts from one');
  });
});

describe('cw certificate', () => {
  it('prints the certificate', async () => {
    const run = await runCommand(['certificate', '3']);
    expect(run.out).toContain('payment certificate 3');
    expect(run.out).toMatch(/amount due this certificate +£15,344\.87/);
  });
});

describe('cw strip', () => {
  it('draws the strip under a heading', async () => {
    const run = await runCommand(['strip', 'WP-01', '3']);
    const lines = run.out.split('\n');
    expect(lines[0]).toBe(
      'WP-01, valuation 3 to 2025-03-25, issued 2025-04-03, due 2025-04-17',
    );
    expect(lines[1]).toMatch(/^strip {4}#+/);
  });

  it('reports a package it does not have', async () => {
    const run = await runCommand(['strip', 'WP-99', '3']);
    expect(run.code).toBe(1);
    expect(run.err).toContain('not_found: no work package with id WP-99');
  });
});

describe('cw curve', () => {
  it('prints twelve periods by default', async () => {
    const run = await runCommand(['curve']);
    expect(run.out.split('\n')).toHaveLength(14);
  });

  it('honours a period count', async () => {
    const run = await runCommand(['curve', '--periods', '4']);
    expect(run.out.split('\n')).toHaveLength(6);
  });
});

describe('cw reconcile', () => {
  it('lists the packages that do not balance against the bill', async () => {
    const run = await runCommand(['reconcile']);
    expect(run.out).toContain('under allocated');
    expect(run.out).toContain('not allocated');
  });
});

describe('failures', () => {
  it('reports a job file it cannot read', async () => {
    const run = await runCommand(['status'], {
      loadError: new BadInputError('no job file at that path', { path: 'x.json' }),
    });
    expect(run.code).toBe(1);
    expect(run.err).toBe('bad_input: no job file at that path');
  });

  it('exits two on something that is not one of our errors', async () => {
    const run = await runCommand(['status'], { loadError: new TypeError('boom') });
    expect(run.code).toBe(2);
    expect(run.err).toContain('internal: boom');
  });

  it('reports an unknown command', async () => {
    const run = await runCommand(['nonsense']);
    expect(run.code).toBe(1);
  });

  it('prints help without failing', async () => {
    const run = await runCommand(['--help']);
    expect(run.code).toBe(0);
    expect(run.out).toContain('progress measurement and interim valuation');
  });
});
