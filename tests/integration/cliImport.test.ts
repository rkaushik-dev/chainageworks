import { describe, expect, it } from 'vitest';
import { runCommand } from '../support/cliHarness.js';

const RECORD_HEADER = 'record,package,code,step,from,to,quantity,unit,measured on,booked by';
const goodRecord = 'SR-20,WP-02,E423.1,excavate,12+800,13+000,900,m3,2025-03-24,J Foreman';
const badRecord = 'SR-21,WP-02,nonsense,excavate,12+800,13+000,900,m3,2025-03-24,J Foreman';

const files = (text: string) => ({ 'site.csv': text });

describe('cw import-records', () => {
  it('takes a clean sheet and saves the job', async () => {
    const run = await runCommand(['import-records', 'site.csv'], {
      files: files([RECORD_HEADER, goodRecord].join('\n')),
    });
    expect(run.code).toBe(0);
    expect(run.out).toBe('read 1 records, took 1, left out 0');
    expect(run.saved).toHaveLength(1);
    expect(run.saved[0]?.path).toBe('chainageworks.json');
    expect(run.saved[0]?.job.records.count()).toBe(4);
  });

  it('takes nothing and saves nothing when a row is wrong', async () => {
    const run = await runCommand(['import-records', 'site.csv'], {
      files: files([RECORD_HEADER, goodRecord, badRecord].join('\n')),
    });
    expect(run.code).toBe(0);
    expect(run.out).toContain('read 2 records, took 1, left out 1');
    expect(run.out).toContain('error: line 3 (code): not a bill item code');
    expect(run.saved).toEqual([]);
  });

  it('takes the clean rows and saves when told to', async () => {
    const run = await runCommand(['import-records', 'site.csv', '--partial'], {
      files: files([RECORD_HEADER, goodRecord, badRecord].join('\n')),
    });
    expect(run.saved).toHaveLength(1);
    expect(run.saved[0]?.job.records.count()).toBe(4);
  });

  it('saves nowhere when auto save is off', async () => {
    const run = await runCommand(['import-records', 'site.csv'], {
      env: { CHAINAGEWORKS_AUTO_SAVE: 'off' },
      files: files([RECORD_HEADER, goodRecord].join('\n')),
    });
    expect(run.code).toBe(0);
    expect(run.saved).toEqual([]);
  });

  it('saves to the file the flag names', async () => {
    const run = await runCommand(['--file', 'a1.json', 'import-records', 'site.csv'], {
      files: files([RECORD_HEADER, goodRecord].join('\n')),
    });
    expect(run.saved[0]?.path).toBe('a1.json');
  });

  it('reports a file it cannot read', async () => {
    const run = await runCommand(['import-records', 'missing.csv'], { files: {} });
    expect(run.code).toBe(1);
    expect(run.err).toBe('bad_input: no such file');
  });

  it('prints json when asked', async () => {
    const run = await runCommand(['--json', 'import-records', 'site.csv'], {
      files: files([RECORD_HEADER, goodRecord].join('\n')),
    });
    expect(JSON.parse(run.out)).toMatchObject({ rowsRead: 1, accepted: 1, rejected: 0 });
  });
});

describe('cw import-sign-offs', () => {
  it('adds them to the register', async () => {
    const run = await runCommand(['import-sign-offs', 'site.csv'], {
      files: files(
        [
          'sign off,point,package,from,to,signed on,by',
          'SO-20,IP-E1,WP-02,12+800,13+000,2025-03-20,R Engineer',
        ].join('\n'),
      ),
    });
    expect(run.out).toBe('read 1 sign offs, took 1, left out 0');
    expect(run.saved[0]?.job.signOffs.count()).toBe(3);
  });
});

describe('cw import-ncrs', () => {
  it('adds them to the register', async () => {
    const run = await runCommand(['import-ncrs', 'site.csv'], {
      files: files(
        [
          'ncr,package,from,to,raised on,description',
          'NCR-20,WP-02,12+800,12+900,2025-03-21,rutting under haul traffic',
        ].join('\n'),
      ),
    });
    expect(run.out).toBe('read 1 reports, took 1, left out 0');
    expect(run.saved[0]?.job.ncrs.count()).toBe(2);
  });
});

describe('cw import-diary', () => {
  it('adds the standdowns and warns about a reason it does not know', async () => {
    const run = await runCommand(['import-diary', 'site.csv'], {
      files: files(['date,reason', '2025-03-05,rain', '2025-03-06,badger'].join('\n')),
    });
    expect(run.out).toContain('read 2 diary lines, took 2, left out 0');
    expect(run.out).toContain('warning: line 3 (reason)');
    expect(run.saved[0]?.job.standdowns).toHaveLength(2);
  });
});

describe('cw import-holidays', () => {
  it('adds the dates', async () => {
    const run = await runCommand(['import-holidays', 'site.csv'], {
      files: files(['date', '2025-04-18', '2025-04-21'].join('\n')),
    });
    expect(run.out).toBe('read 2 holidays, took 2, left out 0');
    expect(run.saved[0]?.job.holidays).toHaveLength(2);
  });

  it('names a missing column and saves nothing', async () => {
    const run = await runCommand(['import-holidays', 'site.csv'], {
      files: files(['name', 'good friday'].join('\n')),
    });
    expect(run.out).toContain('error: file (date): the file has no date column');
    expect(run.saved).toEqual([]);
  });
});
