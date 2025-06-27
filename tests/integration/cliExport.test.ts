import { describe, expect, it } from 'vitest';
import { runCommand } from '../support/cliHarness.js';
import { commercialJob, fluctuatingJob } from '../support/jobFixtures.js';

describe('cw export', () => {
  it('writes one file for each report in the pack', async () => {
    const run = await runCommand(['export', '3', 'out/val-03'], { job: commercialJob() });
    expect(run.code).toBe(0);
    expect(Object.keys(run.written)).toEqual([
      'out/val-03/progress.md',
      'out/val-03/certificate.md',
      'out/val-03/payments.md',
    ]);
  });

  it('writes markdown, not the terminal layout', async () => {
    const run = await runCommand(['export', '3', 'out'], { job: commercialJob() });
    expect(run.written['out/certificate.md']!.split('\n')[0]).toBe('# Payment certificate 3');
    expect(run.written['out/progress.md']).toContain('| Package | Section |');
    expect(run.written['out/payments.md']).toContain('## Notice diary');
  });

  it('lists the files it wrote', async () => {
    const run = await runCommand(['export', '3', 'out'], { job: commercialJob() });
    expect(run.out.split('\n')).toHaveLength(3);
    expect(run.out).toContain('out/payments.md');
  });

  it('lists the files as json when asked', async () => {
    const run = await runCommand(['--json', 'export', '3', 'out'], { job: commercialJob() });
    expect(JSON.parse(run.out)).toEqual([
      'out/progress.md',
      'out/certificate.md',
      'out/payments.md',
    ]);
  });

  it('carries the fluctuation clause into the exported certificate', async () => {
    const run = await runCommand(['export', '3', 'out'], { job: fluctuatingJob() });
    expect(run.written['out/certificate.md']).toContain('Price adjustment at 12.750%');
  });

  it('saves nothing back to the job file', async () => {
    const run = await runCommand(['export', '3', 'out'], { job: commercialJob() });
    expect(run.saved).toEqual([]);
  });

  it('refuses an export with no directory named', async () => {
    const run = await runCommand(['export', '3'], { job: commercialJob() });
    expect(run.code).toBe(1);
  });
});

describe('the markdown flag on a report command', () => {
  it('prints the certificate as markdown', async () => {
    const run = await runCommand(['--markdown', 'certificate', '3'], { job: commercialJob() });
    expect(run.out.split('\n')[0]).toBe('# Payment certificate 3');
  });

  it('prints the progress report as markdown', async () => {
    const run = await runCommand(['--markdown', 'progress', '3'], { job: commercialJob() });
    expect(run.out).toContain('| Package | Section |');
  });

  it('prints the payment position as markdown', async () => {
    const run = await runCommand(['--markdown', 'payments', '3'], { job: commercialJob() });
    expect(run.out).toContain('# Subcontract payments at 2025-03-25');
  });

  it('prints plain text without the flag', async () => {
    const run = await runCommand(['certificate', '3'], { job: commercialJob() });
    expect(run.out.split('\n')[0]).toBe('payment certificate 3');
  });

  it('falls back to plain text on a report with no markdown of its own', async () => {
    const run = await runCommand(['--markdown', 'defects', '3'], { job: commercialJob() });
    expect(run.out.split('\n')[0]).toBe('defects at 2025-03-25');
  });

  it('lets json win over markdown', async () => {
    const run = await runCommand(['--json', '--markdown', 'certificate', '3'], {
      job: commercialJob(),
    });
    expect(JSON.parse(run.out)).toBe(3);
  });
});
