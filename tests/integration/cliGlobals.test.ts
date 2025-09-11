import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../src/errors.js';
import { runCommand } from '../support/cliHarness.js';
import { commercialJob, sampleJob } from '../support/jobFixtures.js';

describe('the global flags are read when the command runs', () => {
  it('takes a flag written before the command', async () => {
    const run = await runCommand(['--json', 'status']);
    expect(JSON.parse(run.out)).toMatchObject({ id: 'A1-IMP' });
  });

  it('takes a flag written after the command', async () => {
    const run = await runCommand(['status', '--json']);
    expect(JSON.parse(run.out)).toMatchObject({ id: 'A1-IMP' });
  });

  it('takes the file flag either side of the command', async () => {
    const before = await runCommand(['--file', 'other.json', 'config']);
    const after = await runCommand(['config', '--file', 'other.json']);
    expect(before.out).toContain('job file            other.json');
    expect(after.out).toContain('job file            other.json');
  });

  it('takes the markdown flag after the command', async () => {
    const run = await runCommand(['certificate', '3', '--markdown'], { job: commercialJob() });
    expect(run.out.split('\n')[0]).toBe('# Payment certificate 3');
  });
});

describe('the job is loaded once a command needs it', () => {
  it('loads it for a command that reads the job', async () => {
    const run = await runCommand(['status'], { loadError: new BadInputError('no job file') });
    expect(run.code).toBe(1);
    expect(run.err).toContain('no job file');
  });

  it('does not load it for a command that does not', async () => {
    const run = await runCommand(['config'], { loadError: new BadInputError('no job file') });
    expect(run.code).toBe(0);
  });

  it('does not load it to print the reference', async () => {
    const run = await runCommand(['commands'], { loadError: new BadInputError('no job file') });
    expect(run.code).toBe(0);
  });
});

describe('every report command answers the same way to the json flag', () => {
  const commands: readonly string[][] = [
    ['progress', '3'],
    ['certificate', '3'],
    ['outturn', '3'],
    ['variations', '3'],
    ['risks', '3'],
    ['payments', '3'],
    ['output', '3'],
    ['defects', '3'],
    ['delay', '3'],
  ];

  it('prints something a reader can parse', async () => {
    for (const argv of commands) {
      const run = await runCommand(['--json', ...argv], { job: commercialJob() });
      expect([argv[0], run.code]).toEqual([argv[0], 0]);
      expect(() => {
        JSON.parse(run.out) as unknown;
      }).not.toThrow();
    }
  });

  it('prints text without the flag', async () => {
    for (const argv of commands) {
      const run = await runCommand(argv, { job: commercialJob() });
      expect([argv[0], run.code]).toEqual([argv[0], 0]);
      expect(run.out.length).toBeGreaterThan(0);
    }
  });

  it('runs against a bare job without falling over', async () => {
    for (const argv of commands) {
      const run = await runCommand(argv, { job: sampleJob() });
      expect([argv[0], run.code]).toEqual([argv[0], 0]);
    }
  });
});

describe('a command that does not exist', () => {
  it('is a misuse rather than a crash', async () => {
    const run = await runCommand(['reticulate']);
    expect(run.code).toBe(1);
  });

  it('leaves the job file alone', async () => {
    const run = await runCommand(['reticulate']);
    expect(run.saved).toEqual([]);
    expect(run.written).toEqual({});
  });
});
