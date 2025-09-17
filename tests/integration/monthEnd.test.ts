import { describe, expect, it } from 'vitest';
import { createService } from '../../src/app/service.js';
import {
  buildMonthEndPack,
  describePack,
  packFile,
  packIsSound,
} from '../../src/app/monthEnd.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { buildSignOff } from '../../src/domain/itp/signOff.js';
import { runCommand } from '../support/cliHarness.js';
import { d, range } from '../support/contextFixtures.js';
import { commercialJob, fluctuatingJob, sampleJob } from '../support/jobFixtures.js';

const service = () => createService(commercialJob(), DEFAULT_CONFIG);

describe('buildMonthEndPack', () => {
  const pack = buildMonthEndPack(service(), 3);

  it('is built to the valuation cut off', () => {
    expect(pack.valuation).toBe(3);
    expect(pack.cutOff).toBe('2025-03-25');
  });

  it('produces the whole pack in one call', () => {
    expect(pack.files.map((file) => file.name)).toEqual([
      'certificate.md',
      'certificate.txt',
      'certificate.csv',
      'certificate-totals.csv',
      'progress.md',
      'payments.md',
      'variations.txt',
      'outturn.txt',
      'outturn.csv',
      'output.txt',
      'risks.txt',
      'delay.txt',
      'defects.txt',
    ]);
  });

  it('writes something into every file', () => {
    for (const file of pack.files) {
      expect([file.name, file.text.length > 0]).toEqual([file.name, true]);
    }
  });

  it('carries the amount due somewhere a person can read it', () => {
    expect(pack.amountDue).toBe('15344.87');
  });

  it('checks the job before it builds anything', () => {
    expect(packIsSound(pack)).toBe(true);
    expect(pack.diagnosis.errors).toBe(0);
  });

  it('leaves the delay and defects files out on a job with no such terms', () => {
    const bare = buildMonthEndPack(createService(sampleJob(), DEFAULT_CONFIG), 3);
    expect(bare.files.map((file) => file.name)).not.toContain('delay.txt');
    expect(bare.files.map((file) => file.name)).not.toContain('defects.txt');
    expect(bare.files).toHaveLength(11);
  });

  it('carries the fluctuation clause into the certificate it writes', () => {
    const adjusted = buildMonthEndPack(createService(fluctuatingJob(), DEFAULT_CONFIG), 3);
    expect(packFile(adjusted, 'certificate.md')!.text).toContain('Price adjustment at');
  });

  it('builds the pack anyway on a job with a broken reference', () => {
    const job = commercialJob();
    job.signOffs.add(
      buildSignOff({
        id: 'SO-99',
        pointId: 'IP-NOWHERE',
        packageId: 'WP-01',
        extent: range('12+000', '12+400'),
        signedOn: d('2025-03-12'),
        by: 'R Engineer',
      }),
    );
    const broken = buildMonthEndPack(createService(job, DEFAULT_CONFIG), 3);
    expect(packIsSound(broken)).toBe(false);
    expect(broken.diagnosis.errors).toBe(1);
    expect(broken.files.length).toBeGreaterThan(10);
  });
});

describe('packFile', () => {
  it('finds a file by name', () => {
    const pack = buildMonthEndPack(service(), 3);
    expect(packFile(pack, 'progress.md')!.text.split('\n')[0]).toBe('# Progress at 2025-03-25');
  });

  it('is null for a file the pack does not carry', () => {
    expect(packFile(buildMonthEndPack(service(), 3), 'insurances.txt')).toBeNull();
  });
});

describe('describePack', () => {
  it('says what was built and whether the job checks out', () => {
    expect(describePack(buildMonthEndPack(service(), 3))).toBe(
      'valuation 3 to 2025-03-25, 13 files, the job checks out',
    );
  });
});

describe('cw month-end', () => {
  it('writes every file in the pack', async () => {
    const run = await runCommand(['month-end', '3', 'out/val-03'], { job: commercialJob() });
    expect(run.code).toBe(0);
    expect(Object.keys(run.written)).toHaveLength(13);
    expect(run.written['out/val-03/certificate.md']).toBeDefined();
    expect(run.written['out/val-03/defects.txt']).toBeDefined();
  });

  it('opens with the summary and then lists what it wrote', async () => {
    const run = await runCommand(['month-end', '3', 'out'], { job: commercialJob() });
    const lines = run.out.split('\n');
    expect(lines[0]).toBe('valuation 3 to 2025-03-25, 13 files, the job checks out');
    expect(lines).toHaveLength(14);
  });

  it('reports the amount due and the check as json', async () => {
    const run = await runCommand(['--json', 'month-end', '3', 'out'], { job: commercialJob() });
    const body = JSON.parse(run.out) as {
      amountDue: string;
      sound: boolean;
      errors: number;
      files: string[];
    };
    expect(body.amountDue).toBe('15344.87');
    expect(body.sound).toBe(true);
    expect(body.errors).toBe(0);
    expect(body.files).toHaveLength(13);
  });

  it('saves nothing back to the job file', async () => {
    const run = await runCommand(['month-end', '3', 'out'], { job: commercialJob() });
    expect(run.saved).toEqual([]);
  });

  it('refuses a run with no directory named', async () => {
    const run = await runCommand(['month-end', '3'], { job: commercialJob() });
    expect(run.code).toBe(1);
    expect(run.written).toEqual({});
  });
});
